package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetPacksTest {

    private val moshi = Moshi.Builder().build()

    private fun parse(json: String): Map<*, *> = moshi.adapter(Map::class.java).fromJson(json)!!

    /**
     * Every effect the packs use, with its id in WLED 0.15.3 (FX.h). All are 1D effects that need no
     * microphone, so they animate on a single LED strip.
     */
    private val wled0153 = mapOf(
        "Twinklefox" to 80, "Fairy" to 49, "Fairytwinkle" to 51, "Glitter" to 87, "Sparkle" to 20,
        "Fireworks 1D" to 90, "Fireworks Starburst" to 89, "Fireworks" to 42, "Popcorn" to 95, "Sparkle+" to 22,
        "Pacifica" to 101, "Lake" to 75, "Noise Pal" to 107, "Flow" to 110, "Aurora" to 38,
        "Rainbow" to 9, "Colorwaves" to 67, "Pride 2015" to 63, "Theater Rainbow" to 14, "Chase Rainbow" to 30,
        "Phased Noise" to 109, "Twinkleup" to 106, "Perlin Move" to 147, "Plasma" to 97,
        "Candle" to 88, "Candle Multi" to 102, "Fire 2012" to 66, "Fire Flicker" to 45,
        "Strobe Rainbow" to 24, "Bpm" to 68, "Ripple Rainbow" to 99, "Chase Flash Rnd" to 32, "Dynamic" to 7,
        "Meteor" to 76, "Lighthouse" to 41, "Sinelon Rainbow" to 94, "Multi Comet" to 59, "Juggle" to 64,
        "Sunrise" to 104, "Blends" to 115, "Breathe" to 2, "Fade" to 12, "Heartbeat" to 100,
        "Halloween Eyes" to 82, "Lightning" to 57, "Strobe Mega" to 25, "Washing Machine" to 113,
        "Dancing Shadows" to 112,
    )

    /** `/json/eff` as WLED 0.15.3 returns it: index = effect id. */
    private val effects = List(190) { "Effect $it" }.toMutableList().apply {
        wled0153.forEach { (name, id) -> this[id] = name }
    }

    private val pack = PresetPacks.TWINKLE_TONIGHT

    @Test
    fun emptyDeviceGetsLowestFreeIdsAndFirmwareEffectIds() {
        val plan = PresetPackPlanner.plan(pack, parse("""{"0":{}}"""), effects)
        assertEquals(
            listOf(
                PresetStep.Create("Twinklefox", 1, 80),
                PresetStep.Create("Fairy", 2, 49),
                PresetStep.Create("Fairytwinkle", 3, 51),
                PresetStep.Create("Glitter", 4, 87),
                PresetStep.Create("Sparkle", 5, 20),
            ),
            plan.presets,
        )
        assertEquals(PlaylistStep.Save(6, listOf(1, 2, 3, 4, 5)), plan.playlist)
    }

    @Test
    fun skipsTakenSlotsIncludingSchedulePresets() {
        val presets = """{"1":{"n":"Mine"},"3":{"n":"Also mine"},"250":{"n":"Schedule: Off"}}"""
        val plan = PresetPackPlanner.plan(pack, parse(presets), effects)
        assertEquals(listOf(2, 4, 5, 6, 7), plan.created.map { it.id })
        assertEquals(PlaylistStep.Save(8, listOf(2, 4, 5, 6, 7)), plan.playlist)
    }

    @Test
    fun runningTwiceAddsNothingAndOnlyPlays() {
        val presets = """{
            "1":{"n":"Twinklefox"},"2":{"n":"Fairy"},"3":{"n":"Fairytwinkle"},"4":{"n":"Glitter"},"5":{"n":"Sparkle"},
            "6":{"n":"✨ Twinkle Tonight","playlist":{"ps":[1,2,3,4,5]}}
        }"""
        val plan = PresetPackPlanner.plan(pack, parse(presets), effects)
        assertTrue(plan.created.isEmpty())
        assertEquals(5, plan.reused.size)
        assertEquals(PlaylistStep.Play(6), plan.playlist)
    }

    @Test
    fun deletedPresetIsRecreatedAndPlaylistUpdatedInPlace() {
        val presets = """{
            "1":{"n":"Twinklefox"},"3":{"n":"Fairytwinkle"},"4":{"n":"Glitter"},"5":{"n":"Sparkle"},
            "6":{"n":"✨ Twinkle Tonight","playlist":{"ps":[1,2,3,4,5]}}
        }"""
        val plan = PresetPackPlanner.plan(pack, parse(presets), effects)
        assertEquals(listOf(PresetStep.Create("Fairy", 2, 49)), plan.created)
        assertEquals(PlaylistStep.Play(6), plan.playlist)

        val moved = presets.replace("\"5\":{\"n\":\"Sparkle\"}", "\"9\":{\"n\":\"Sparkle\"}")
        val replan = PresetPackPlanner.plan(pack, parse(moved), effects)
        assertEquals(PlaylistStep.Save(6, listOf(1, 2, 3, 4, 9)), replan.playlist)
    }

    @Test
    fun missingEffectIsLeftOutOfThePlaylist() {
        val older = effects.toMutableList().apply { this[87] = "RSVD" }
        val plan = PresetPackPlanner.plan(pack, parse("{}"), older)
        assertEquals(listOf(PresetStep.MissingEffect("Glitter", "Glitter")), plan.unavailable)
        assertEquals(PlaylistStep.Save(5, listOf(1, 2, 3, 4)), plan.playlist)
    }

    @Test
    fun fullDeviceSkipsThePlaylist() {
        val full = parse((1..250).joinToString(",", "{", "}") { "\"$it\":{\"n\":\"P$it\"}" })
        val plan = PresetPackPlanner.plan(pack, full, effects)
        assertEquals(5, plan.unavailable.size)
        assertEquals(PlaylistStep.Skip, plan.playlist)
    }

    @Test
    fun presetPayloadRunsTheEffectWithItsDefaults() {
        val body = parse(PresetPackPayloads.presetJson(PresetStep.Create("Twinklefox", 1, 80)))
        assertEquals(1.0, body["psave"])
        assertEquals("Twinklefox", body["n"])
        assertEquals(true, body["o"])
        assertEquals(true, body["on"])
        assertEquals(mapOf("fx" to 80.0, "fxdef" to true), body["seg"])
    }

    @Test
    fun playlistPayloadUsesTenthsOfSecondsAndLoopsForever() {
        val body = parse(PresetPackPayloads.playlistJson(pack.playlist, PlaylistStep.Save(6, listOf(1, 2, 3, 4, 5))))
        assertEquals(6.0, body["psave"])
        assertEquals("✨ Twinkle Tonight", body["n"])
        assertEquals("✨", body["ql"])
        assertEquals(true, body["on"])
        val playlist = body["playlist"] as Map<*, *>
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), playlist["ps"])
        assertEquals(List(5) { 600.0 }, playlist["dur"])
        assertEquals(List(5) { 10.0 }, playlist["transition"])
        assertEquals(0.0, playlist["repeat"])
        assertEquals(false, playlist["r"])
    }

    @Test
    fun namesFitTheFirmwareLimit() {
        // WLED keeps 32 bytes of a preset name.
        PresetPacks.ALL.forEach { p ->
            (p.presets.map { it.name } + p.playlist.name).forEach {
                assertTrue(it, it.toByteArray(Charsets.UTF_8).size <= 32)
            }
            assertTrue((p.playlist.quickLoad ?: "").codePointCount(0, (p.playlist.quickLoad ?: "").length) <= 2)
        }
    }

    @Test
    fun everyPackEffectIsAWled0153StripEffect() {
        PresetPacks.ALL.flatMap { it.presets }.forEach { preset ->
            assertTrue(preset.effect, preset.effect in wled0153)
            assertEquals(preset.effect, preset.name)
        }
    }

    @Test
    fun tenPlaylistsEachStartingWithItsIcon() {
        assertEquals(10, PresetPacks.ALL.size)
        assertEquals(10, PresetPacks.ALL.map { it.playlist.name }.toSet().size)
        PresetPacks.ALL.forEach {
            val icon = it.playlist.quickLoad!!
            assertTrue(it.playlist.name, it.playlist.name.startsWith("$icon "))
        }
        assertEquals("✨ Twinkle Tonight", PresetPacks.PLAYS_AFTER_SETUP.playlist.name)
    }

    @Test
    fun slowPlaylistsPlayLonger() {
        assertEquals(60, PresetPacks.TWINKLE_TONIGHT.playlist.secondsPerPreset)
        listOf(PresetPacks.CHILL_MODE, PresetPacks.RISE_AND_SHINE).forEach {
            assertEquals(60, it.playlist.secondsPerPreset)
            assertEquals(3, it.playlist.fadeSeconds)
        }
        assertEquals(20, PresetPacks.SKY_FULL_OF_SPARKS.playlist.secondsPerPreset)
        assertEquals(1, PresetPacks.SKY_FULL_OF_SPARKS.playlist.fadeSeconds)
    }

    @Test
    fun effectSharedBetweenPlaylistsIsSavedOnce() {
        // Chill Mode already added "Aurora" as preset 5; Northern Glow reuses it.
        val presets = parse("""{"5":{"n":"Aurora"}}""")
        val plan = PresetPackPlanner.plan(PresetPacks.NORTHERN_GLOW, presets, effects)
        assertEquals(listOf(PresetStep.Reuse("Aurora", 5)), plan.reused)
        assertEquals(4, plan.created.size)
    }
}
