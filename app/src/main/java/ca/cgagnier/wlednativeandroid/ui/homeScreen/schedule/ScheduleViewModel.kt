package ca.cgagnier.wlednativeandroid.ui.homeScreen.schedule

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.api.DeviceApi
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.schedule.Schedule
import ca.cgagnier.wlednativeandroid.service.schedule.ScheduleCodec
import ca.cgagnier.wlednativeandroid.service.schedule.ScheduleLimits
import ca.cgagnier.wlednativeandroid.service.schedule.SchedulePayloads
import ca.cgagnier.wlednativeandroid.service.schedule.SchedulePresets
import ca.cgagnier.wlednativeandroid.service.schedule.ScheduleTrigger
import ca.cgagnier.wlednativeandroid.service.timezone.TimezoneCatalog
import ca.cgagnier.wlednativeandroid.service.timezone.TimezonePayloads
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.TimeZone
import javax.inject.Inject

private const val TAG = "ScheduleViewModel"
private const val HTTP_UNAUTHORIZED = 401
private val JSON = "application/json".toMediaType()

/** What a schedule does when it fires. */
sealed interface ScheduleAction {
    data object TurnOn : ScheduleAction
    data object TurnOff : ScheduleAction

    /** A preset picked under Config → Time & Macros, kept as is. */
    data class Preset(val id: Int, val name: String) : ScheduleAction
}

data class ScheduleEditor(
    /** Index in the list, or null for a new schedule. */
    val index: Int?,
    val draft: Schedule,
    val action: ScheduleAction,
    val pendingLocation: Pair<Double, Double>? = null,
)

data class ScheduleUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Int? = null,
    val schedules: List<Schedule> = emptyList(),
    val presets: Map<Int, String> = emptyMap(),
    val limits: ScheduleLimits = ScheduleLimits.LEGACY,
    val currentFormat: Boolean = false,
    val ntpEnabled: Boolean = true,
    val hasLocation: Boolean = true,
    val needsPin: Boolean = false,
    val pin: String = "",
    val editor: ScheduleEditor? = null,
) {
    fun actionFor(schedule: Schedule): ScheduleAction = when (presets[schedule.presetId]) {
        SchedulePresets.ON_NAME -> ScheduleAction.TurnOn
        SchedulePresets.OFF_NAME -> ScheduleAction.TurnOff
        else -> ScheduleAction.Preset(schedule.presetId, presets[schedule.presetId].orEmpty())
    }

    val canAddAny: Boolean get() = ScheduleTrigger.entries.any { limits.canAdd(schedules, it) }
}

/**
 * Reads and writes the device's own schedule list (the one under Config → Time & Macros) through
 * `/json/cfg`, so both places always show the same schedules.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(private val deviceApiFactory: DeviceApiFactory) : ViewModel() {

    var state by mutableStateOf(ScheduleUiState())
        private set

    private val moshi = Moshi.Builder().build()
    private var device: Device? = null

    fun load(device: Device, buildId: Int?, platform: String?) {
        this.device = device
        val currentFormat = ScheduleCodec.isCurrentFormat(buildId)
        val limits = if (currentFormat) {
            ScheduleLimits.current(isEsp8266 = platform.orEmpty().contains("8266"))
        } else {
            ScheduleLimits.LEGACY
        }
        state = state.copy(loading = true, error = null, currentFormat = currentFormat, limits = limits)
        viewModelScope.launch { refresh(device) }
    }

    fun setPin(pin: String) {
        state = state.copy(pin = pin)
    }

    fun retry() {
        device?.let { d -> viewModelScope.launch { refresh(d) } }
    }

    fun setEnabled(index: Int, enabled: Boolean) {
        val updated = state.schedules.toMutableList()
        updated[index] = updated[index].copy(enabled = enabled)
        persist(updated)
    }

    fun delete(index: Int) {
        persist(state.schedules.filterIndexed { i, _ -> i != index })
    }

    fun openNew() {
        val trigger = ScheduleTrigger.entries.first { state.limits.canAdd(state.schedules, it) }
        val draft = Schedule(trigger, hour = 0, minute = 0, presetId = 0, days = Schedule.ALL_DAYS, enabled = true)
        state = state.copy(editor = ScheduleEditor(index = null, draft = draft, action = ScheduleAction.TurnOn))
    }

    fun openEdit(index: Int) {
        val schedule = state.schedules[index]
        state = state.copy(editor = ScheduleEditor(index, schedule, state.actionFor(schedule)))
    }

    fun closeEditor() {
        state = state.copy(editor = null, error = null)
    }

    fun updateDraft(transform: (Schedule) -> Schedule) {
        val editor = state.editor ?: return
        state = state.copy(editor = editor.copy(draft = transform(editor.draft)))
    }

    fun setAction(action: ScheduleAction) {
        val editor = state.editor ?: return
        state = state.copy(editor = editor.copy(action = action))
    }

    fun setLocation(latitude: Double, longitude: Double) {
        val editor = state.editor ?: return
        state = state.copy(editor = editor.copy(pendingLocation = latitude to longitude), hasLocation = true)
    }

    /** True when [trigger] can be chosen for the open editor without exceeding the device's slots. */
    fun canUseTrigger(trigger: ScheduleTrigger): Boolean {
        val editor = state.editor ?: return false
        val others = state.schedules.filterIndexed { i, _ -> i != editor.index }
        return state.limits.canAdd(others, trigger)
    }

    fun saveEditor() {
        val editor = state.editor ?: return
        if ((editor.draft.days and Schedule.ALL_DAYS) == 0) return
        val updated = state.schedules.toMutableList()
        viewModelScope.launch {
            state = state.copy(saving = true, error = null)
            val api = api() ?: return@launch
            val presetId = runCatchingIo { presetFor(api, editor.action) }
            if (presetId == null) {
                state = state.copy(saving = false, error = state.error ?: R.string.schedule_error_unreachable)
                return@launch
            }
            val schedule = editor.draft.copy(presetId = presetId)
            if (editor.index == null) updated.add(schedule) else updated[editor.index] = schedule
            if (write(api, updated, editor.pendingLocation)) state = state.copy(editor = null)
        }
    }

    fun deleteEditor() {
        val index = state.editor?.index ?: return closeEditor()
        state = state.copy(editor = null)
        delete(index)
    }

    private fun persist(schedules: List<Schedule>) {
        viewModelScope.launch {
            state = state.copy(saving = true, error = null)
            val api = api() ?: return@launch
            write(api, schedules, location = null)
        }
    }

    private fun api(): DeviceApi? {
        val d = device
        if (d == null) state = state.copy(saving = false, loading = false)
        return d?.let { deviceApiFactory.create(it) }
    }

    private suspend fun refresh(device: Device) {
        state = state.copy(loading = true, error = null)
        val loaded = runCatchingIo {
            val api = deviceApiFactory.create(device)
            val cfgResponse = api.getConfig()
            if (cfgResponse.code() == HTTP_UNAUTHORIZED) {
                state = state.copy(needsPin = true, error = R.string.schedule_error_pin)
                return@runCatchingIo false
            }
            val cfg = parseMap(cfgResponse.body()?.string())
            val presets = parseMap(api.getPresets().body()?.string())
            val ntp = (cfg["if"] as? Map<*, *>)?.get("ntp") as? Map<*, *>
            val ins = ((cfg["timers"] as? Map<*, *>)?.get("ins") as? List<*>).orEmpty()
            state = state.copy(
                schedules = ScheduleCodec.decode(ins, state.currentFormat),
                presets = SchedulePresets.names(presets),
                ntpEnabled = ntp?.get("en") == true,
                hasLocation = ntp.coordinate("lt") != 0.0 || ntp.coordinate("ln") != 0.0,
            )
            true
        }
        state =
            state.copy(
                loading = false,
                error = if (loaded ==
                    null
                ) {
                    R.string.schedule_error_unreachable
                } else {
                    state.error
                },
            )
    }

    /** Finds or creates the preset the schedule should apply. */
    private suspend fun presetFor(api: DeviceApi, action: ScheduleAction): Int? = when (action) {
        is ScheduleAction.Preset -> action.id
        ScheduleAction.TurnOn -> ensurePreset(api, SchedulePresets.ON_NAME, on = true)
        ScheduleAction.TurnOff -> ensurePreset(api, SchedulePresets.OFF_NAME, on = false)
    }

    private suspend fun ensurePreset(api: DeviceApi, name: String, on: Boolean): Int? {
        val existing = SchedulePresets.find(state.presets, name)
        val free = SchedulePresets.freeSlot(state.presets)
        return when {
            existing != null -> existing

            free == null -> {
                state = state.copy(error = R.string.schedule_error_no_preset_slot)
                null
            }

            api.postState(SchedulePresets.saveJson(free, name, on).toRequestBody(JSON)).isSuccessful -> {
                state = state.copy(presets = state.presets + (free to name))
                free
            }

            else -> null
        }
    }

    /** Writes the full list; also turns on internet time (with the phone's zone) if it is off. */
    private suspend fun write(api: DeviceApi, schedules: List<Schedule>, location: Pair<Double, Double>?): Boolean {
        val ntp = mutableMapOf<String, Any>()
        if (!state.ntpEnabled) {
            val zone = TimezoneCatalog.settingFor(TimeZone.getDefault().id, System.currentTimeMillis())
            ntp.putAll(TimezonePayloads.ntpConfig(zone))
        }
        location?.let { (lat, lon) ->
            ntp["lt"] = lat
            ntp["ln"] = lon
        }
        val timers = ScheduleCodec.encode(schedules, state.currentFormat)
        val body = SchedulePayloads.configJson(timers, ntp, state.pin.ifBlank { null })
        val result = runCatchingIo {
            val response = api.postConfig(body.toRequestBody(JSON))
            when {
                response.isSuccessful -> {
                    state = state.copy(schedules = schedules, ntpEnabled = true)
                    true
                }

                response.code() == HTTP_UNAUTHORIZED -> {
                    state = state.copy(needsPin = true, error = R.string.schedule_error_pin)
                    false
                }

                else -> {
                    state = state.copy(error = R.string.schedule_error_rejected)
                    false
                }
            }
        }
        if (result == null) state = state.copy(error = R.string.schedule_error_unreachable)
        state = state.copy(saving = false)
        return result == true
    }

    private fun Map<*, *>?.coordinate(key: String): Double = (this?.get(key) as? Number)?.toDouble() ?: 0.0

    private fun parseMap(json: String?): Map<*, *> =
        json?.let { runCatching { moshi.adapter(Map::class.java).fromJson(it) }.getOrNull() }.orEmpty()

    private suspend fun <T> runCatchingIo(block: suspend () -> T): T? = try {
        block()
    } catch (e: IOException) {
        Log.w(TAG, "Device request failed", e)
        null
    }
}
