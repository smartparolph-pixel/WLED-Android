package ca.cgagnier.wlednativeandroid.service.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanternStatusTest {
    @Test
    fun namesComeFromPresetsJson() {
        val presets = mapOf(
            "0" to emptyMap<String, Any>(),
            "7" to mapOf("n" to "Twinkle"),
            "12" to mapOf("n" to " Sky Full of Sparks ", "playlist" to emptyMap<String, Any>()),
            "13" to mapOf("on" to true),
        )
        assertEquals(mapOf(7 to "Twinkle", 12 to "Sky Full of Sparks"), LanternStatus.presetNames(presets))
    }

    @Test
    fun aRunningPlaylistIsNamedOverItsPreset() {
        assertEquals(12, LanternStatus.playingId(presetId = 7, playlistId = 12))
        assertEquals(7, LanternStatus.playingId(presetId = 7, playlistId = -1))
        assertNull(LanternStatus.playingId(presetId = -1, playlistId = -1))
        assertNull(LanternStatus.playingId(presetId = 0, playlistId = null))
    }

    @Test
    fun onForIsSplitIntoDaysHoursMinutes() {
        assertEquals(LanternStatus.Duration(0, 0, 0), LanternStatus.onFor(59))
        assertEquals(LanternStatus.Duration(0, 0, 2), LanternStatus.onFor(150))
        assertEquals(LanternStatus.Duration(0, 1, 5), LanternStatus.onFor(3900))
        assertEquals(LanternStatus.Duration(1, 2, 0), LanternStatus.onFor(93_600))
        assertEquals(LanternStatus.Duration(0, 0, 0), LanternStatus.onFor(-5))
    }
}
