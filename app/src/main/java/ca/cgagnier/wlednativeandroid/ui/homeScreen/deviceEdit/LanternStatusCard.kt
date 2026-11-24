package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.status.LanternStatus
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import kotlinx.coroutines.delay

private const val TICK_MS = 30_000L
private const val MILLIS_PER_SECOND = 1000L

/** The lantern's status: its name, what is playing, and how long it has been on. Read only. */
@Composable
fun LanternStatusCard(device: DeviceWithState, presetNames: Map<Int, String>, modifier: Modifier = Modifier) {
    val stateInfo = device.stateInfo.value
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(deviceName(device.device), style = MaterialTheme.typography.titleMedium)
            when {
                stateInfo == null || !device.isOnline -> {
                    StatusLine(stringResource(R.string.lantern_status_not_connected))
                }
                stateInfo.state.isOn == false -> StatusLine(stringResource(R.string.lantern_status_off))
                else -> {
                    val state = stateInfo.state
                    val id = LanternStatus.playingId(state.selectedPresetId, state.selectedPlaylistId)
                    val playing = when {
                        id == null -> stringResource(R.string.lantern_status_nothing)
                        else -> presetNames[id] ?: stringResource(R.string.lantern_status_preset_number, id)
                    }
                    StatusLine(stringResource(R.string.lantern_status_now_playing, playing))
                    stateInfo.info.uptime?.let { OnFor(it.toLong()) }
                }
            }
        }
    }
}

/** "On for …", counted on from the last uptime the lantern sent (it only sends one when something changes). */
@Composable
private fun OnFor(uptimeSeconds: Long) {
    val receivedAt = remember(uptimeSeconds) { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = SystemClock.elapsedRealtime()
        }
    }
    val elapsed = ((now - receivedAt) / MILLIS_PER_SECOND).coerceAtLeast(0)
    val onFor = LanternStatus.onFor(uptimeSeconds + elapsed)
    val text = when {
        onFor.days > 0 -> stringResource(R.string.lantern_status_days_hours, onFor.days, onFor.hours)
        onFor.hours > 0 -> stringResource(R.string.lantern_status_hours_minutes, onFor.hours, onFor.minutes)
        else -> stringResource(R.string.lantern_status_minutes, onFor.minutes)
    }
    StatusLine(stringResource(R.string.lantern_status_on_for, text))
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(top = 4.dp),
    )
}
