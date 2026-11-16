package ca.cgagnier.wlednativeandroid.ui.homeScreen.lanternwifi

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifi
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiCredentials
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiStatus
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiStore
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

private const val TAG = "LanternWifiViewModel"
private const val HTTP_UNAUTHORIZED = 401
private val JSON = "application/json".toMediaType()

data class LanternWifiUiState(
    val loading: Boolean = true,
    /** The lantern's own WiFi name as it reports it. */
    val lanternSsid: String? = null,
    val status: LanternWifiStatus = LanternWifiStatus.Unknown,
    val saved: LanternWifiCredentials? = null,
    val saving: Boolean = false,
    val needsPin: Boolean = false,
    val pin: String = "",
    val error: Int? = null,
    /** Just changed: it takes effect the next time the lantern opens its own WiFi. */
    val changed: Boolean = false,
)

/** "Parol WiFi" in the lantern's settings: shows its own WiFi and gives it its own name and password. */
@HiltViewModel
class LanternWifiViewModel @Inject constructor(
    private val deviceApiFactory: DeviceApiFactory,
    private val store: LanternWifiStore,
    moshi: Moshi,
) : ViewModel() {
    private val mapAdapter = moshi.adapter(Map::class.java)

    var state by mutableStateOf(LanternWifiUiState())
        private set

    fun load(device: Device) {
        state = state.copy(loading = true, error = null, saved = store.get(device.macAddress))
        viewModelScope.launch {
            val (ssid, pskl) = try {
                val response = deviceApiFactory.create(device).getConfig()
                val cfg = if (response.isSuccessful) response.body()?.string()?.let(mapAdapter::fromJson) else null
                cfg?.let(LanternWifi::fromConfig) ?: (null to null)
            } catch (e: IOException) {
                Log.w(TAG, "Could not read the lantern's WiFi settings", e)
                null to null
            } catch (e: JsonDataException) {
                Log.w(TAG, "Unexpected lantern settings", e)
                null to null
            }
            state = state.copy(
                loading = false,
                lanternSsid = ssid,
                status = LanternWifi.status(ssid, pskl, state.saved),
                error = R.string.lantern_wifi_unreachable.takeIf { ssid == null },
            )
        }
    }

    fun setPin(value: String) {
        state = state.copy(pin = value)
    }

    /** Gives the lantern its own name and a new random password (or the one saved for it), then saves it. */
    fun secure(device: Device) {
        if (state.saving) return
        val credentials = state.saved?.takeIf { state.status == LanternWifiStatus.Shared }
            ?: LanternWifi.newCredentials(device.macAddress)
        state = state.copy(saving = true, error = null)
        viewModelScope.launch {
            val body = LanternWifi.configJson(credentials, state.pin.ifBlank { null }).toRequestBody(JSON)
            val error = try {
                val response = deviceApiFactory.create(device).postConfig(body)
                when {
                    response.isSuccessful -> null

                    response.code() == HTTP_UNAUTHORIZED -> {
                        state = state.copy(needsPin = true)
                        R.string.lantern_wifi_error_pin
                    }

                    else -> R.string.lantern_wifi_error_generic
                }
            } catch (e: IOException) {
                Log.w(TAG, "Could not set the lantern's WiFi", e)
                R.string.lantern_wifi_unreachable
            }
            state = if (error == null) {
                store.save(device.macAddress, credentials)
                state.copy(
                    saving = false,
                    saved = credentials,
                    lanternSsid = credentials.ssid,
                    status = LanternWifiStatus.Secured,
                    changed = true,
                    needsPin = false,
                )
            } else {
                state.copy(saving = false, error = error)
            }
        }
    }
}
