package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlinx.coroutines.delay

/** How long a lantern that was connected keeps looking connected while the app reconnects to it. */
const val RECONNECT_GRACE_MS = 10_000L

/**
 * Whether to show [device] as connected. A lantern that was connected keeps looking connected for
 * [RECONNECT_GRACE_MS] after its connection drops: WLED closes websockets when it has too many open (the
 * list, the lantern page, a browser) and the app reconnects within seconds, and showing every one of those
 * short gaps made the card's status line flash. A lantern that hasn't been reached yet shows as it is.
 */
@Composable
fun rememberSteadyOnline(device: DeviceWithState): Boolean {
    val online = device.isOnline
    var wasOnline by remember(device) { mutableStateOf(online) }
    var gapTooLong by remember(device) { mutableStateOf(false) }
    LaunchedEffect(device, online) {
        if (online) {
            wasOnline = true
            gapTooLong = false
        } else {
            delay(RECONNECT_GRACE_MS)
            gapTooLong = true
        }
    }
    return online || (wasOnline && !gapTooLong)
}

/** The websocket status to draw: connected while [steadyOnline] bridges a short reconnect. */
fun shownStatus(device: DeviceWithState, steadyOnline: Boolean): WebsocketStatus =
    if (steadyOnline) WebsocketStatus.CONNECTED else device.websocketStatus.value
