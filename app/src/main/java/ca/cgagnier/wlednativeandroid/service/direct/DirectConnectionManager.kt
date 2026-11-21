package ca.cgagnier.wlednativeandroid.service.direct

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiCredentials
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DirectConnection"
private const val PROBE_ATTEMPTS = 10
private const val PROBE_RETRY_MS = 1000L
private const val PROBE_TIMEOUT_SECONDS = 4L
private const val JOIN_TIMEOUT_MS = 60_000

/**
 * "Connect directly" is left this long after Sinag goes to the background, so locking the phone or a quick
 * app switch keeps it.
 */
const val BACKGROUND_GRACE_MINUTES = 5
private const val BACKGROUND_GRACE_MS = BACKGROUND_GRACE_MINUTES * 60_000L

/** A saved lantern reached over its own WiFi. */
data class DirectLantern(val macAddress: String, val network: Network)

/** Progress of "Connect directly" to the lantern WiFi named [ssid]. */
sealed interface DirectJoin {
    data object Idle : DirectJoin

    /** Waiting for Android to find the lantern's WiFi (and for the user to approve its prompt). */
    data class Searching(val ssid: String) : DirectJoin

    /** On the lantern's WiFi; checking that it is a Parol saved in Sinag. */
    data class Verifying(val ssid: String) : DirectJoin

    /** Connected; [DirectConnectionManager.stopDirect] leaves it. */
    data class Connected(val ssid: String) : DirectJoin

    data class Failed(val ssid: String, val reason: DirectJoinFailure) : DirectJoin

    /** The connection ended without the user leaving: lost (out of range, unplugged) or after Sinag was closed. */
    data class Ended(val ssid: String, val automatically: Boolean) : DirectJoin
}

/** In progress: Android searching, or the app checking the lantern. */
val DirectJoin.inProgress: Boolean get() = this is DirectJoin.Searching || this is DirectJoin.Verifying

/** The lantern WiFi this state is about, if any. */
val DirectJoin.ssid: String?
    get() = when (this) {
        DirectJoin.Idle -> null
        is DirectJoin.Searching -> ssid
        is DirectJoin.Verifying -> ssid
        is DirectJoin.Connected -> ssid
        is DirectJoin.Failed -> ssid
        is DirectJoin.Ended -> ssid
    }

enum class DirectJoinFailure {
    /** WiFi is off on the phone. */
    WifiOff,

    /** Android didn't find the lantern's WiFi, or its prompt was declined. */
    NotFound,

    /** On the lantern's WiFi, but no Parol answered. */
    NoAnswer,

    /** A Parol answered but it isn't saved in Sinag. */
    NotSaved,
}

/**
 * Finds a saved lantern when the phone is on the lantern's own WiFi (the lantern was moved where there is
 * no home WiFi), and keeps the app's traffic on that WiFi.
 *
 * The lantern is matched by MAC at [DEFAULT_WLED_AP_IP]; its saved home address is not changed (see
 * [DirectConnection.reachable]). While it is found the app process is bound to the lantern's WiFi, because
 * Android otherwise sends requests over mobile data when a WiFi has no internet. Leaving the WiFi undoes both.
 */
@Singleton
class DirectConnectionManager @Inject constructor(
    @param:ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
    moshi: Moshi,
    private val deviceRepository: DeviceRepository,
    private val scope: CoroutineScope,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val infoAdapter = moshi.adapter(Info::class.java)

    private val _lantern = MutableStateFlow<DirectLantern?>(null)

    /** The saved lantern currently reached over its own WiFi, if any. */
    val lantern: StateFlow<DirectLantern?> = _lantern.asStateFlow()

    private val _join = MutableStateFlow<DirectJoin>(DirectJoin.Idle)
    val join: StateFlow<DirectJoin> = _join.asStateFlow()

    private val probes = mutableMapOf<Network, Job>()

    /** The Parol WiFi (saved on this phone) last joined with "Connect directly"; null for WLED-AP. */
    @Volatile
    var joiningWifi: LanternWifiCredentials? = null
        private set

    @Volatile
    private var joinCallback: ConnectivityManager.NetworkCallback? = null

    /** Whether the app can join the lantern's WiFi itself (Android 10+; checked by API, see provisioning). */
    val canJoin: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            runCatching { Class.forName("android.net.wifi.WifiNetworkSpecifier") }.isSuccess

    private val watcher = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            consider(network, linkProperties)
        }

        override fun onLost(network: Network) {
            forget(network)
        }
    }

    init {
        // Every WiFi, including ones without internet: a lantern's own WiFi never has any.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, watcher) }
            .onFailure { Log.e(TAG, "Could not watch WiFi networks", it) }
        scope.launch(Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.addObserver(appVisibility) }
    }

    private var leaveJob: Job? = null

    /**
     * When Sinag goes to the background (closed, or another app opened), the lantern's WiFi joined with
     * "Connect directly" is released after [BACKGROUND_GRACE_MS], and Android rejoins the phone's usual WiFi.
     * Coming back within that time keeps it. A WiFi joined in Android's settings is left as it is.
     */
    private val appVisibility = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            if (joinCallback == null) return
            leaveJob?.cancel()
            leaveJob = scope.launch {
                delay(BACKGROUND_GRACE_MS)
                val ssid = _join.value.ssid
                if (joinCallback != null && ssid != null) {
                    Log.i(TAG, "Sinag is in the background: leaving the lantern's WiFi")
                    stopDirect()
                    _join.value = DirectJoin.Ended(ssid, automatically = true)
                }
            }
        }

        override fun onStart(owner: LifecycleOwner) {
            leaveJob?.cancel()
            leaveJob = null
        }
    }

    private fun consider(network: Network, linkProperties: LinkProperties) {
        val addresses = linkProperties.dnsServers.map { it.hostAddress } +
            linkProperties.routes.mapNotNull { it.gateway?.hostAddress }
        if (!DirectConnection.isLanternNetwork(addresses)) return
        synchronized(probes) {
            if (_lantern.value?.network == network || probes[network]?.isActive == true) return
            probes[network] = scope.launch(Dispatchers.IO) { probe(network) }
        }
    }

    /** Asks the lantern who it is over [network]; the lantern may need a few seconds after the WiFi is up. */
    private suspend fun probe(network: Network) {
        val http = okHttpClient.newBuilder()
            .socketFactory(network.socketFactory)
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(null)
            .build()
        repeat(PROBE_ATTEMPTS) { attempt ->
            val mac = fetchMac(http)
            if (mac != null) {
                val saved = deviceRepository.findDeviceByMacAddress(mac)
                if (saved == null) {
                    Log.i(TAG, "Lantern WiFi $network belongs to a lantern that isn't saved")
                    failJoin(DirectJoinFailure.NotSaved)
                } else {
                    use(DirectLantern(saved.macAddress, network))
                    // Joined with "Connect directly" (rather than in Android's settings): report it.
                    val ssid = _join.value.ssid
                    if (joinCallback != null && ssid != null) _join.value = DirectJoin.Connected(ssid)
                }
                return
            }
            if (attempt < PROBE_ATTEMPTS - 1) delay(PROBE_RETRY_MS)
        }
        Log.w(TAG, "No WLED answered on $network")
        failJoin(DirectJoinFailure.NoAnswer)
    }

    /** Ends a "Connect directly" in progress with [reason]; nothing to do for a WiFi joined in settings. */
    private fun failJoin(reason: DirectJoinFailure) {
        val ssid = _join.value.ssid ?: return
        if (joinCallback == null) return
        stopDirect()
        _join.value = DirectJoin.Failed(ssid, reason)
    }

    private fun fetchMac(http: OkHttpClient): String? = try {
        http.newCall(Request.Builder().url("http://$DEFAULT_WLED_AP_IP/json/info").build()).execute().use {
            val info = if (it.isSuccessful) infoAdapter.fromJson(it.body.string()) else null
            info?.macAddress?.takeIf(String::isNotEmpty)
        }
    } catch (e: IOException) {
        Log.d(TAG, "Lantern not answering yet: ${e.message}")
        null
    } catch (e: JsonDataException) {
        Log.d(TAG, "Not a WLED answer", e)
        null
    }

    private fun use(lantern: DirectLantern) {
        Log.i(TAG, "Direct connection to ${lantern.macAddress} on ${lantern.network}")
        // Requests (websocket, API, the lantern's web page) go to this WiFi even though it has no internet.
        connectivityManager.bindProcessToNetwork(lantern.network)
        _lantern.value = lantern
    }

    private fun forget(network: Network) {
        synchronized(probes) { probes.remove(network)?.cancel() }
        if (_lantern.value?.network != network) return
        Log.i(TAG, "Left the lantern's WiFi $network")
        connectivityManager.bindProcessToNetwork(null)
        _lantern.value = null
    }

    /**
     * "Connect directly": joins a lantern's own WiFi from the app, [wifi] as saved for it (WLED's shared
     * WLED-AP by default). Android asks the user to confirm, and returns the phone to its usual WiFi when
     * [stopDirect] is called.
     */
    @SuppressLint("NewApi") // Guarded by canJoin, which checks the API itself.
    fun connectDirectly(wifi: LanternWifiCredentials? = null) {
        if (!canJoin) return
        val target = wifi ?: LanternWifiCredentials(DirectConnection.AP_SSID, DirectConnection.AP_PASSWORD)
        // A request still held (e.g. after the connection was lost) is released first, so a new one can start.
        stopDirect()
        joiningWifi = wifi
        if (wifiManager?.isWifiEnabled == false) {
            _join.value = DirectJoin.Failed(target.ssid, DirectJoinFailure.WifiOff)
            return
        }
        _join.value = DirectJoin.Searching(target.ssid)
        joinLanternWifi(target)
    }

    /** "Try again" after a failure or a lost connection: the same lantern WiFi as last time. */
    fun retry() = connectDirectly(joiningWifi)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun joinLanternWifi(wifi: LanternWifiCredentials) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(wifi.ssid)
            .setWpa2Passphrase(wifi.password)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (joinCallback === this) _join.value = DirectJoin.Verifying(wifi.ssid)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                consider(network, linkProperties)
            }

            override fun onUnavailable() {
                Log.w(TAG, "Lantern WiFi not found or declined")
                if (joinCallback !== this) return
                joinCallback = null
                _join.value = DirectJoin.Failed(wifi.ssid, DirectJoinFailure.NotFound)
            }

            override fun onLost(network: Network) {
                forget(network)
                if (joinCallback !== this) return
                // Out of range, unplugged, or Android dropped it: release the request so a new one can start.
                Log.i(TAG, "Lost the lantern's WiFi")
                stopDirect()
                _join.value = DirectJoin.Ended(wifi.ssid, automatically = false)
            }
        }
        joinCallback = callback
        try {
            connectivityManager.requestNetwork(request, callback, JOIN_TIMEOUT_MS)
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to join the lantern's WiFi", e)
            joinCallback = null
            _join.value = DirectJoin.Failed(wifi.ssid, DirectJoinFailure.NotFound)
        }
    }

    /** Leaves a WiFi joined with [connectDirectly]; a WiFi joined in Android's settings is left there. */
    fun stopDirect() {
        val callback = joinCallback ?: return
        joinCallback = null
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Join request already released", e)
        }
        _lantern.value?.let { forget(it.network) }
        _join.value = DirectJoin.Idle
    }

    /** Clears a finished "Connect directly" message. */
    fun acknowledge() {
        val state = _join.value
        if (state is DirectJoin.Failed || state is DirectJoin.Ended) _join.value = DirectJoin.Idle
    }

    /** Whether the user joined through the app (so the app offers to leave). */
    val joinedByApp: Boolean get() = joinCallback != null
}
