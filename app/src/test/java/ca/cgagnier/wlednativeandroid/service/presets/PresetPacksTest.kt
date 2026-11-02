package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetPacksTest {

    private val moshi = Moshi.Builder().build()

    private fun parse(json: String): Map<*, *> = moshi.adapter(Map::class.java).fromJson(json)!!

    /** Effect names as WLED 0.15.3 lists them: index = effect id. */
    private val effects = List(120) { "Effect $it" }.toMutableList().apply {
        this[20] = "Sparkle"
        this[49] = "Fairy"
        this[51] = "Fairytwinkle"
        this[80] = "Twinklefox"
        this[87] = "Glitter"
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
}
