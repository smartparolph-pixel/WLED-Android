package ca.cgagnier.wlednativeandroid.service.schedule

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SchedulesTest {

    private val moshi = Moshi.Builder().build()

    private fun parse(json: String): List<*> = moshi.adapter(List::class.java).fromJson(json)!!

    private val evening =
        Schedule(ScheduleTrigger.TIME, hour = 18, minute = 30, presetId = 250, days = 0x7F, enabled = true)
    private val sunset =
        Schedule(ScheduleTrigger.SUNSET, hour = 0, minute = -15, presetId = 249, days = 0x1F, enabled = true)

    @Test
    fun decodesCurrentFormat() {
        val json = """[
            {"en":1,"hour":18,"min":30,"macro":250,"dow":127,"start":{"mon":1,"day":1},"end":{"mon":12,"day":31}},
            {"en":0,"hour":254,"min":-15,"macro":249,"dow":31,"start":{"mon":3,"day":5},"end":{"mon":10,"day":20}},
            {"en":1,"hour":255,"min":10,"macro":3,"dow":96}
        ]"""
        val schedules = ScheduleCodec.decode(parse(json), currentFormat = true)
        assertEquals(3, schedules.size)
        assertEquals(evening, schedules[0])
        assertEquals(ScheduleTrigger.SUNSET, schedules[1].trigger)
        assertEquals(-15, schedules[1].minute)
        assertFalse(schedules[1].enabled)
        assertEquals(3, schedules[1].startMonth)
        assertEquals(20, schedules[1].endDay)
        assertEquals(ScheduleTrigger.SUNRISE, schedules[2].trigger)
    }

    @Test
    fun decodesLegacyFormatAndSkipsEmptySlots() {
        // As serialized by WLED 0.15: only used time slots, then sunrise and sunset (both hour 255) always.
        val json = """[
            {"en":1,"hour":18,"min":30,"macro":250,"dow":127,"start":{"mon":1,"day":1},"end":{"mon":12,"day":31}},
            {"en":0,"hour":255,"min":0,"macro":0,"dow":127},
            {"en":1,"hour":255,"min":-15,"macro":249,"dow":31}
        ]"""
        val schedules = ScheduleCodec.decode(parse(json), currentFormat = false)
        assertEquals(listOf(evening, sunset), schedules)
    }

    @Test
    fun encodesCurrentFormatAsPlainList() {
        val encoded = ScheduleCodec.encode(listOf(evening, sunset), currentFormat = true)
        assertEquals(2, encoded.size)
        assertEquals(254, encoded[1]["hour"])
        assertEquals(-15, encoded[1]["min"])
        assertEquals(mapOf("mon" to 1, "day" to 1), encoded[1]["start"])
    }

    @Test
    fun encodesLegacyFormatAsAllTenSlots() {
        val encoded = ScheduleCodec.encode(listOf(sunset, evening), currentFormat = false)
        assertEquals(10, encoded.size)
        assertEquals(18, encoded[0]["hour"])
        // Unused time slots are written empty so the device forgets deleted schedules.
        (1..7).forEach { assertEquals(0, encoded[it]["macro"]) }
        // Slot 8 = sunrise (empty here), slot 9 = sunset; both use hour 255 and carry no dates.
        assertEquals(255, encoded[8]["hour"])
        assertEquals(0, encoded[8]["macro"])
        assertEquals(255, encoded[9]["hour"])
        assertEquals(249, encoded[9]["macro"])
        assertNull(encoded[9]["start"])
    }

    @Test
    fun legacyRoundTrip() {
        val encoded = ScheduleCodec.encode(listOf(evening, sunset), currentFormat = false)
        val json = moshi.adapter(Any::class.java).toJson(encoded)
        assertEquals(listOf(evening, sunset), ScheduleCodec.decode(parse(json), currentFormat = false))
    }

    @Test
    fun formatDependsOnBuild() {
        assertFalse(ScheduleCodec.isCurrentFormat(2_412_100))
        assertFalse(ScheduleCodec.isCurrentFormat(null))
        assertTrue(ScheduleCodec.isCurrentFormat(2_605_010))
    }

    @Test
    fun legacyLimits() {
        val eightTimes = List(8) { evening }
        assertFalse(ScheduleLimits.LEGACY.canAdd(eightTimes, ScheduleTrigger.TIME))
        assertTrue(ScheduleLimits.LEGACY.canAdd(eightTimes, ScheduleTrigger.SUNRISE))
        assertFalse(ScheduleLimits.LEGACY.canAdd(listOf(sunset), ScheduleTrigger.SUNSET))
        assertTrue(ScheduleLimits.current(isEsp8266 = false).canAdd(List(63) { sunset }, ScheduleTrigger.SUNSET))
        assertFalse(ScheduleLimits.current(isEsp8266 = true).canAdd(List(16) { evening }, ScheduleTrigger.TIME))
    }

    @Test
    fun presetsNamesAndFreeSlots() {
        val presets = moshi.adapter(Map::class.java).fromJson(
            """{"0":{},"1":{"n":"Christmas","on":true},"250":{"n":"Schedule: On","on":true},"7":{"on":true}}""",
        )!!
        val names = SchedulePresets.names(presets)
        assertEquals(mapOf(1 to "Christmas", 250 to "Schedule: On", 7 to "Preset 7"), names)
        assertEquals(250, SchedulePresets.find(names, SchedulePresets.ON_NAME))
        assertNull(SchedulePresets.find(names, SchedulePresets.OFF_NAME))
        assertEquals(249, SchedulePresets.freeSlot(names))
        assertEquals(248, SchedulePresets.freeSlot(names, taken = setOf(249)))
        assertEquals(
            """{"psave":249,"n":"Schedule: Off","on":false,"o":true}""",
            SchedulePresets.saveJson(249, SchedulePresets.OFF_NAME, on = false),
        )
    }

    @Test
    fun configPayload() {
        val timers = ScheduleCodec.encode(listOf(evening), currentFormat = true)
        assertEquals(
            """{"timers":{"ins":[{"en":1,"hour":18,"min":30,"macro":250,"dow":127,""" +
                """"start":{"mon":1,"day":1},"end":{"mon":12,"day":31}}]},"if":{"ntp":{"en":true}},"pin":"1234"}""",
            SchedulePayloads.configJson(timers, ntp = mapOf("en" to true), pin = "1234"),
        )
    }

    @Test
    fun daySummaries() {
        val every = { names: String -> "Every $names" }
        assertEquals("Every day", ScheduleDays.summary(0x7F, Locale.US, "Every day", every))
        assertEquals(
            "Every Monday, Tuesday, Wednesday, Thursday, Friday, Saturday",
            ScheduleDays.summary(0x3F, Locale.US, "Every day", every),
        )
        assertEquals("Every Sunday, Monday", ScheduleDays.summary(0x41, Locale.US, "Every day", every))
        assertEquals("", ScheduleDays.summary(0, Locale.US, "Every day", every))
    }

    @Test
    fun dayLettersAndToggle() {
        assertEquals(
            listOf("S", "M", "T", "W", "T", "F", "S"),
            ScheduleDays.DISPLAY_ORDER.map { ScheduleDays.letter(it, Locale.US) },
        )
        assertEquals(0x3F, ScheduleDays.toggle(0x7F, 6))
        assertTrue(ScheduleDays.isSelected(0x40, 6))
    }
}
