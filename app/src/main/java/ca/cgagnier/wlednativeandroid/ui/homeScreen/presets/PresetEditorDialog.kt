package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.presets.PresetEditorData
import ca.cgagnier.wlednativeandroid.service.presets.PresetEditorLoad
import ca.cgagnier.wlednativeandroid.service.presets.SavedPlaylist
import ca.cgagnier.wlednativeandroid.service.presets.SavedPreset
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.homeScreen.schedule.SectionShape

/**
 * Full-screen editor for a device's playlists and effect presets, laid out like the Schedule screens:
 * a list, and an edit screen for the playlist or effect tapped.
 */
@Composable
fun PresetEditorDialog(
    device: DeviceWithState,
    onDismiss: () -> Unit,
    viewModel: PresetEditorViewModel = hiltViewModel(key = "presetEditor:${device.device.macAddress}"),
) {
    LaunchedEffect(device.device.macAddress) { viewModel.open(device.device) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val data = viewModel.data
            when (val screen = viewModel.screen) {
                PresetEditorScreen.List -> EditorListScreen(viewModel, onBack = onDismiss)

                is PresetEditorScreen.Playlist -> if (data != null) {
                    BackHandler { viewModel.back() }
                    PlaylistEditScreen(viewModel, screen.draft, data)
                }

                is PresetEditorScreen.Effect -> if (data != null) {
                    BackHandler { viewModel.back() }
                    EffectEditScreen(viewModel, screen.draft, data)
                }
            }
        }
    }
}

@Composable
private fun EditorListScreen(viewModel: PresetEditorViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.preset_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.description_back_button),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::newPlaylist, enabled = viewModel.data != null) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.preset_editor_new_playlist))
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
            when (val load = viewModel.load) {
                null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is PresetEditorLoad.Loaded -> EditorLists(viewModel, load.data)

                PresetEditorLoad.Unreadable -> Problem(R.string.preset_editor_unreadable, viewModel::reload)

                PresetEditorLoad.Unreachable -> Problem(R.string.preset_pack_unreachable, viewModel::reload)
            }
        }
    }
}

@Composable
private fun Problem(text: Int, onRetry: () -> Unit) {
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 16.dp),
    )
    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_connection)) }
}

@Composable
private fun EditorLists(viewModel: PresetEditorViewModel, data: PresetEditorData) {
    val playlists = data.presets.playlists
    EditorError(viewModel)
    PowerOnSection(viewModel, data)
    EditorSectionLabel(R.string.preset_editor_playlists)
    EditorSectionCard {
        playlists.forEachIndexed { index, playlist ->
            PlaylistListRow(playlist) { viewModel.editPlaylist(playlist) }
            if (index < playlists.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
    val effects = data.presets.presets.filter { it.editable }
    if (effects.isNotEmpty()) {
        EditorSectionLabel(R.string.preset_editor_effects)
        EditorSectionCard {
            effects.forEachIndexed { index, preset ->
                EffectListRow(preset) { viewModel.editEffect(preset) }
                if (index < effects.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
    Text(
        text = stringResource(R.string.preset_editor_list_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
    )
}

/** "Plays when powered on": the playlist the lantern starts when it is plugged in or power comes back. */
@Composable
private fun PowerOnSection(viewModel: PresetEditorViewModel, data: PresetEditorData) {
    var open by remember { mutableStateOf(false) }
    val current = data.presets.playlists.firstOrNull { it.id == data.powerOnId }
    EditorSectionLabel(R.string.preset_editor_power_on)
    EditorSectionCard {
        Box {
            ListRow(
                title = current?.name ?: stringResource(R.string.preset_editor_power_on_none),
                summary = stringResource(R.string.preset_editor_power_on_hint),
                locked = false,
                onClick = { if (!viewModel.saving) open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                data.presets.playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text(playlist.name) },
                        onClick = {
                            open = false
                            if (playlist.id != data.powerOnId) viewModel.setPowerOn(playlist)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistListRow(playlist: SavedPlaylist, onClick: () -> Unit) {
    val locked = playlist.pack?.locked == true
    val summary = if (locked) {
        stringResource(R.string.preset_editor_random_summary)
    } else {
        listOfNotNull(
            stringResource(R.string.preset_editor_playlist_summary, playlist.presetIds.size, playlist.seconds),
            stringResource(R.string.preset_editor_shuffle).takeIf { playlist.shuffle },
        ).joinToString(" · ")
    }
    ListRow(title = playlist.name, summary = summary, locked = locked, onClick = onClick)
}

@Composable
private fun EffectListRow(preset: SavedPreset, onClick: () -> Unit) {
    val customized = preset.settings.intensity != null || preset.settings.palette != null ||
        preset.settings.brightness != null || preset.settings.speed != null
    val summary = stringResource(
        if (customized) R.string.preset_editor_effect_custom else R.string.preset_editor_effect_default,
    )
    ListRow(title = preset.name, summary = summary, locked = false, onClick = onClick)
}

@Composable
private fun ListRow(title: String, summary: String, locked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (locked) stringResource(R.string.preset_editor_locked) else null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EditorSectionLabel(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
internal fun EditorSectionCard(content: @Composable () -> Unit) {
    Card(
        shape = SectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

/** Error text from the last save, shown at the top of an edit screen. */
@Composable
internal fun EditorError(viewModel: PresetEditorViewModel) {
    viewModel.error?.let {
        Text(
            text = stringResource(it),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/** Back on the left, Save on the right, as on the Schedule edit screen. */
@Composable
internal fun EditorTopBar(title: Int, viewModel: PresetEditorViewModel, onSave: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            IconButton(onClick = viewModel::back) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.description_back_button),
                )
            }
        },
        actions = {
            TextButton(onClick = onSave, enabled = !viewModel.saving) {
                Text(stringResource(R.string.schedule_save), style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}
