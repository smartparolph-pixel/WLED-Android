@file:Suppress("MatchingDeclarationName") // The banner and its actions.

package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.direct.BACKGROUND_GRACE_MINUTES
import ca.cgagnier.wlednativeandroid.service.direct.DirectJoin
import ca.cgagnier.wlednativeandroid.service.direct.DirectJoinFailure

/** What the user can do from the status banner. */
data class DirectBannerActions(
    val onCancel: () -> Unit,
    val onLeave: () -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
    val onWifiSettings: () -> Unit,
)

private enum class Tone { Progress, Success, Problem, Info }

private data class BannerContent(
    val tone: Tone,
    val title: String,
    val text: String,
    val actions: List<Pair<Int, () -> Unit>>,
)

/**
 * "Connect directly" status at the top of the list: what is happening, whether it worked, why not, and what
 * to do next. Announced to screen readers as it changes.
 */
@Composable
fun DirectConnectionBanner(state: DirectJoin, actions: DirectBannerActions, modifier: Modifier = Modifier) {
    val content = bannerContent(state, actions) ?: return
    val (container, onContainer) = when (content.tone) {
        Tone.Problem -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        Tone.Success -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

        Tone.Progress, Tone.Info ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(content.tone, onContainer)
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = content.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                content.actions.forEach { (label, action) ->
                    TextButton(onClick = action) { Text(stringResource(label), color = onContainer) }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(tone: Tone, tint: Color) {
    val modifier = Modifier.size(24.dp)
    when (tone) {
        Tone.Progress -> CircularProgressIndicator(modifier = modifier.padding(2.dp), strokeWidth = 2.dp, color = tint)
        Tone.Success -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tint, modifier = modifier)
        Tone.Problem -> Icon(Icons.Filled.Warning, contentDescription = null, tint = tint, modifier = modifier)
        Tone.Info -> Icon(Icons.Filled.Info, contentDescription = null, tint = tint, modifier = modifier)
    }
}

@Composable
private fun bannerContent(state: DirectJoin, a: DirectBannerActions): BannerContent? {
    val cancel = R.string.cancel to a.onCancel
    val retry = R.string.direct_status_try_again to a.onRetry
    val dismiss = R.string.direct_status_dismiss to a.onDismiss
    return when (state) {
        DirectJoin.Idle -> null

        is DirectJoin.Searching -> BannerContent(
            Tone.Progress,
            stringResource(R.string.direct_connection_connecting_to, state.ssid),
            stringResource(R.string.direct_status_searching_text),
            listOf(cancel),
        )

        is DirectJoin.Verifying -> BannerContent(
            Tone.Progress,
            stringResource(R.string.direct_status_verifying_title, state.ssid),
            stringResource(R.string.direct_status_verifying_text),
            listOf(cancel),
        )

        is DirectJoin.Connected -> BannerContent(
            Tone.Success,
            stringResource(R.string.direct_status_connected_title, state.ssid),
            stringResource(R.string.direct_status_connected_text, BACKGROUND_GRACE_MINUTES),
            listOf(R.string.direct_status_leave to a.onLeave),
        )

        is DirectJoin.Failed -> failedContent(state, a, retry, dismiss)

        is DirectJoin.Ended -> if (state.automatically) {
            BannerContent(
                Tone.Info,
                stringResource(R.string.direct_status_left_title, state.ssid),
                stringResource(R.string.direct_status_left_text, BACKGROUND_GRACE_MINUTES),
                listOf(R.string.direct_status_reconnect to a.onRetry, dismiss),
            )
        } else {
            BannerContent(
                Tone.Problem,
                stringResource(R.string.direct_status_lost_title, state.ssid),
                stringResource(R.string.direct_status_lost_text),
                listOf(dismiss, retry),
            )
        }
    }
}

@Composable
private fun failedContent(
    state: DirectJoin.Failed,
    a: DirectBannerActions,
    retry: Pair<Int, () -> Unit>,
    dismiss: Pair<Int, () -> Unit>,
): BannerContent = when (state.reason) {
    DirectJoinFailure.WifiOff -> BannerContent(
        Tone.Problem,
        stringResource(R.string.direct_status_wifi_off_title),
        stringResource(R.string.direct_status_wifi_off_text, state.ssid),
        listOf(dismiss, R.string.direct_status_wifi_settings to a.onWifiSettings),
    )

    DirectJoinFailure.NotFound -> BannerContent(
        Tone.Problem,
        stringResource(R.string.direct_status_failed_title, state.ssid),
        stringResource(R.string.direct_status_not_found_text),
        listOf(dismiss, retry),
    )

    DirectJoinFailure.NoAnswer -> BannerContent(
        Tone.Problem,
        stringResource(R.string.direct_status_no_answer_title, state.ssid),
        stringResource(R.string.direct_status_no_answer_text),
        listOf(dismiss, retry),
    )

    DirectJoinFailure.NotSaved -> BannerContent(
        Tone.Problem,
        stringResource(R.string.direct_status_not_saved_title),
        stringResource(R.string.direct_connection_not_saved),
        listOf(dismiss),
    )
}
