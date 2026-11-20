package ca.cgagnier.wlednativeandroid.service.provisioning

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "WifiScanner"
private const val SCAN_TIMEOUT_MS = 10_000L

/**
 * Lists the WiFi networks around the phone for "Set up a new Parol".
 *
 * Android only returns WiFi scan results to apps with precise location permission (on every version,
 * Android 13+ included) and with Location turned on; without them the list is empty. Android limits apps
 * to a few scans every two minutes; when a new scan isn't allowed, the latest results are used.
 */
class WifiScanner @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    /**
     * The permissions to ask for before [scan]. Android 12+ needs both in one request so the user can choose
     * precise location; scan results need precise.
     */
    val permissions: Array<String> =
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    val isWifiOn: Boolean get() = wifiManager?.isWifiEnabled == true

    /** Location (Settings > Location) is on; scan results are empty when it is off. */
    val isLocationOn: Boolean
        get() = context.getSystemService(LocationManager::class.java)
            ?.let { LocationManagerCompat.isLocationEnabled(it) } == true

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** @throws SecurityException if [permission] wasn't granted. */
    @SuppressLint("MissingPermission") // Checked first: hasPermission().
    suspend fun scan(): List<WifiChoice> {
        if (!hasPermission()) throw SecurityException("Precise location not granted")
        val manager = wifiManager ?: return emptyList()
        val done = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                done.complete(Unit)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            @Suppress("DEPRECATION") // Still the only way to ask for a fresh scan; throttled by Android.
            val started = manager.startScan()
            if (started) {
                withTimeoutOrNull(SCAN_TIMEOUT_MS) { done.await() }
            } else {
                Log.i(TAG, "Scan not started (throttled); using the latest results")
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
        val points = manager.scanResults.map {
            @Suppress("DEPRECATION") // wifiSsid needs API 33; SSID is fine for display.
            val ssid = it.SSID.orEmpty().removeSurrounding("\"")
            ScannedAccessPoint(ssid, it.level, it.frequency, it.capabilities.orEmpty())
        }
        return WifiNetworks.choices(points)
    }
}
