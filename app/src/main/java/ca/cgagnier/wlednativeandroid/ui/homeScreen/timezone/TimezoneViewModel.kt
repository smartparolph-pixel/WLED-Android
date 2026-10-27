package ca.cgagnier.wlednativeandroid.ui.homeScreen.timezone

import android.icu.text.TimeZoneNames
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.timezone.TimezoneCatalog
import ca.cgagnier.wlednativeandroid.service.timezone.TimezoneOption
import ca.cgagnier.wlednativeandroid.service.timezone.TimezonePayloads
import ca.cgagnier.wlednativeandroid.service.timezone.WledTimezoneSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import android.icu.util.TimeZone as IcuTimeZone

private const val TAG = "TimezoneViewModel"
private const val HTTP_UNAUTHORIZED = 401
private val JSON = "application/json".toMediaType()

sealed interface TimezoneApplyState {
    data object Idle : TimezoneApplyState
    data object Applying : TimezoneApplyState
    data class Applied(val setting: WledTimezoneSetting) : TimezoneApplyState
    data class Failed(val message: Int) : TimezoneApplyState
}

data class TimezoneUiState(
    val query: String = "",
    val options: List<TimezoneOption> = emptyList(),
    val selectedId: String = "",
    val pin: String = "",
    val needsPin: Boolean = false,
    val apply: TimezoneApplyState = TimezoneApplyState.Idle,
) {
    val selected: TimezoneOption? get() = options.firstOrNull { it.id == selectedId }
    val visibleOptions: List<TimezoneOption> get() = TimezoneCatalog.filter(options, query)
}

@HiltViewModel
class TimezoneViewModel @Inject constructor(private val deviceApiFactory: DeviceApiFactory) : ViewModel() {

    var state by mutableStateOf(TimezoneUiState())
        private set

    init {
        val phoneZone = TimeZone.getDefault().id
        state = state.copy(selectedId = IcuTimeZone.getCanonicalID(phoneZone) ?: phoneZone)
        viewModelScope.launch {
            val options = withContext(Dispatchers.Default) { loadOptions() }
            state = state.copy(options = options)
        }
    }

    fun setQuery(value: String) {
        state = state.copy(query = value)
    }

    fun select(id: String) {
        state = state.copy(selectedId = id, apply = TimezoneApplyState.Idle)
    }

    fun setPin(value: String) {
        state = state.copy(pin = value)
    }

    fun apply(device: Device) {
        val setting = TimezoneCatalog.settingFor(state.selectedId, System.currentTimeMillis())
        val body = TimezonePayloads.configJson(setting, state.pin.ifBlank { null }).toRequestBody(JSON)
        state = state.copy(apply = TimezoneApplyState.Applying)
        viewModelScope.launch {
            val result = try {
                val response = deviceApiFactory.create(device).postConfig(body)
                when {
                    response.isSuccessful -> TimezoneApplyState.Applied(setting)

                    response.code() == HTTP_UNAUTHORIZED -> {
                        state = state.copy(needsPin = true)
                        TimezoneApplyState.Failed(R.string.timezone_error_pin)
                    }

                    else -> TimezoneApplyState.Failed(R.string.timezone_error_generic)
                }
            } catch (e: IOException) {
                Log.w(TAG, "Could not set timezone on ${device.address}", e)
                TimezoneApplyState.Failed(R.string.timezone_error_unreachable)
            }
            state = state.copy(apply = result)
        }
    }

    private fun loadOptions(): List<TimezoneOption> {
        val locale = Locale.getDefault()
        val names = TimeZoneNames.getInstance(locale)
        // Canonical locations skip legacy aliases (Asia/Calcutta etc.); the filter needs Android 9.
        val ids: Collection<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            IcuTimeZone.getAvailableIDs(IcuTimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null)
        } else {
            TimeZone.getAvailableIDs().toList()
        }
        return TimezoneCatalog.options(ids, System.currentTimeMillis(), locale) { names.getExemplarLocationName(it) }
    }
}
