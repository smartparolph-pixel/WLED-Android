package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi

/** A preset that runs one firmware effect with that effect's own defaults (speed, intensity, palette). */
data class EffectPreset(
    val name: String,
    /** The effect's name exactly as the firmware lists it in `/json/eff`. */
    val effect: String,
)

/** A playlist that plays the pack's presets in order. */
data class PlaylistSpec(
    val name: String,
    /** Quick-load label for the WLED web page's shortcut buttons, at most 2 characters. */
    val quickLoad: String? = null,
    val secondsPerPreset: Int,
    val fadeSeconds: Int,
    /** Loop forever; otherwise play once. */
    val loop: Boolean = true,
    val shuffle: Boolean = false,
)

/**
 * Presets plus the playlist that plays them. To ship another set, add a pack to [PresetPacks.ALL]; the
 * installer, the button on the device screen and the step after device setup all pick it up.
 */
data class PresetPack(val presets: List<EffectPreset>, val playlist: PlaylistSpec)

object PresetPacks {
    val TWINKLE_TONIGHT = PresetPack(
        presets = listOf(
            EffectPreset("Twinklefox", effect = "Twinklefox"),
            EffectPreset("Fairy", effect = "Fairy"),
            EffectPreset("Fairytwinkle", effect = "Fairytwinkle"),
            EffectPreset("Glitter", effect = "Glitter"),
            EffectPreset("Sparkle", effect = "Sparkle"),
        ),
        playlist = PlaylistSpec(
            name = "✨ Twinkle Tonight",
            quickLoad = "✨",
            secondsPerPreset = 60,
            fadeSeconds = 1,
        ),
    )

    val ALL = listOf(TWINKLE_TONIGHT)
}

/** What to do for one preset of a pack. */
sealed interface PresetStep {
    val name: String

    /** A preset with this name is already on the device; it is used as is. */
    data class Reuse(override val name: String, val id: Int) : PresetStep

    data class Create(override val name: String, val id: Int, val effectId: Int) : PresetStep

    /** The device's firmware has no effect with this name. */
    data class MissingEffect(override val name: String, val effect: String) : PresetStep

    /** All 250 preset slots are taken. */
    data class NoFreeSlot(override val name: String) : PresetStep
}

/** What to do for the playlist. */
sealed interface PlaylistStep {
    /** Already on the device with exactly these presets: only start it. */
    data class Play(val id: Int) : PlaylistStep

    /** Save at [id] (new, or replacing a same-named playlist whose presets changed), which also starts it. */
    data class Save(val id: Int, val presetIds: List<Int>) : PlaylistStep

    /** None of the presets could be added, or no free slot is left. */
    data object Skip : PlaylistStep
}

data class PackPlan(val presets: List<PresetStep>, val playlist: PlaylistStep) {
    val created get() = presets.filterIsInstance<PresetStep.Create>()
    val reused get() = presets.filterIsInstance<PresetStep.Reuse>()
    val unavailable get() = presets.filter { it is PresetStep.MissingEffect || it is PresetStep.NoFreeSlot }
}

/**
 * Decides what to add to a device, so a pack can be installed any number of times without making
 * duplicates. Matching is by name: a preset or playlist with the same name is reused, not added again.
 */
object PresetPackPlanner {
    const val MAX_PRESET_ID = 250

    /**
     * @param presetsJson The device's `/presets.json`.
     * @param effects The device's `/json/eff`: effect names, index = effect id.
     */
    fun plan(pack: PresetPack, presetsJson: Map<*, *>, effects: List<String>): PackPlan {
        val existing = existingPresets(presetsJson)
        val slots = FreeSlots(existing.keys)
        val steps = pack.presets.map { presetStep(it, existing, effects, slots) }
        val presetIds = steps.mapNotNull { it.presetId }
        return PackPlan(steps, playlistStep(pack.playlist, presetIds, existing, slots))
    }

    private fun presetStep(
        preset: EffectPreset,
        existing: Map<Int, Existing>,
        effects: List<String>,
        slots: FreeSlots,
    ): PresetStep {
        val reuseId = existing.entries.firstOrNull { it.value.name == preset.name }?.key
        val effectId = effects.indexOf(preset.effect)
        return when {
            reuseId != null -> PresetStep.Reuse(preset.name, reuseId)

            effectId < 0 -> PresetStep.MissingEffect(preset.name, preset.effect)

            else -> slots.next()?.let { PresetStep.Create(preset.name, it, effectId) }
                ?: PresetStep.NoFreeSlot(preset.name)
        }
    }

    private fun playlistStep(
        spec: PlaylistSpec,
        presetIds: List<Int>,
        existing: Map<Int, Existing>,
        slots: FreeSlots,
    ): PlaylistStep {
        if (presetIds.isEmpty()) return PlaylistStep.Skip
        val current = existing.entries.firstOrNull { it.value.name == spec.name }
        return when {
            current == null -> slots.next()?.let { PlaylistStep.Save(it, presetIds) } ?: PlaylistStep.Skip
            current.value.playlistPresets == presetIds -> PlaylistStep.Play(current.key)
            else -> PlaylistStep.Save(current.key, presetIds)
        }
    }

    /** Hands out the lowest unused ids, so new presets stay clear of the Schedule presets at the top. */
    private class FreeSlots(taken: Set<Int>) {
        private val taken = taken.toMutableSet()

        fun next(): Int? = (1..MAX_PRESET_ID).firstOrNull { it !in taken }?.also { taken += it }
    }

    private val PresetStep.presetId: Int?
        get() = when (this) {
            is PresetStep.Reuse -> id
            is PresetStep.Create -> id
            else -> null
        }

    private data class Existing(val name: String, val playlistPresets: List<Int>?)

    private fun existingPresets(presetsJson: Map<*, *>): Map<Int, Existing> =
        presetsJson.entries.mapNotNull { (key, value) ->
            val id = key.toString().toIntOrNull()?.takeIf { it in 1..MAX_PRESET_ID } ?: return@mapNotNull null
            val preset = (value as? Map<*, *>)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = (preset["n"] as? String)?.takeIf { it.isNotBlank() } ?: "Preset $id"
            val playlist = (preset["playlist"] as? Map<*, *>)?.get("ps") as? List<*>
            id to Existing(name, playlist?.mapNotNull { (it as? Number)?.toInt() })
        }.toMap()
}

/** Bodies for `POST /json/state`. Note the device applies each one to the lights as it saves it. */
object PresetPackPayloads {
    private const val TENTHS_PER_SECOND = 10
    private val moshi: Moshi = Moshi.Builder().build()

    /** Saves a preset that turns the light on and runs [effectId] with the effect's default settings. */
    fun presetJson(step: PresetStep.Create): String = toJson(
        mapOf(
            "psave" to step.id,
            "n" to step.name,
            "o" to true,
            "on" to true,
            "seg" to mapOf("fx" to step.effectId, "fxdef" to true),
        ),
    )

    /** Saves the playlist; the device starts playing it right away. */
    fun playlistJson(spec: PlaylistSpec, step: PlaylistStep.Save): String {
        val count = step.presetIds.size
        val body = mutableMapOf<String, Any>(
            "psave" to step.id,
            "n" to spec.name,
            "o" to true,
            "on" to true,
            "playlist" to mapOf(
                "ps" to step.presetIds,
                "dur" to List(count) { spec.secondsPerPreset * TENTHS_PER_SECOND },
                "transition" to List(count) { spec.fadeSeconds * TENTHS_PER_SECOND },
                // 0 repeats forever; 1 plays the list once.
                "repeat" to if (spec.loop) 0 else 1,
                "end" to 0,
                "r" to spec.shuffle,
            ),
        )
        spec.quickLoad?.let { body["ql"] = it }
        return toJson(body)
    }

    /** Starts an existing preset or playlist. */
    fun playJson(id: Int): String = toJson(mapOf("ps" to id))

    private fun toJson(value: Any): String = moshi.adapter(Any::class.java).toJson(value)
}
