package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerOnTest {
    private val moshi = Moshi.Builder().build()

    private fun parse(json: String): Map<*, *> = moshi.adapter(Map::class.java).fromJson(json)!!

    @Test
    fun readsTheBootPresetFromTheConfig() {
        assertEquals(42, PowerOn.bootPreset(parse("""{"def":{"ps":42,"on":true,"bri":128}}""")))
        assertEquals(0, PowerOn.bootPreset(parse("""{"def":{"ps":0}}""")))
        assertEquals(0, PowerOn.bootPreset(parse("""{}""")))
    }

    @Test
    fun setsSkyFullOfSparksWhenNoneIsSetOrTheChosenOneIsGone() {
        val playlists = setOf(7, 12)
        assertEquals(7, PowerOn.presetToSet(current = 0, playlistIds = playlists, defaultId = 7))
        assertEquals(7, PowerOn.presetToSet(current = 30, playlistIds = playlists, defaultId = 7))
    }

    @Test
    fun aBootPresetThatIsNoLongerAPlaylistIsReplaced() {
        // Preset 3 exists but holds a single effect now (e.g. after a reset): power-on goes back to the default.
        val presets = parse(
            """
            {"3":{"n":"Fireworks","seg":{"fx":42}},
             "7":{"n":"Sky","playlist":{"ps":[1,2]}},
             "12":{"n":"Mine","playlist":{"ps":[3]}}}
            """.trimIndent(),
        )
        assertEquals(setOf(7, 12), PowerOn.playlistIds(presets))
        assertEquals(7, PowerOn.presetToSet(current = 3, playlistIds = PowerOn.playlistIds(presets), defaultId = 7))
    }

    @Test
    fun keepsTheUsersChoice() {
        assertNull(PowerOn.presetToSet(current = 12, playlistIds = setOf(7, 12), defaultId = 7))
        assertNull(PowerOn.presetToSet(current = 0, playlistIds = setOf(7), defaultId = null))
    }

    @Test
    fun configOnlyTouchesThePowerOnSettings() {
        assertEquals("""{"def":{"ps":7,"on":true,"bri":255}}""", PowerOn.configJson(7, PowerOn.FULL_BRIGHTNESS))
        assertEquals("""{"def":{"ps":12,"on":true}}""", PowerOn.configJson(12))
    }
}
