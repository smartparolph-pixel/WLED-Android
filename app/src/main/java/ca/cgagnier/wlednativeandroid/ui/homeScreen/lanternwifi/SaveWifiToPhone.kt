package ca.cgagnier.wlednativeandroid.ui.homeScreen.lanternwifi

import android.app.Activity
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiCredentials

/** Saving a WiFi to the phone with Android's own "Save network?" prompt needs Android 11+. */
val canSaveWifiToPhone: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

/**
 * Saves a lantern's own WiFi to the phone's saved networks with Android's "Save network?" prompt, so the
 * phone can join it from WiFi settings without asking for the password, and rejoins it on its own.
 * ("Connect directly" alone joins it for Sinag only; Android doesn't save that.)
 *
 * @return null below Android 11, where the password has to be copied instead.
 */
@Composable
fun rememberSaveWifiToPhone(): ((LanternWifiCredentials) -> Unit)? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && result.resultCode == Activity.RESULT_OK) {
            val saved = wasSaved(result.data)
            val message = if (saved) R.string.lantern_wifi_saved_to_phone else R.string.lantern_wifi_not_saved_to_phone
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    if (!canSaveWifiToPhone) return null
    return { credentials ->
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) launcher.launch(saveIntent(credentials))
        }.onFailure {
            Toast.makeText(context, R.string.lantern_wifi_not_saved_to_phone, Toast.LENGTH_LONG).show()
        }
    }
}

/** Android's answer to "Save network?": saved now, or already saved. */
@RequiresApi(Build.VERSION_CODES.R)
private fun wasSaved(data: Intent?): Boolean {
    val codes = data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST).orEmpty()
    return codes.all { it == Settings.ADD_WIFI_RESULT_SUCCESS || it == Settings.ADD_WIFI_RESULT_ALREADY_EXISTS }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun saveIntent(credentials: LanternWifiCredentials): Intent {
    val network = WifiNetworkSuggestion.Builder()
        .setSsid(credentials.ssid)
        .setWpa2Passphrase(credentials.password)
        .build()
    return Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
        .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(network))
}
