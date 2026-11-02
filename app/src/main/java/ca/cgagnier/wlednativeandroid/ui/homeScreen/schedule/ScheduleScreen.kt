package ca.cgagnier.wlednativeandroid.ui.homeScreen.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.schedule.Schedule
import ca.cgagnier.wlednativeandroid.service.schedule.ScheduleDays
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

private const val PIN_LENGTH = 4
internal val SectionShape = RoundedCornerShape(24.dp)

/**
 * Full-screen schedule manager for one device: the list (with swipe-to-delete and on/off toggles) and
 * the New/Edit screen. Uses the same schedules as Config → Time & Macros on the device.
 */
@Composable
fun ScheduleDialog(device: DeviceWithState, onDismiss: () -> Unit, viewModel: ScheduleViewModel = hiltViewModel()) {
    val info = device.stateInfo.value?.info
    LaunchedEffect(device.device.macAddress) {
        viewModel.load(device.device, info?.buildId, info?.platformName)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val editor = viewModel.state.editor
            if (editor == null) {
                ScheduleListScreen(viewModel, onBack = onDismiss)
            } else {
                BackHandler { viewModel.closeEditor() }
                ScheduleEditScreen(viewModel, editor)
            }
        }
    }
}

@Composable
private fun ScheduleListScreen(viewModel: ScheduleViewModel, onBack: () -> Unit) {
    val state = viewModel.state
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.schedule_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.description_back_button),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openNew, enabled = !state.loading && state.canAddAny) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.schedule_add))
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
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.schedules.isEmpty() -> Text(
                    text = stringResource(R.string.schedule_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> ScheduleList(viewModel)
            }
            if (!state.loading && !state.canAddAny) {
                Text(
                    text = stringResource(R.string.schedule_full),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ScheduleList(viewModel: ScheduleViewModel) {
    val state = viewModel.state
    Card(
        shape = SectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        state.schedules.forEachIndexed { index, schedule ->
            key(index, schedule) {
                ScheduleRow(
                    schedule = schedule,
                    action = state.actionFor(schedule),
                    enabledToggle = !state.saving,
                    onClick = { viewModel.openEdit(index) },
                    onToggle = { viewModel.setEnabled(index, it) },
                    onDelete = { viewModel.delete(index) },
                )
            }
            if (index < state.schedules.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 88.dp))
        }
    }
}

@Composable
@Suppress("LongParameterList") // Stateless row: data plus one callback per gesture.
private fun ScheduleRow(
    schedule: Schedule,
    action: ScheduleAction,
    enabledToggle: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.schedule_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            ActionBadge(action)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(scheduleTimeText(schedule), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = daysSummary(schedule.days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle, enabled = enabledToggle)
        }
    }
}

/** Light bulb with ON / OFF (or the preset's name) underneath. */
@Composable
private fun ActionBadge(action: ScheduleAction) {
    val isOn = action != ScheduleAction.TurnOff
    val label = when (action) {
        ScheduleAction.TurnOn -> stringResource(R.string.schedule_on)
        ScheduleAction.TurnOff -> stringResource(R.string.schedule_off)
        is ScheduleAction.Preset -> action.name.ifEmpty { stringResource(R.string.schedule_no_preset) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_lightbulb_24),
                contentDescription = null,
                tint = if (isOn) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Error text, PIN entry and retry, shared by both screens. */
@Composable
internal fun StatusMessages(viewModel: ScheduleViewModel) {
    val state = viewModel.state
    state.error?.let {
        Text(
            text = stringResource(it),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    if (state.needsPin) {
        OutlinedTextField(
            value = state.pin,
            onValueChange = { if (it.length <= PIN_LENGTH) viewModel.setPin(it) },
            label = { Text(stringResource(R.string.schedule_pin)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.error != null && state.editor == null) {
        TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.schedule_retry)) }
    }
}

/** Day circles shown under "Repeat", Sunday first. */
@Composable
internal fun DayCircles(days: Int, onToggle: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        ScheduleDays.DISPLAY_ORDER.forEach { bit ->
            val selected = ScheduleDays.isSelected(days, bit)
            val name = ScheduleDays.name(bit, locale)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    )
                    .clickable(onClickLabel = name) { onToggle(bit) },
            ) {
                Text(
                    text = ScheduleDays.letter(bit, locale),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Red "Delete Schedule" button at the bottom of the edit screen. */
@Composable
internal fun DeleteButton(onDelete: () -> Unit) {
    Button(
        onClick = onDelete,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Text(stringResource(R.string.schedule_delete))
    }
}
