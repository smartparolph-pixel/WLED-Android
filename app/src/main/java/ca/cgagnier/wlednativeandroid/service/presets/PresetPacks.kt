package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi

/** A preset that runs one firmware effect with that effect's own defaults (speed, intensity, palette). */
data class EffectPreset(
    val name: String,
    /** The effect's name exactly as the firmware lists it in `/json/eff`. */
    val effect: String,
    /** Overrides the effect's default speed, when that default does not suit a playlist. */
    val speed: Int? = null,
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
data class PresetPack(
    val presets: List<EffectPreset>,
    val playlist: PlaylistSpec,
    /** Kept exactly as defined: not editable in the app, and put back if changed on the device. */
    val locked: Boolean = false,
)

object PresetPacks {
    private const val DEFAULT_SECONDS = 20
    private const val DEFAULT_FADE = 1
    private const val SLOW_SECONDS = 60
    private const val SLOW_FADE = 3

    /**
     * Speeds that differ from the effect's default. Sunrise's speed is its length in minutes (60 by
     * default), which looks dark for most of a 60 s playlist slot; 1 makes it rise within the slot.
     */
    private val SPEEDS = mapOf("Sunrise" to 1)

    /**
     * A pack whose presets are named after their effects, so an effect used by several playlists is saved
     * once. The playlist name starts with [icon], which is also its quick-load button.
     */
    private fun pack(
        icon: String,
        name: String,
        effects: List<String>,
        seconds: Int = DEFAULT_SECONDS,
        fade: Int = DEFAULT_FADE,
    ) = PresetPack(
        presets = effects.map { EffectPreset(it, effect = it, speed = SPEEDS[it]) },
        playlist = PlaylistSpec(
            name = "$icon $name",
            quickLoad = icon,
            secondsPerPreset = seconds,
            fadeSeconds = fade,
        ),
    )

    // Effect names are WLED 0.15.3's; ids in PresetPacksTest.

    val TWINKLE_TONIGHT = pack(
        "✨",
        "Twinkle Tonight",
        listOf("Twinklefox", "Fairy", "Fairytwinkle", "Glitter", "Sparkle"),
        seconds = SLOW_SECONDS,
    )

    val SKY_FULL_OF_SPARKS = pack(
        "🎆",
        "Sky Full of Sparks",
        listOf("Fireworks 1D", "Fireworks Starburst", "Fireworks", "Popcorn", "Sparkle+"),
    )

    val CHILL_MODE = pack(
        "🌊",
        "Chill Mode",
        listOf("Pacifica", "Lake", "Noise Pal", "Flow", "Aurora"),
        seconds = SLOW_SECONDS,
        fade = SLOW_FADE,
    )

    val RAINBOW_RUSH = pack(
        "🌈",
        "Rainbow Rush",
        listOf("Rainbow", "Colorwaves", "Pride 2015", "Theater Rainbow", "Chase Rainbow"),
    )

    val NORTHERN_GLOW = pack(
        "🌌",
        "Northern Glow",
        listOf("Aurora", "Phased Noise", "Twinkleup", "Perlin Move", "Plasma"),
    )

    val FLAME_ON = pack(
        "🔥",
        "Flame On",
        listOf("Candle", "Candle Multi", "Fire 2012", "Fire Flicker"),
    )

    /** A party mix that needs no microphone (WLED's sound-reactive effects stay dark without one). */
    val BEAT_DROP = pack(
        "🎵",
        "Beat Drop",
        listOf("Strobe Rainbow", "Bpm", "Ripple Rainbow", "Chase Flash Rnd", "Dynamic"),
    )

    val COMET_CHASERS = pack(
        "☄️",
        "Comet Chasers",
        listOf("Meteor", "Lighthouse", "Sinelon Rainbow", "Multi Comet", "Juggle"),
    )

    val RISE_AND_SHINE = pack(
        "🌅",
        "Rise & Shine",
        listOf("Sunrise", "Blends", "Breathe", "Fade", "Heartbeat"),
        seconds = SLOW_SECONDS,
        fade = SLOW_FADE,
    )

    val SPOOKY_SWITCH = pack(
        "🎃",
        "Spooky Switch",
        listOf("Halloween Eyes", "Lightning", "Strobe Mega", "Washing Machine", "Dancing Shadows"),
    )

    private val THEMED = listOf(
        TWINKLE_TONIGHT,
        SKY_FULL_OF_SPARKS,
        CHILL_MODE,
        RAINBOW_RUSH,
        NORTHERN_GLOW,
        FLAME_ON,
        BEAT_DROP,
        COMET_CHASERS,
        RISE_AND_SHINE,
        SPOOKY_SWITCH,
    )

    /** Every effect of the other playlists in a new random order each loop. It cannot be edited. */
    val RANDOM = PresetPack(
        presets = THEMED.flatMap { it.presets }.distinctBy { it.name },
        playlist = PlaylistSpec(
            name = "🎲 Random",
            quickLoad = "🎲",
            secondsPerPreset = DEFAULT_SECONDS,
            fadeSeconds = DEFAULT_FADE,
            shuffle = true,
        ),
        locked = true,
    )

    val ALL = THEMED + RANDOM

    /** The built-in pack whose playlist has this name, if any. */
    fun byPlaylistName(name: String): PresetPack? = ALL.firstOrNull { it.playlist.name == name }

    /** The default playlist: started after "Set up a new device" and when playlists are first added. */
    val PLAYS_AFTER_SETUP = SKY_FULL_OF_SPARKS
}

/** What to do for one preset of a pack. */
sealed interface PresetStep {
    val name: String

    /** A preset with this name is already on the device; it is used as is. */
    data class Reuse(override val name: String, val id: Int) : PresetStep

    /** Save at [id]: a new preset, or one put back to its defaults. */
    data class Create(override val name: String, val id: Int, val effectId: Int, val speed: Int? = null) :
        PresetStep

    /** The device's firmware has no effect with this name. */
    data class MissingEffect(override val name: String, val effect: String) : PresetStep

    /** All 250 preset slots are taken. */
    data class NoFreeSlot(override val name: String) : PresetStep
}

/** What to do for the playlist. */
sealed interface PlaylistStep {
    /** Already on the device (possibly edited by the user, which is kept); nothing to save. */
    data class Play(val id: Int) : PlaylistStep

    /** Save at [id] (new, reset to default, or a locked playlist that was changed), which also starts it. */
    data class Save(val id: Int, val presetIds: List<Int>) : PlaylistStep

    /** None of the presets could be added, or no free slot is left. */
    data object Skip : PlaylistStep
}

/** The playlist's preset id once the plan is carried out, or null if it is skipped. */
val PlaylistStep.playlistId: Int?
    get() = when (this) {
        is PlaylistStep.Play -> id
        is PlaylistStep.Save -> id
        PlaylistStep.Skip -> null
    }

data class PackPlan(val presets: List<PresetStep>, val playlist: PlaylistStep) {
    val created get() = presets.filterIsInstance<PresetStep.Create>()
    val reused get() = presets.filterIsInstance<PresetStep.Reuse>()
    val unavailable get() = presets.filter { it is PresetStep.MissingEffect || it is PresetStep.NoFreeSlot }
}

/**
 * Decides what to add to a device, so a pack can be installed any number of times without making
 * duplicates. Matching is by name: a preset or playlist with the same name is reused, not added again,
 * so the user's edits to it are kept. Only a [PresetPack.locked] playlist is put back if it changed.
 */
object PresetPackPlanner {
    const val MAX_PRESET_ID = 250

    /**
     * Plans several packs from one read of `/presets.json`: each pack sees what the packs before it will
     * save, so shared effects (e.g. Aurora) are saved once and ids never collide.
     */
    fun planAll(
        packs: List<PresetPack>,
        presetsJson: Map<*, *>,
        effects: List<String>,
    ): List<Pair<PresetPack, PackPlan>> {
        val device = presetsJson.entries.associate { it.key.toString() to it.value }.toMutableMap()
        return packs.map { pack ->
            val plan = plan(pack, device, effects)
            plan.created.forEach { step ->
                device[step.id.toString()] = mapOf("n" to step.name, "seg" to mapOf("sx" to step.speed))
            }
            (plan.playlist as? PlaylistStep.Save)?.let {
                device[it.id.toString()] = mapOf("n" to pack.playlist.name, "playlist" to mapOf("ps" to it.presetIds))
            }
            pack to plan
        }
    }

    /**
     * @param presetsJson The device's `/presets.json`.
     * @param effects The device's `/json/eff`: effect names, index = effect id.
     */
    fun plan(pack: PresetPack, presetsJson: Map<*, *>, effects: List<String>): PackPlan =
        plan(pack, presetsJson, effects, reset = false)

    /**
     * "Reset to default": saves the pack's presets and playlist again as defined, at the ids they have
     * now (or new ones if missing), undoing the user's edits.
     */
    fun reset(pack: PresetPack, presetsJson: Map<*, *>, effects: List<String>): PackPlan =
        plan(pack, presetsJson, effects, reset = true)

    private fun plan(pack: PresetPack, presetsJson: Map<*, *>, effects: List<String>, reset: Boolean): PackPlan {
        val existing = existingPresets(presetsJson)
        val slots = FreeSlots(existing.keys)
        val steps = pack.presets.map { presetStep(it, existing, effects, slots, reset) }
        val presetIds = steps.mapNotNull { it.presetId }
        val playlist = playlistStep(pack, presetIds, existing, slots, reset || pack.locked)
        return PackPlan(steps, playlist)
    }

    private fun presetStep(
        preset: EffectPreset,
        existing: Map<Int, Existing>,
        effects: List<String>,
        slots: FreeSlots,
        reset: Boolean,
    ): PresetStep {
        val current = existing.entries.firstOrNull { it.value.name == preset.name }
        val effectId = effects.indexOf(preset.effect)
        // A preset the app saved before its speed override existed has no speed: give it the override.
        val outdated = current != null && preset.speed != null && current.value.speed == null
        return when {
            current != null && !reset && !(outdated && effectId >= 0) -> PresetStep.Reuse(preset.name, current.key)

            effectId < 0 -> PresetStep.MissingEffect(preset.name, preset.effect)

            else -> (current?.key ?: slots.next())?.let { PresetStep.Create(preset.name, it, effectId, preset.speed) }
                ?: PresetStep.NoFreeSlot(preset.name)
        }
    }

    private fun playlistStep(
        pack: PresetPack,
        presetIds: List<Int>,
        existing: Map<Int, Existing>,
        slots: FreeSlots,
        enforce: Boolean,
    ): PlaylistStep {
        if (presetIds.isEmpty()) return PlaylistStep.Skip
        val current = existing.entries.firstOrNull { it.value.name == pack.playlist.name }
        val keep = current != null && current.value.playlistPresets != null &&
            (!enforce || current.value.playlistPresets == presetIds)
        return when {
            current == null -> slots.next()?.let { PlaylistStep.Save(it, presetIds) } ?: PlaylistStep.Skip
            keep -> PlaylistStep.Play(current.key)
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

    private data class Existing(val name: String, val playlistPresets: List<Int>?, val speed: Int?)

    private fun existingPresets(presetsJson: Map<*, *>): Map<Int, Existing> =
        presetsJson.entries.mapNotNull { (key, value) ->
            val id = key.toString().toIntOrNull()?.takeIf { it in 1..MAX_PRESET_ID } ?: return@mapNotNull null
            val preset = (value as? Map<*, *>)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = (preset["n"] as? String)?.takeIf { it.isNotBlank() } ?: "Preset $id"
            val playlist = (preset["playlist"] as? Map<*, *>)?.get("ps") as? List<*>
            val speed = ((preset["seg"] as? Map<*, *>)?.get("sx") as? Number)?.toInt()
            id to Existing(name, playlist?.mapNotNull { (it as? Number)?.toInt() }, speed)
        }.toMap()
}

/** Bodies for `POST /json/state`. Note the device applies each one to the lights as it saves it. */
object PresetPackPayloads {
    private const val TENTHS_PER_SECOND = 10
    private val moshi: Moshi = Moshi.Builder().build()

    /**
     * Saves a preset that turns the light on and runs the effect with its default settings (the firmware
     * applies `fxdef` before `sx`, so a speed override wins).
     */
    fun presetJson(step: PresetStep.Create): String = toJson(
        mapOf(
            "psave" to step.id,
            "n" to step.name,
            "o" to true,
            "on" to true,
            "seg" to listOfNotNull(
                "fx" to step.effectId,
                "fxdef" to true,
                step.speed?.let { "sx" to it },
            ).toMap(),
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

    /** Starts a preset or playlist. */
    fun playJson(id: Int): String = toJson(mapOf("ps" to id))

    private fun toJson(value: Any): String = moshi.adapter(Any::class.java).toJson(value)
}
