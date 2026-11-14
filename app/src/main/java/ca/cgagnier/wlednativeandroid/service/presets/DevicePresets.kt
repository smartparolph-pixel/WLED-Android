package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi

/** Effect settings a preset can override; null keeps the effect's default (or, for brightness, the current). */
data class EffectSettings(
    val speed: Int? = null,
    val intensity: Int? = null,
    val palette: Int? = null,
    val brightness: Int? = null,
)

/**
 * A preset that is not a playlist.
 *
 * @param effectId The effect it runs, when it is a single-effect preset the app can edit (saved as an API
 *   call, like the app's own); null for presets saved from WLED's web page with their full state.
 */
data class SavedPreset(val id: Int, val name: String, val effectId: Int?, val settings: EffectSettings) {
    val editable get() = effectId != null
}

/** A playlist on the device. Every entry uses [seconds] and [fadeSeconds] when saved from the app. */
data class SavedPlaylist(
    val id: Int,
    val name: String,
    val quickLoad: String?,
    val presetIds: List<Int>,
    val seconds: Int,
    val fadeSeconds: Int,
    val shuffle: Boolean,
) {
    /** The built-in pack this playlist comes from, or null for the user's own. */
    val pack get() = PresetPacks.byPlaylistName(name)
}

/** The device's presets.json, split into presets and playlists, each sorted by id. */
data class DevicePresets(val presets: List<SavedPreset>, val playlists: List<SavedPlaylist>) {
    fun preset(id: Int) = presets.firstOrNull { it.id == id }

    /** Playlists that are not built in, i.e. made by the user in the app or on WLED's web page. */
    val custom get() = playlists.filter { it.pack == null }

    /** Lowest id not used by any preset or playlist, or null if all are taken. */
    fun freeId(): Int? {
        val taken = presets.map { it.id }.toSet() + playlists.map { it.id }
        return (1..PresetPackPlanner.MAX_PRESET_ID).firstOrNull { it !in taken }
    }

    companion object {
        private const val TENTHS = 10

        fun parse(presetsJson: Map<*, *>): DevicePresets {
            val presets = mutableListOf<SavedPreset>()
            val playlists = mutableListOf<SavedPlaylist>()
            presetsJson.forEach { (key, value) ->
                val id = key.toString().toIntOrNull()?.takeIf { it in 1..PresetPackPlanner.MAX_PRESET_ID }
                val entry = (value as? Map<*, *>)?.takeIf { it.isNotEmpty() }
                if (id == null || entry == null) return@forEach
                val name = (entry["n"] as? String)?.takeIf { it.isNotBlank() } ?: "Preset $id"
                val playlist = entry["playlist"] as? Map<*, *>
                if (playlist != null) {
                    playlists += parsePlaylist(id, name, entry["ql"] as? String, playlist)
                } else {
                    presets += parsePreset(id, name, entry)
                }
            }
            return DevicePresets(presets.sortedBy { it.id }, playlists.sortedBy { it.id })
        }

        private fun parsePreset(id: Int, name: String, entry: Map<*, *>): SavedPreset {
            val seg = entry["seg"] as? Map<*, *>
            return SavedPreset(
                id = id,
                name = name,
                effectId = seg?.int("fx"),
                settings = EffectSettings(
                    speed = seg?.int("sx"),
                    intensity = seg?.int("ix"),
                    palette = seg?.int("pal"),
                    brightness = entry.int("bri"),
                ),
            )
        }

        private fun parsePlaylist(id: Int, name: String, quickLoad: String?, playlist: Map<*, *>) = SavedPlaylist(
            id = id,
            name = name,
            quickLoad = quickLoad,
            presetIds = (playlist["ps"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() },
            seconds = (first(playlist["dur"]) ?: 0) / TENTHS,
            fadeSeconds = (first(playlist["transition"]) ?: 0) / TENTHS,
            shuffle = when (val r = playlist["r"]) {
                is Boolean -> r
                is Number -> r.toInt() != 0
                else -> false
            },
        )

        /** A playlist's `dur` and `transition` are a list, or one number for every entry. */
        private fun first(value: Any?): Int? = when (value) {
            is Number -> value.toInt()
            is List<*> -> (value.firstOrNull() as? Number)?.toInt()
            else -> null
        }

        private fun Map<*, *>.int(key: String) = (this[key] as? Number)?.toInt()
    }
}

/**
 * What an effect offers, from `/json/fxdata` (the part after `@` of the effect's firmware data, e.g.
 * `Time [min],Width;;!;;sx=60`): which sliders it uses, their labels, and its defaults.
 *
 * @param speedLabel Null when the effect has no speed slider; "" for the standard label.
 */
data class EffectControls(
    val speedLabel: String?,
    val intensityLabel: String?,
    val usesPalette: Boolean,
    val defaults: EffectSettings,
) {
    companion object {
        private const val SLIDER_DEFAULT = 128
        private const val SECTION_SLIDERS = 0
        private const val SECTION_PALETTE = 2
        private const val SECTION_DEFAULTS = 4

        fun parse(data: String): EffectControls {
            if (data.isEmpty()) {
                // No metadata: the web page shows speed, intensity and palette.
                return EffectControls("", "", true, EffectSettings(SLIDER_DEFAULT, SLIDER_DEFAULT, 0))
            }
            val sections = data.split(";")
            val sliders = sections.getOrNull(SECTION_SLIDERS).orEmpty().split(",")
            val palette = sections.getOrNull(SECTION_PALETTE).orEmpty().split(",").first()
            val defaults = sections.getOrNull(SECTION_DEFAULTS).orEmpty().split(",")
                .mapNotNull { pair -> pair.split("=").takeIf { it.size == 2 }?.let { it[0] to it[1].toIntOrNull() } }
                .toMap()
            return EffectControls(
                speedLabel = label(sliders.getOrNull(0)),
                intensityLabel = label(sliders.getOrNull(1)),
                usesPalette = palette.isNotEmpty() && palette.toIntOrNull() == null,
                defaults = EffectSettings(
                    speed = defaults["sx"] ?: SLIDER_DEFAULT,
                    intensity = defaults["ix"] ?: SLIDER_DEFAULT,
                    palette = defaults["pal"] ?: 0,
                ),
            )
        }

        private fun label(slider: String?): String? = when (slider) {
            null, "" -> null
            "!" -> ""
            else -> slider
        }
    }
}

/** Bodies for `POST /json/state` used by the editor. Saving applies the preset or playlist to the lights. */
object PresetEditorPayloads {
    private val moshi: Moshi = Moshi.Builder().build()

    /** Saves a single-effect preset; settings left null use the effect's defaults. */
    fun presetJson(id: Int, name: String, effectId: Int, settings: EffectSettings): String =
        toJson(stateMap(effectId, settings) + mapOf("psave" to id, "n" to name, "o" to true))

    /** Shows the effect with these settings on the lights without saving anything. */
    fun previewJson(effectId: Int, settings: EffectSettings): String = toJson(stateMap(effectId, settings))

    /** Saves the playlist; the device starts playing it. */
    fun playlistJson(playlist: SavedPlaylist): String = PresetPackPayloads.playlistJson(
        PlaylistSpec(
            name = playlist.name,
            quickLoad = playlist.quickLoad,
            secondsPerPreset = playlist.seconds,
            fadeSeconds = playlist.fadeSeconds,
            shuffle = playlist.shuffle,
        ),
        PlaylistStep.Save(playlist.id, playlist.presetIds),
    )

    fun deleteJson(id: Int): String = toJson(mapOf("pdel" to id))

    private fun stateMap(effectId: Int, settings: EffectSettings): Map<String, Any> {
        val seg = listOfNotNull(
            "fx" to effectId,
            "fxdef" to true,
            settings.speed?.let { "sx" to it },
            settings.intensity?.let { "ix" to it },
            settings.palette?.let { "pal" to it },
        ).toMap()
        return listOfNotNull("on" to true, settings.brightness?.let { "bri" to it }, "seg" to seg).toMap()
    }

    private fun toJson(value: Any): String = moshi.adapter(Any::class.java).toJson(value)
}
