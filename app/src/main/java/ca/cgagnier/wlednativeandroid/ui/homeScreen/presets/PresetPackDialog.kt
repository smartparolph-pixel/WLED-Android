package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.presets.PackSetupResult
import ca.cgagnier.wlednativeandroid.service.presets.PresetPacks
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

private val PLAYLIST_LIST_MAX_HEIGHT = 420.dp

/**
 * The device's playlists ([PresetPacks.ALL], then the user's own). Any built-in ones that are missing are
 * added without asking when this opens; tapping one plays it, and the one playing is ticked. Edit opens
 * [PresetEditorDialog].
 */
@Composable
fun PresetPackDialog(device: DeviceWithState, onDismiss: () -> Unit, viewModel: PresetPackViewModel = hiltViewModel()) {
    LaunchedEffect(device.device.macAddress) { viewModel.prepare(device.device) }
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) {
        PresetEditorDialog(
            device = device,
            onDismiss = {
                editing = false
                viewModel.refresh(device.device)
            },
        )
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.preset_pack_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.preset_pack_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                when (val setup = viewModel.setup) {
                    null -> Preparing()

                    is PackSetupResult.Ready -> {
                        PlaylistList(device, setup, viewModel)
                        presetPackNotes(setup)?.let { Note(it) }
                    }

                    PackSetupResult.NothingAvailable -> Note(stringResource(R.string.preset_pack_nothing))

                    PackSetupResult.PresetsUnreadable, PackSetupResult.Unreachable -> {
                        Note(presetPackSetupText(setup))
                        TextButton(onClick = { viewModel.retry(device.device) }) {
                            Text(stringResource(R.string.retry_connection))
                        }
                    }
                }
                if (viewModel.playFailed) Note(stringResource(R.string.preset_pack_unreachable))
                BottomButtons(
                    canEdit = viewModel.setup is PackSetupResult.Ready,
                    onEdit = { editing = true },
                    onDone = onDismiss,
                )
            }
        }
    }
}

/** Built-in playlists, then the user's own; tapping one plays it. */
@Composable
private fun PlaylistList(device: DeviceWithState, setup: PackSetupResult.Ready, viewModel: PresetPackViewModel) {
    val playingId = device.stateInfo.value?.state?.selectedPlaylistId
    Column(
        modifier = Modifier
            .heightIn(max = PLAYLIST_LIST_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        val rows = PresetPacks.ALL.map { it.playlist.name to setup.playlistIds[it] } +
            setup.custom.map { it.name to it.id }
        rows.forEach { (name, id) ->
            PlaylistRow(
                name = name,
                playing = id != null && id == playingId,
                starting = id != null && viewModel.starting == id,
                enabled = id != null && viewModel.starting == null,
                onClick = { id?.let { viewModel.play(device.device, it) } },
            )
        }
    }
}

/** Edit (opens the editor) at the bottom left, Done at the bottom right. */
@Composable
private fun BottomButtons(canEdit: Boolean, onEdit: () -> Unit, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onEdit, enabled = canEdit) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.preset_editor_edit))
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
    }
}

@Composable
private fun Preparing() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp).size(20.dp))
        Text(stringResource(R.string.preset_pack_preparing), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlaylistRow(name: String, playing: Boolean, starting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        when {
            starting -> CircularProgressIndicator(modifier = Modifier.size(18.dp))

            playing -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.preset_pack_playing),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

/** Effects that could not be added, and a warning if the device did not confirm the saves. */
@Composable
fun presetPackNotes(setup: PackSetupResult.Ready): String? = listOfNotNull(
    setup.unavailable.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.preset_pack_unavailable, it.joinToString()) },
    if (setup.verified) null else stringResource(R.string.preset_pack_unverified),
).joinToString(" ").ifEmpty { null }

/** What setting up the playlists did, for the end of "Set up a new device". */
@Composable
fun presetPackSetupText(setup: PackSetupResult): String = when (setup) {
    is PackSetupResult.Ready -> listOfNotNull(
        stringResource(R.string.preset_pack_ready, PresetPacks.PLAYS_AFTER_SETUP.playlist.name),
        presetPackNotes(setup),
    ).joinToString(" ")

    PackSetupResult.NothingAvailable -> stringResource(R.string.preset_pack_nothing)

    PackSetupResult.PresetsUnreadable -> stringResource(R.string.preset_editor_unreadable)

    PackSetupResult.Unreachable -> stringResource(R.string.preset_pack_unreachable)
}
