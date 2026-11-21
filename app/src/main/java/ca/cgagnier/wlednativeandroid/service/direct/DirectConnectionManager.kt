package ca.cgagnier.wlednativeandroid.service.direct

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
private const val PROBE_ATTEMPTS = 5
private const val PROBE_RETRY_MS = 2000L
private const val PROBE_TIMEOUT_SECONDS = 4L
private const val JOIN_TIMEOUT_MS = 60_000

/** A saved lantern reached over its own WiFi. */
data class DirectLantern(val macAddress: String, val network: Network)

/** Progress of "Connect directly". */
sealed interface DirectJoin {
    data object Idle : DirectJoin

    data object Joining : DirectJoin

    /** On the lantern's WiFi through the app; [DirectConnectionManager.stopDirect] leaves it. */
    data object Joined : DirectJoin

    /** No lantern WiFi was found, or the user declined Android's prompt. */
    data object NotFound : DirectJoin

    /** A lantern answered but it isn't one saved in the app. */
    data object NotSaved : DirectJoin
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
            // Joined with "Connect directly" (rather than in Android's settings): report how it went.
            val fromJoin = joinCallback != null
            if (mac != null) {
                val saved = deviceRepository.findDeviceByMacAddress(mac)
                if (saved == null) {
                    Log.i(TAG, "Lantern WiFi $network belongs to a lantern that isn't saved")
                    if (fromJoin) {
                        _join.value = DirectJoin.NotSaved
                        stopDirect()
                    }
                } else {
                    use(DirectLantern(saved.macAddress, network))
                    if (fromJoin) _join.value = DirectJoin.Joined
                }
                return
            }
            if (attempt < PROBE_ATTEMPTS - 1) delay(PROBE_RETRY_MS)
        }
        Log.w(TAG, "No WLED answered on $network")
        if (joinCallback != null) {
            _join.value = DirectJoin.NotFound
            stopDirect()
        }
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
        if (_join.value == DirectJoin.Joined) _join.value = DirectJoin.Idle
    }

    /**
     * "Connect directly": joins a lantern's own WiFi from the app, [wifi] as saved for it (WLED's shared
     * WLED-AP by default). Android asks the user to confirm, and returns the phone to its usual WiFi when
     * [stopDirect] is called.
     */
    @SuppressLint("NewApi") // Guarded by canJoin, which checks the API itself.
    fun connectDirectly(wifi: LanternWifiCredentials? = null) {
        if (!canJoin || joinCallback != null) return
        joiningWifi = wifi
        _join.value = DirectJoin.Joining
        joinLanternWifi(wifi ?: LanternWifiCredentials(DirectConnection.AP_SSID, DirectConnection.AP_PASSWORD))
    }

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
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                consider(network, linkProperties)
            }

            override fun onUnavailable() {
                Log.w(TAG, "Lantern WiFi not found or declined")
                joinCallback = null
                _join.value = DirectJoin.NotFound
            }

            override fun onLost(network: Network) {
                forget(network)
            }
        }
        joinCallback = callback
        try {
            connectivityManager.requestNetwork(request, callback, JOIN_TIMEOUT_MS)
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to join the lantern's WiFi", e)
            joinCallback = null
            _join.value = DirectJoin.NotFound
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
        if (_join.value == DirectJoin.Joined || _join.value == DirectJoin.Joining) _join.value = DirectJoin.Idle
    }

    /** Clears a finished "Connect directly" message. */
    fun acknowledge() {
        val state = _join.value
        if (state == DirectJoin.NotFound || state == DirectJoin.NotSaved) _join.value = DirectJoin.Idle
    }

    /** Whether the user joined through the app (so the app offers to leave). */
    val joinedByApp: Boolean get() = joinCallback != null
}
