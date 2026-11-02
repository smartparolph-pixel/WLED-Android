package ca.cgagnier.wlednativeandroid.ui.homeScreen.schedule

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.schedule.Schedule
import ca.cgagnier.wlednativeandroid.service.schedule.ScheduleDays
import ca.cgagnier.wlednativeandroid.service.schedule.ScheduleTrigger
import java.util.Calendar
import kotlin.math.abs

private const val OFFSET_STEP_MINUTES = 5
private const val LAST_HOUR = 23
private const val LAST_MINUTE = 59
private const val EVERY_HOUR = 24

@Composable
internal fun ScheduleEditScreen(viewModel: ScheduleViewModel, editor: ScheduleEditor) {
    val state = viewModel.state
    val draft = editor.draft
    val hasDays = (draft.days and Schedule.ALL_DAYS) != 0
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(if (editor.index == null) R.string.schedule_new else R.string.schedule_edit))
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeEditor) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.description_back_button),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::saveEditor, enabled = hasDays && !state.saving) {
                        Text(stringResource(R.string.schedule_save), style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            StatusMessages(viewModel)
            SectionLabel(R.string.schedule_at)
            SectionCard { AtSection(viewModel, editor) }
            SectionLabel(R.string.schedule_turn_device)
            SectionCard { TurnDeviceSection(editor.action, viewModel::setAction) }
            SectionLabel(R.string.schedule_repeat)
            SectionCard {
                DayCircles(draft.days) { bit ->
                    viewModel.updateDraft { it.copy(days = ScheduleDays.toggle(it.days, bit)) }
                }
            }
            Text(
                text = if (hasDays) daysSummary(draft.days) else stringResource(R.string.schedule_pick_day),
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasDays) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
            if (editor.index != null) DeleteButton(viewModel::deleteEditor)
        }
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = SectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun AtSection(viewModel: ScheduleViewModel, editor: ScheduleEditor) {
    val draft = editor.draft
    val triggers = listOf(
        ScheduleTrigger.TIME to R.string.schedule_custom_time,
        ScheduleTrigger.SUNRISE to R.string.schedule_sunrise,
        ScheduleTrigger.SUNSET to R.string.schedule_sunset,
    )
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(triggers.first { it.first == draft.trigger }.second),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(scheduleTimeText(draft), style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            triggers.forEachIndexed { index, (trigger, label) ->
                SegmentedButton(
                    selected = draft.trigger == trigger,
                    onClick = {
                        viewModel.updateDraft {
                            if (it.trigger == trigger) it else it.copy(trigger = trigger, hour = 0, minute = 0)
                        }
                    },
                    enabled = draft.trigger == trigger || viewModel.canUseTrigger(trigger),
                    shape = SegmentedButtonDefaults.itemShape(index, triggers.size),
                ) {
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
        if (draft.trigger == ScheduleTrigger.TIME) {
            TimeSection(draft) { hour, minute -> viewModel.updateDraft { it.copy(hour = hour, minute = minute) } }
        } else {
            SunOffsetSection(draft, viewModel.state.limits.maxOffsetMinutes) { offset ->
                viewModel.updateDraft { it.copy(minute = offset) }
            }
            LocationRow(viewModel, editor)
        }
    }
}

/** Android's standard Material time picker, with its keyboard-entry variant. */
@Composable
private fun TimeSection(draft: Schedule, onChange: (Int, Int) -> Unit) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val pickerState = rememberTimePickerState(
        initialHour = draft.hour.coerceIn(0, LAST_HOUR),
        initialMinute = draft.minute.coerceIn(0, LAST_MINUTE),
        is24Hour = is24Hour,
    )
    var useKeyboard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pickerState.hour, pickerState.minute) { onChange(pickerState.hour, pickerState.minute) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        if (useKeyboard) TimeInput(state = pickerState) else TimePicker(state = pickerState)
        TextButton(onClick = { useKeyboard = !useKeyboard }) {
            Text(stringResource(if (useKeyboard) R.string.schedule_use_dial else R.string.schedule_type_time))
        }
    }
}

@Composable
private fun SunOffsetSection(draft: Schedule, maxOffset: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        FilledTonalButton(
            onClick = { onChange((draft.minute - OFFSET_STEP_MINUTES).coerceAtLeast(-maxOffset)) },
            enabled = draft.minute > -maxOffset,
        ) { Text(stringResource(R.string.schedule_offset_earlier)) }
        FilledTonalButton(
            onClick = { onChange((draft.minute + OFFSET_STEP_MINUTES).coerceAtMost(maxOffset)) },
            enabled = draft.minute < maxOffset,
        ) { Text(stringResource(R.string.schedule_offset_later)) }
    }
}

/** Sunrise and sunset need the device's coordinates; offer the phone's. */
@Composable
private fun LocationRow(viewModel: ScheduleViewModel, editor: ScheduleEditor) {
    val state = viewModel.state
    var failed by rememberSaveable { mutableStateOf(false) }
    val requestLocation = rememberPhoneLocation(
        onLocation = { lat, lon -> viewModel.setLocation(lat, lon) },
        onFailure = { failed = true },
    )
    val message = when {
        editor.pendingLocation != null -> R.string.schedule_location_set
        failed -> R.string.schedule_location_failed
        !state.hasLocation -> R.string.schedule_location_missing
        else -> null
    }
    message?.let {
        Text(
            text = stringResource(it),
            style = MaterialTheme.typography.bodyMedium,
            color = if (it == R.string.schedule_location_set) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    if (!state.hasLocation || failed) {
        OutlinedButton(onClick = requestLocation, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.schedule_use_phone_location))
        }
    }
}

@Composable
private fun TurnDeviceSection(action: ScheduleAction, onSelect: (ScheduleAction) -> Unit) {
    val options = buildList {
        add(ScheduleAction.TurnOn to stringResource(R.string.schedule_turn_on))
        add(ScheduleAction.TurnOff to stringResource(R.string.schedule_turn_off))
        if (action is ScheduleAction.Preset) {
            val name = action.name.ifEmpty { stringResource(R.string.schedule_no_preset) }
            add(action to stringResource(R.string.schedule_preset, name))
        }
    }
    options.forEachIndexed { index, (option, label) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(option) }
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (option == action) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (index < options.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
    }
}

/** "6:30 PM", "15 min before sunset", "Every hour at :05". Follows the phone's 12/24-hour setting. */
@Composable
internal fun scheduleTimeText(schedule: Schedule): String {
    val context = LocalContext.current
    val offset = abs(schedule.minute)
    return when (schedule.trigger) {
        ScheduleTrigger.TIME -> if (schedule.hour >= EVERY_HOUR) {
            stringResource(R.string.schedule_every_hour, schedule.minute)
        } else {
            formatTime(context, schedule.hour, schedule.minute)
        }

        ScheduleTrigger.SUNRISE -> when {
            schedule.minute < 0 -> stringResource(R.string.schedule_before_sunrise, offset)
            schedule.minute > 0 -> stringResource(R.string.schedule_after_sunrise, offset)
            else -> stringResource(R.string.schedule_at_sunrise)
        }

        ScheduleTrigger.SUNSET -> when {
            schedule.minute < 0 -> stringResource(R.string.schedule_before_sunset, offset)
            schedule.minute > 0 -> stringResource(R.string.schedule_after_sunset, offset)
            else -> stringResource(R.string.schedule_at_sunset)
        }
    }
}

/** "Every day", "Every Monday, Tuesday", or empty. */
@Composable
internal fun daysSummary(days: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (days and Schedule.ALL_DAYS) {
        Schedule.ALL_DAYS -> stringResource(R.string.schedule_every_day)
        0 -> ""
        else -> stringResource(R.string.schedule_every_list, ScheduleDays.names(days, locale))
    }
}

private fun formatTime(context: Context, hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/** Returns a callback that asks for coarse location once, then reports the phone's last position. */
@Composable
private fun rememberPhoneLocation(onLocation: (Double, Double) -> Unit, onFailure: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readLocation(context, onLocation, onFailure) else onFailure()
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            readLocation(
                context,
                onLocation,
                onFailure,
            )
        } else {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

private fun readLocation(context: Context, onLocation: (Double, Double) -> Unit, onFailure: () -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val providers = manager?.getProviders(true).orEmpty()
    val last = if (granted) providers.mapNotNull { manager?.getLastKnownLocation(it) }.maxByOrNull { it.time } else null
    when {
        last != null -> onLocation(last.latitude, last.longitude)

        granted && providers.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            manager?.getCurrentLocation(providers.first(), null, context.mainExecutor) { location ->
                if (location != null) onLocation(location.latitude, location.longitude) else onFailure()
            }

        else -> onFailure()
    }
}
