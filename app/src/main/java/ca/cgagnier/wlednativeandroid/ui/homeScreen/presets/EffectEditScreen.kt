package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.presets.EffectControls
import ca.cgagnier.wlednativeandroid.service.presets.PresetEditorData

private const val SLIDER_MAX = 255f
private const val DEFAULT_BRIGHTNESS = 128

@Composable
internal fun EffectEditScreen(viewModel: PresetEditorViewModel, draft: EffectDraft, data: PresetEditorData) {
    val controls = data.controls(draft.preset.effectId)
    Scaffold(
        topBar = {
            EditorTopBar(R.string.preset_editor_edit_effect, viewModel, onSave = viewModel::saveEffect)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            EditorError(viewModel)
            Text(
                draft.preset.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            UsedIn(draft, data)
            EditorSectionLabel(R.string.preset_editor_settings)
            EditorSectionCard { SettingsSection(viewModel, draft, data, controls) }
            Text(
                text = stringResource(R.string.preset_editor_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            OutlinedButton(
                onClick = viewModel::resetEffect,
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
            ) {
                Text(stringResource(R.string.preset_editor_reset))
            }
        }
    }
}

/** Which playlists play this preset, since a change shows in all of them. */
@Composable
private fun UsedIn(draft: EffectDraft, data: PresetEditorData) {
    val names = data.presets.playlists.filter { draft.preset.id in it.presetIds }.map { it.name }
    if (names.isEmpty()) return
    Text(
        text = stringResource(R.string.preset_editor_used_in, names.joinToString(", ")),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SettingsSection(
    viewModel: PresetEditorViewModel,
    draft: EffectDraft,
    data: PresetEditorData,
    controls: EffectControls,
) {
    val settings = draft.settings
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        controls.speedLabel?.let { label ->
            SliderRow(
                label = label.ifEmpty { stringResource(R.string.preset_editor_speed) },
                value = settings.speed ?: controls.defaults.speed ?: 0,
                onChange = { v -> viewModel.updateEffect(preview = false) { it.copy(speed = v) } },
                onDone = viewModel::previewCurrent,
            )
        }
        controls.intensityLabel?.let { label ->
            SliderRow(
                label = label.ifEmpty { stringResource(R.string.preset_editor_intensity) },
                value = settings.intensity ?: controls.defaults.intensity ?: 0,
                onChange = { v -> viewModel.updateEffect(preview = false) { it.copy(intensity = v) } },
                onDone = viewModel::previewCurrent,
            )
        }
        if (controls.usesPalette && data.palettes.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            PaletteRow(
                palettes = data.palettes,
                selected = settings.palette ?: controls.defaults.palette ?: 0,
            ) { pal -> viewModel.updateEffect(preview = true) { it.copy(palette = pal) } }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        BrightnessRows(viewModel, settings.brightness)
    }
}

@Composable
private fun SliderRow(label: String, value: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            onValueChangeFinished = onDone,
            valueRange = 0f..SLIDER_MAX,
        )
    }
}

@Composable
private fun PaletteRow(palettes: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.preset_editor_palette),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(palettes.getOrNull(selected) ?: selected.toString(), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            palettes.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/** Off: the preset keeps whatever brightness the lights have. On: it sets its own. */
@Composable
private fun BrightnessRows(viewModel: PresetEditorViewModel, brightness: Int?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.preset_editor_set_brightness), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.preset_editor_brightness_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = brightness != null,
            onCheckedChange = { on ->
                viewModel.updateEffect(preview = true) { it.copy(brightness = if (on) DEFAULT_BRIGHTNESS else null) }
            },
        )
    }
    if (brightness != null) {
        SliderRow(
            label = stringResource(R.string.preset_editor_brightness),
            value = brightness,
            onChange = { v -> viewModel.updateEffect(preview = false) { it.copy(brightness = v.coerceAtLeast(1)) } },
            onDone = viewModel::previewCurrent,
        )
    }
}
