package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.presets.EffectSettings
import ca.cgagnier.wlednativeandroid.service.presets.PresetEditorLoad
import ca.cgagnier.wlednativeandroid.service.presets.PresetEditorPayloads
import ca.cgagnier.wlednativeandroid.service.presets.PresetEditorService
import ca.cgagnier.wlednativeandroid.service.presets.PresetPack
import ca.cgagnier.wlednativeandroid.service.presets.PresetPacks
import ca.cgagnier.wlednativeandroid.service.presets.SavedPlaylist
import ca.cgagnier.wlednativeandroid.service.presets.SavedPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** WLED keeps 32 bytes of a name and plays at most 100 entries in a playlist. */
private const val MAX_NAME_BYTES = 32
private const val MAX_PLAYLIST_ENTRIES = 100
private const val RELOAD_DELAY_MS = 1000L
private const val NEW_PLAYLIST_SECONDS = 20
private const val NEW_PLAYLIST_FADE = 1
private const val MAX_FADE_SECONDS = 10

/** Choices for "Each effect plays", in seconds. */
@Suppress("MagicNumber") // The choices themselves.
val PLAYLIST_SECONDS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600)

/** Choices for "Fade between effects", in seconds. */
val PLAYLIST_FADES = (0..MAX_FADE_SECONDS).toList()

/** A playlist being edited; [id] is null for a new one. */
data class PlaylistDraft(
    val id: Int?,
    val name: String,
    val quickLoad: String?,
    val presetIds: List<Int>,
    val seconds: Int,
    val fadeSeconds: Int,
    val shuffle: Boolean,
) {
    /** The built-in pack it comes from; its name cannot change and it can be reset but not deleted. */
    val pack: PresetPack? get() = id?.let { PresetPacks.byPlaylistName(name) }
}

data class EffectDraft(val preset: SavedPreset, val settings: EffectSettings)

sealed interface PresetEditorScreen {
    data object List : PresetEditorScreen

    data class Playlist(val draft: PlaylistDraft) : PresetEditorScreen

    data class Effect(val draft: EffectDraft) : PresetEditorScreen
}

/**
 * The editor behind "Edit" in Presets & playlists: the device's playlists and single-effect presets,
 * read from presets.json and saved back one at a time through the JSON API.
 */
@HiltViewModel
class PresetEditorViewModel @Inject constructor(private val service: PresetEditorService) : ViewModel() {
    /** Null while loading. */
    var load by mutableStateOf<PresetEditorLoad?>(null)
        private set
    var screen by mutableStateOf<PresetEditorScreen>(PresetEditorScreen.List)
        private set
    var saving by mutableStateOf(false)
        private set

    @get:StringRes
    var error by mutableStateOf<Int?>(null)
        private set

    private var device: Device? = null

    val data get() = (load as? PresetEditorLoad.Loaded)?.data

    fun open(device: Device) {
        if (this.device?.macAddress == device.macAddress && load is PresetEditorLoad.Loaded) return
        this.device = device
        reload()
    }

    fun reload() {
        val device = device ?: return
        load = null
        viewModelScope.launch { load = service.load(device) }
    }

    fun back() {
        screen = PresetEditorScreen.List
        error = null
    }

    fun newPlaylist() {
        error = null
        screen = PresetEditorScreen.Playlist(
            PlaylistDraft(null, "", null, emptyList(), NEW_PLAYLIST_SECONDS, NEW_PLAYLIST_FADE, shuffle = false),
        )
    }

    fun editPlaylist(playlist: SavedPlaylist) {
        if (playlist.pack?.locked == true) return
        error = null
        screen = PresetEditorScreen.Playlist(
            PlaylistDraft(
                id = playlist.id,
                name = playlist.name,
                quickLoad = playlist.quickLoad,
                presetIds = playlist.presetIds,
                seconds = playlist.seconds.coerceAtLeast(1),
                fadeSeconds = playlist.fadeSeconds,
                shuffle = playlist.shuffle,
            ),
        )
    }

    fun updatePlaylist(change: (PlaylistDraft) -> PlaylistDraft) {
        val current = screen as? PresetEditorScreen.Playlist ?: return
        screen = current.copy(draft = change(current.draft))
    }

    fun editEffect(preset: SavedPreset) {
        if (!preset.editable) return
        error = null
        val draft = EffectDraft(preset, preset.settings)
        screen = PresetEditorScreen.Effect(draft)
        previewEffect(draft)
    }

    /** Changes the effect being edited and, if [preview], shows it on the lights right away. */
    fun updateEffect(preview: Boolean, change: (EffectSettings) -> EffectSettings) {
        val current = screen as? PresetEditorScreen.Effect ?: return
        val draft = current.draft.copy(settings = change(current.draft.settings))
        screen = PresetEditorScreen.Effect(draft)
        if (preview) previewEffect(draft)
    }

    /** Shows the effect being edited on the lights, e.g. when a slider is let go. */
    fun previewCurrent() {
        (screen as? PresetEditorScreen.Effect)?.draft?.let(::previewEffect)
    }

    private fun previewEffect(draft: EffectDraft) {
        val device = device ?: return
        val effectId = draft.preset.effectId ?: return
        viewModelScope.launch { service.post(device, PresetEditorPayloads.previewJson(effectId, draft.settings)) }
    }

    fun savePlaylist() {
        val draft = (screen as? PresetEditorScreen.Playlist)?.draft?.let { it.copy(name = it.name.trim()) } ?: return
        val id = draft.id ?: data?.presets?.freeId()
        error = playlistProblem(draft) ?: R.string.preset_editor_error_full.takeIf { id == null }
        if (error != null || id == null) return
        val playlist = SavedPlaylist(
            id = id,
            name = draft.name,
            quickLoad = draft.quickLoad,
            presetIds = draft.presetIds,
            seconds = draft.seconds,
            fadeSeconds = draft.fadeSeconds,
            shuffle = draft.shuffle,
        )
        send(PresetEditorPayloads.playlistJson(playlist))
    }

    @StringRes
    private fun playlistProblem(draft: PlaylistDraft): Int? {
        val others = data?.presets?.playlists.orEmpty().filter { it.id != draft.id }.map { it.name }
        return when {
            draft.name.isEmpty() -> R.string.preset_editor_error_name

            draft.name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> R.string.preset_editor_error_name_long

            draft.name in others || (draft.id == null && PresetPacks.byPlaylistName(draft.name) != null) ->
                R.string.preset_editor_error_name_taken

            draft.presetIds.isEmpty() -> R.string.preset_editor_error_no_effects

            draft.presetIds.size > MAX_PLAYLIST_ENTRIES -> R.string.preset_editor_error_too_many

            else -> null
        }
    }

    /** Makes the lantern start [playlist] when power comes back (plugged in, after a power cut). */
    fun setPowerOn(playlist: SavedPlaylist) {
        val device = device ?: return
        perform { service.setPowerOn(device, playlist.id) }
    }

    fun deletePlaylist() {
        val id = (screen as? PresetEditorScreen.Playlist)?.draft?.id ?: return
        send(PresetEditorPayloads.deleteJson(id))
    }

    fun resetPlaylist() {
        val pack = (screen as? PresetEditorScreen.Playlist)?.draft?.pack ?: return
        val device = device ?: return
        perform { service.reset(device, pack) }
    }

    fun saveEffect() {
        val draft = (screen as? PresetEditorScreen.Effect)?.draft ?: return
        val effectId = draft.preset.effectId ?: return
        send(PresetEditorPayloads.presetJson(draft.preset.id, draft.preset.name, effectId, draft.settings))
    }

    /** Saves the effect with its default settings (or the app's, e.g. Sunrise's speed). */
    fun resetEffect() {
        val draft = (screen as? PresetEditorScreen.Effect)?.draft ?: return
        val effectId = draft.preset.effectId ?: return
        val speed = PresetPacks.ALL.flatMap { it.presets }.firstOrNull { it.name == draft.preset.name }?.speed
        send(PresetEditorPayloads.presetJson(draft.preset.id, draft.preset.name, effectId, EffectSettings(speed)))
    }

    private fun send(body: String) {
        val device = device ?: return
        perform { service.post(device, body) }
    }

    /** Runs a save; on success goes back to the list and reads presets.json again. */
    private fun perform(action: suspend () -> Boolean) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            val ok = action()
            saving = false
            if (ok) {
                // Playlists are written after the device answers; give it time before reading back.
                delay(RELOAD_DELAY_MS)
                screen = PresetEditorScreen.List
                reload()
            } else {
                error = R.string.preset_editor_error_save
            }
        }
    }
}
