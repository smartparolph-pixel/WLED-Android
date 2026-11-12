package ca.cgagnier.wlednativeandroid.ui.homeScreen.shortcuts

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.homeScreen.presets.PresetPackDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.schedule.ScheduleDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.share.ShareDeviceDialog

private const val DISABLED_ALPHA = 0.38f

/** The features reachable from the main page, in bar order. */
private enum class Shortcut(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    SCHEDULE(R.string.schedule_title, R.drawable.baseline_date_range_24),
    PRESETS(R.string.preset_pack_button, R.drawable.baseline_auto_awesome_24),
    SHARE(R.string.shortcut_share, R.drawable.baseline_qr_code_24),
}

/**
 * Bottom bar on the main page with shortcuts to Schedule, Presets & playlists and Share Parol. With one
 * device the feature opens for it straight away; with several, a list asks which one first. The time zone,
 * set during setup, stays on the device's edit screen.
 */
@Composable
fun DeviceShortcutBar(devices: List<DeviceWithState>) {
    var choosingFor by remember { mutableStateOf<Shortcut?>(null) }
    var open by remember { mutableStateOf<Pair<Shortcut, DeviceWithState>?>(null) }

    BottomAppBar {
        Row(modifier = Modifier.fillMaxWidth()) {
            Shortcut.entries.forEach { shortcut ->
                ShortcutItem(
                    shortcut = shortcut,
                    enabled = devices.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (devices.size == 1) open = shortcut to devices.first() else choosingFor = shortcut
                    },
                )
            }
        }
    }

    choosingFor?.let { shortcut ->
        DevicePicker(
            devices = devices,
            onPick = {
                open = shortcut to it
                choosingFor = null
            },
            onDismiss = { choosingFor = null },
        )
    }
    open?.let { (shortcut, device) ->
        val close = { open = null }
        // One view model per device, so a dialog never shows another device's state.
        val key = "shortcut:${shortcut.name}:${device.device.macAddress}"
        when (shortcut) {
            Shortcut.SCHEDULE -> ScheduleDialog(device, close, hiltViewModel(key = key))
            Shortcut.PRESETS -> PresetPackDialog(device, close, hiltViewModel(key = key))
            Shortcut.SHARE -> ShareDeviceDialog(device.device, close)
        }
    }
}

/** Icon with its label underneath, the whole column as one button. */
@Composable
private fun ShortcutItem(shortcut: Shortcut, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(shortcut.icon),
            // The label below already names the action.
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = stringResource(shortcut.label),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun DevicePicker(devices: List<DeviceWithState>, onPick: (DeviceWithState) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shortcut_pick_device)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                devices.forEach { device ->
                    TextButton(onClick = { onPick(device) }, modifier = Modifier.fillMaxWidth()) {
                        Text(deviceName(device.device), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
