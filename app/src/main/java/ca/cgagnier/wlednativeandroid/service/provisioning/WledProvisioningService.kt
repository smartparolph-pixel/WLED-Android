package ca.cgagnier.wlednativeandroid.service.provisioning

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import android.util.Log
import androidx.annotation.RequiresApi
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.service.DeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "WledProvisioning"
private val JSON = "application/json".toMediaType()

/**
 * Tapo-style onboarding for a WLED device:
 *
 *  1. Join the device's setup AP (system-confirmed on Android 10+; joined by the user in Settings before).
 *  2. Verify it is WLED and read its MAC (`GET /json/info`).
 *  3. Push home WiFi credentials and the brightness limiter for the lanterns' power supply
 *     (`POST /json/cfg`), then reboot it (`POST /json/state {"rb":true}`).
 *  4. Drop the AP, let the phone fall back to its normal WiFi.
 *  5. Wait for the device to re-appear over mDNS, matching on MAC, and add it to the app.
 *
 * All traffic during steps 2-3 is routed through the specific AP [Network] with its socket
 * factory, so nothing depends on which network Android considers "default".
 */
class WledProvisioningService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val deviceFirstContactService: DeviceFirstContactService,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    @SuppressLint("NewApi") // joinSetupNetwork is guarded by canJoinNetworks, which checks the API itself.
    fun provision(request: ProvisioningRequest): Flow<ProvisioningProgress> = flow {
        val manualJoin = !canJoinNetworks

        emit(ProvisioningProgress.JoiningSetupNetwork)
        val connection = if (manualJoin) manuallyJoinedNetwork() else joinSetupNetwork(request)
        val info = try {
            val http = httpClientFor(connection.network)
            val deviceInfo = try {
                fetchInfo(http)
            } catch (e: ProvisioningException.NotAWledDevice) {
                // On a manual join, a WiFi that is not the setup network means the user has not joined it yet.
                throw if (manualJoin) ProvisioningException.ManualJoinRequired() else e
            }

            emit(ProvisioningProgress.SendingCredentials)
            postCredentials(http, request, powerLimit(http))

            emit(ProvisioningProgress.RebootingDevice)
            delay(ProvisioningDefaults.CONFIG_WRITE_GRACE_MS)
            requestReboot(http)
            deviceInfo
        } finally {
            // Releasing the request makes Android disconnect from the AP and rejoin the home WiFi.
            connection.release()
        }

        emit(ProvisioningProgress.WaitingForDevice)
        val device = waitForDeviceOnHomeNetwork(info.macAddress)
            ?: throw ProvisioningException.DeviceDidNotReappear()
        emit(ProvisioningProgress.Done(device))
    }.flowOn(Dispatchers.IO)

    // region step 1: join AP

    /**
     * Whether the app can join the setup network itself (Android 10+). Checks for the API rather than
     * trusting SDK_INT alone: ColorOS 14 has been seen reporting API 28 to the app on Android 14.
     */
    private val canJoinNetworks: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            runCatching { Class.forName("android.net.wifi.WifiNetworkSpecifier") }.isSuccess

    private class ApConnection(val network: Network, val release: () -> Unit)

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun joinSetupNetwork(request: ProvisioningRequest): ApConnection =
        suspendCancellableCoroutine { cont ->
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsidPattern(PatternMatcher(request.apSsidPrefix, PatternMatcher.PATTERN_PREFIX))
                .apply { request.apPassword?.let { setWpa2Passphrase(it) } }
                .build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                // The setup AP has no internet; without this Android may refuse to "use" it.
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Setup network available: $network")
                    if (cont.isActive) {
                        cont.resume(ApConnection(network) { safeUnregister(this) })
                    }
                }

                override fun onUnavailable() {
                    Log.w(TAG, "Setup network unavailable (declined or not found)")
                    if (cont.isActive) cont.resumeWithException(ProvisioningException.SetupNetworkUnavailable())
                }
            }

            connectivityManager.requestNetwork(networkRequest, callback, ProvisioningDefaults.AP_JOIN_TIMEOUT_MS)
            cont.invokeOnCancellation { safeUnregister(callback) }
        }

    /**
     * Android 9 and older cannot join a network for the app, so the user joins the setup network in
     * Settings first. Requests are still bound to that WiFi, because Android may keep mobile data as
     * the default network while the WiFi has no internet.
     */
    private fun manuallyJoinedNetwork(): ApConnection {
        @Suppress("DEPRECATION") // allNetworks is fine here: this path only runs on Android 9 and older.
        val wifi = connectivityManager.allNetworks.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: throw ProvisioningException.ManualJoinRequired()
        // Nothing to release: the phone returns to its usual WiFi when the device reboots.
        return ApConnection(wifi) {}
    }

    private fun safeUnregister(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Callback already unregistered", e)
        }
    }

    // endregion

    // region steps 2-3: talk to the device through the AP network

    private fun httpClientFor(network: Network): OkHttpClient = okHttpClient.newBuilder()
        .socketFactory(network.socketFactory)
        .connectTimeout(ProvisioningDefaults.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ProvisioningDefaults.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ProvisioningDefaults.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .cache(null)
        .build()

    private fun apUrl(path: String) = "http://${ProvisioningDefaults.AP_HOST}$path"

    private fun fetchInfo(http: OkHttpClient): Info {
        try {
            http.newCall(Request.Builder().url(apUrl("/json/info")).build()).execute().use { response ->
                val body = response.body.string()
                val info = if (response.isSuccessful) moshi.adapter(Info::class.java).fromJson(body) else null
                if (info == null || info.macAddress.isNullOrEmpty()) {
                    throw ProvisioningException.NotAWledDevice()
                }
                return info
            }
        } catch (e: IOException) {
            throw ProvisioningException.NotAWledDevice(e)
        }
    }

    /**
     * The brightness limiter for the lanterns' power supply ([ProvisioningDefaults.POWER_LIMIT_MA]), sent
     * with the WiFi credentials. Null if the device's LED setup could not be read: setup goes on without it.
     */
    private fun powerLimit(http: OkHttpClient): Map<String, Any>? = try {
        http.newCall(Request.Builder().url(apUrl("/json/cfg")).build()).execute().use { response ->
            val cfg = if (response.isSuccessful) {
                moshi.adapter(
                    Map::class.java,
                ).fromJson(response.body.string())
            } else {
                null
            }
            cfg?.let { ProvisioningPayloads.powerLimitConfig(it) }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Could not read the LED setup; leaving the brightness limiter as it is", e)
        null
    } catch (e: JsonDataException) {
        Log.w(TAG, "Unexpected LED setup; leaving the brightness limiter as it is", e)
        null
    }

    private fun postCredentials(http: OkHttpClient, request: ProvisioningRequest, led: Map<String, Any>?) {
        val body = ProvisioningPayloads.wifiConfigJson(
            request.homeSsid,
            request.homePassword,
            request.timezone,
            led,
            request.deviceName,
        )
        val call = Request.Builder()
            .url(apUrl("/json/cfg"))
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(call).execute().use { response ->
            if (!response.isSuccessful) throw ProvisioningException.CredentialsRejected(response.code)
        }
    }

    private fun requestReboot(http: OkHttpClient) {
        val call = Request.Builder()
            .url(apUrl("/json/state"))
            .post(ProvisioningPayloads.REBOOT_JSON.toRequestBody(JSON))
            .build()
        try {
            http.newCall(call).execute().close()
        } catch (e: IOException) {
            // The device often resets before it finishes answering. That is the success path.
            Log.d(TAG, "Connection dropped while rebooting device (expected)", e)
        }
    }

    // endregion

    // region step 5: find it again

    private suspend fun waitForDeviceOnHomeNetwork(macAddress: String?): Device? {
        val found = CompletableDeferred<String>()
        val discovery = DeviceDiscovery(context) { address, mdnsMac ->
            if (ProvisioningPayloads.sameMac(mdnsMac, macAddress)) found.complete(address)
        }
        return withContext(Dispatchers.IO) {
            try {
                // Give the device time to boot and Android time to rejoin the home WiFi.
                delay(REBOOT_SETTLE_MS)
                discovery.start()
                val address = withTimeoutOrNull(ProvisioningDefaults.DEVICE_REAPPEAR_TIMEOUT_MS) { found.await() }
                address?.let { deviceFirstContactService.fetchAndUpsertDevice(it) }
            } finally {
                discovery.stop()
            }
        }
    }

    // endregion

    private companion object {
        const val REBOOT_SETTLE_MS = 5_000L
    }
}
