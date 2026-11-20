package ca.cgagnier.wlednativeandroid.service.provisioning

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "WifiScanner"
private const val SCAN_TIMEOUT_MS = 10_000L

/**
 * Lists the WiFi networks around the phone for "Set up a new device".
 *
 * Android 13+ asks for "Nearby devices" (declared as not used for location); older versions need location
 * permission, and Location turned on, to return scan results. Android limits apps to a few scans every two
 * minutes; when a new scan isn't allowed, the latest results are used.
 */
class WifiScanner @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    /** The permission to ask for before [scan]. */
    val permission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    val isWifiOn: Boolean get() = wifiManager?.isWifiEnabled == true

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** @throws SecurityException if [permission] wasn't granted. */
    @SuppressLint("MissingPermission") // Checked first: hasPermission().
    suspend fun scan(): List<WifiChoice> {
        if (!hasPermission()) throw SecurityException("$permission not granted")
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
