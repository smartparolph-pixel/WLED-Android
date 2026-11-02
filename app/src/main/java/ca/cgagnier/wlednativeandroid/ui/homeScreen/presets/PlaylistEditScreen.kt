package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.presets.PresetEditorData

private val ADD_LIST_MAX_HEIGHT = 420.dp

@Composable
internal fun PlaylistEditScreen(viewModel: PresetEditorViewModel, draft: PlaylistDraft, data: PresetEditorData) {
    var adding by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            EditorTopBar(
                title = if (draft.id ==
                    null
                ) {
                    R.string.preset_editor_new_playlist
                } else {
                    R.string.preset_editor_edit_playlist
                },
                viewModel = viewModel,
                onSave = viewModel::savePlaylist,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            EditorError(viewModel)
            NameField(viewModel, draft)
            EditorSectionLabel(R.string.preset_editor_timing)
            EditorSectionCard { TimingSection(viewModel, draft) }
            EditorSectionLabel(R.string.preset_editor_playlist_effects)
            EditorSectionCard { EffectsSection(viewModel, draft, data) }
            OutlinedButton(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.preset_editor_add_effects))
            }
            BottomActions(viewModel, draft)
        }
    }
    if (adding) {
        AddEffectsDialog(
            data = data,
            onDismiss = { adding = false },
            onAdd = { ids ->
                adding = false
                viewModel.updatePlaylist { it.copy(presetIds = it.presetIds + ids) }
            },
        )
    }
}

@Composable
private fun NameField(viewModel: PresetEditorViewModel, draft: PlaylistDraft) {
    if (draft.pack != null) {
        // A built-in playlist keeps its name, so the app recognises it (and does not add it again).
        Text(draft.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
    } else {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { name -> viewModel.updatePlaylist { it.copy(name = name) } },
            label = { Text(stringResource(R.string.preset_editor_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun TimingSection(viewModel: PresetEditorViewModel, draft: PlaylistDraft) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        StepperRow(
            label = stringResource(R.string.preset_editor_seconds),
            value = draft.seconds,
            choices = PLAYLIST_SECONDS,
        ) { seconds -> viewModel.updatePlaylist { it.copy(seconds = seconds) } }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        StepperRow(
            label = stringResource(R.string.preset_editor_fade),
            value = draft.fadeSeconds,
            choices = PLAYLIST_FADES,
        ) { fade -> viewModel.updatePlaylist { it.copy(fadeSeconds = fade) } }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.preset_editor_shuffle), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.preset_editor_shuffle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.shuffle,
                onCheckedChange = { shuffle -> viewModel.updatePlaylist { it.copy(shuffle = shuffle) } },
            )
        }
    }
}

/** "−  20 s  +" stepping through [choices]; a value not in the list steps to its neighbours. */
@Composable
private fun StepperRow(label: String, value: Int, choices: List<Int>, onChange: (Int) -> Unit) {
    val lower = choices.lastOrNull { it < value }
    val higher = choices.firstOrNull { it > value }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { lower?.let(onChange) }, enabled = lower != null) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.preset_editor_less))
        }
        Text(
            text = stringResource(R.string.preset_editor_seconds_value, value),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { higher?.let(onChange) }, enabled = higher != null) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.preset_editor_more))
        }
    }
}

@Composable
private fun EffectsSection(viewModel: PresetEditorViewModel, draft: PlaylistDraft, data: PresetEditorData) {
    if (draft.presetIds.isEmpty()) {
        Text(
            text = stringResource(R.string.preset_editor_error_no_effects),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    draft.presetIds.forEachIndexed { index, id ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = data.presets.preset(id)?.name ?: stringResource(R.string.preset_editor_missing, id),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.updatePlaylist { it.move(index, -1) } }, enabled = index > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.preset_editor_move_up))
            }
            IconButton(
                onClick = { viewModel.updatePlaylist { it.move(index, 1) } },
                enabled = index < draft.presetIds.lastIndex,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.preset_editor_move_down),
                )
            }
            IconButton(onClick = {
                viewModel.updatePlaylist { it.copy(presetIds = it.presetIds.filterIndexed { i, _ -> i != index }) }
            }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.preset_editor_remove))
            }
        }
        if (index < draft.presetIds.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    }
}

private fun PlaylistDraft.move(index: Int, by: Int): PlaylistDraft {
    val ids = presetIds.toMutableList()
    val target = index + by
    if (target !in ids.indices) return this
    ids.add(target, ids.removeAt(index))
    return copy(presetIds = ids)
}

/** Reset to default (built-in playlists) or Delete (the user's own). */
@Composable
private fun BottomActions(viewModel: PresetEditorViewModel, draft: PlaylistDraft) {
    when {
        draft.pack != null -> {
            OutlinedButton(
                onClick = viewModel::resetPlaylist,
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.preset_editor_reset))
            }
            Text(
                text = stringResource(R.string.preset_editor_reset_playlist_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp, start = 4.dp),
            )
        }

        draft.id != null -> Button(
            onClick = viewModel::deletePlaylist,
            enabled = !viewModel.saving,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        ) {
            Text(stringResource(R.string.preset_editor_delete))
        }
    }
}

/** Picks presets (effects) to add to the end of the playlist. */
@Composable
private fun AddEffectsDialog(data: PresetEditorData, onDismiss: () -> Unit, onAdd: (List<Int>) -> Unit) {
    val picked = remember { mutableStateListOf<Int>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_editor_add_effects)) },
        text = {
            Column(modifier = Modifier.heightIn(max = ADD_LIST_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                data.presets.presets.forEach { preset ->
                    val checked = preset.id in picked
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) picked.remove(preset.id) else picked.add(preset.id) },
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(picked.toList()) }, enabled = picked.isNotEmpty()) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
