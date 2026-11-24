package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePresetsTest {
    private val moshi = Moshi.Builder().build()

    private fun parse(json: String): Map<*, *> = moshi.adapter(Map::class.java).fromJson(json)!!

    /** As WLED 0.15.3 stores them: app presets as API calls, playlists as written by the firmware. */
    private val presetsJson = """{
        "0":{},
        "1":{"n":"Twinklefox","on":true,"seg":{"fx":80,"fxdef":true}},
        "2":{"n":"Sunrise","on":true,"bri":90,"seg":{"fx":104,"fxdef":true,"sx":1,"ix":40,"pal":5}},
        "3":{"n":"From the web page","on":true,"seg":[{"id":0,"fx":9}]},
        "6":{"n":"✨ Twinkle Tonight","ql":"✨","on":true,
             "playlist":{"ps":[1,2],"dur":[600,600],"transition":[10,10],"repeat":0,"end":0,"r":0}},
        "7":{"n":"Mine","playlist":{"ps":[2],"dur":150,"transition":30,"r":1}}
    }"""

    @Test
    fun splitsPresetsAndPlaylists() {
        val device = DevicePresets.parse(parse(presetsJson))
        assertEquals(listOf(1, 2, 3), device.presets.map { it.id })
        assertEquals(
            SavedPreset(2, "Sunrise", 104, EffectSettings(speed = 1, intensity = 40, palette = 5, brightness = 90)),
            device.preset(2),
        )
        assertFalse(device.preset(3)!!.editable)
        assertEquals(
            SavedPlaylist(6, "✨ Twinkle Tonight", "✨", listOf(1, 2), 60, 1, shuffle = false),
            device.playlists[0],
        )
        assertEquals(SavedPlaylist(7, "Mine", null, listOf(2), 15, 3, shuffle = true), device.playlists[1])
        assertEquals(PresetPacks.TWINKLE_TONIGHT, device.playlists[0].pack)
        assertEquals(listOf("Mine"), device.custom.map { it.name })
        assertEquals(4, device.freeId())
    }

    @Test
    fun effectControlsFollowTheFirmwareMetadata() {
        val sunrise = EffectControls.parse("Time [min],Width;;!;;sx=60")
        assertEquals("Time [min]", sunrise.speedLabel)
        assertEquals("Width", sunrise.intensityLabel)
        assertTrue(sunrise.usesPalette)
        assertEquals(60, sunrise.defaults.speed)

        val fade = EffectControls.parse("!;!,!;!;01")
        assertEquals("", fade.speedLabel)
        assertNull(fade.intensityLabel)
        assertTrue(fade.usesPalette)

        val noPalette = EffectControls.parse("!,!;!,!;;1")
        assertFalse(noPalette.usesPalette)

        val legacy = EffectControls.parse("")
        assertEquals("", legacy.intensityLabel)
    }

    @Test
    fun editorPayloads() {
        val save = parse(PresetEditorPayloads.presetJson(2, "Sunrise", 104, EffectSettings(speed = 3, brightness = 80)))
        assertEquals(2.0, save["psave"])
        assertEquals(true, save["o"])
        assertEquals(80.0, save["bri"])
        assertEquals(mapOf("fx" to 104.0, "fxdef" to true, "sx" to 3.0), save["seg"])

        val preview = parse(PresetEditorPayloads.previewJson(104, EffectSettings(palette = 4)))
        assertNull(preview["psave"])
        assertEquals(mapOf("fx" to 104.0, "fxdef" to true, "pal" to 4.0), preview["seg"])

        val playlist = parse(
            PresetEditorPayloads.playlistJson(SavedPlaylist(7, "Mine", null, listOf(2, 1), 15, 2, shuffle = true)),
        )
        assertEquals(7.0, playlist["psave"])
        assertNull(playlist["ql"])
        val body = playlist["playlist"] as Map<*, *>
        assertEquals(listOf(150.0, 150.0), body["dur"])
        assertEquals(listOf(20.0, 20.0), body["transition"])
        assertEquals(true, body["r"])

        assertEquals(mapOf("pdel" to 7.0), parse(PresetEditorPayloads.deleteJson(7)))
    }
}
