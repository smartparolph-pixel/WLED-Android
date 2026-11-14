package ca.cgagnier.wlednativeandroid.service.timezone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.TimeZone

class WledTimezonesTest {

    // 2026-01-01T00:00:00Z
    private val from = 1_767_225_600L

    private fun matchFor(id: String): WledTimezoneSetting {
        val zone = TimeZone.getTimeZone(id)
        return WledTimezones.match({ zone.getOffset(it * 1000) / 1000 }, from)
    }

    private fun assertExact(id: String, index: Int, offsetSeconds: Int = 0) {
        assertEquals(id, WledTimezoneSetting(index, offsetSeconds, exact = true), matchFor(id))
    }

    @Test
    fun tableMatchesFirmwareOrder() {
        assertEquals(25, WledTimezones.ALL.size)
        WledTimezones.ALL.forEachIndexed { i, zone -> assertEquals(i, zone.index) }
    }

    @Test
    fun civilDays() {
        assertEquals(0L, WledTimezones.daysFromCivil(1970, 1, 1))
        assertEquals(11_017L, WledTimezones.daysFromCivil(2000, 3, 1))
        assertEquals(20_454L, WledTimezones.daysFromCivil(2026, 1, 1))
    }

    @Test
    fun centralEuropeSwitchesAtTheRightInstant() {
        val cet = WledTimezones.ALL[2]
        // Last Sunday of March 2026 is the 29th; the switch happens at 01:00 UTC.
        val switchToSummer = 1_774_746_000L
        assertEquals(3_600, WledTimezones.offsetSecondsAt(cet, switchToSummer - 1))
        assertEquals(7_200, WledTimezones.offsetSecondsAt(cet, switchToSummer))
        // Last Sunday of October 2026 is the 25th, again at 01:00 UTC.
        val switchToWinter = 1_792_890_000L
        assertEquals(7_200, WledTimezones.offsetSecondsAt(cet, switchToWinter - 1))
        assertEquals(3_600, WledTimezones.offsetSecondsAt(cet, switchToWinter))
    }

    @Test
    fun zonesWithMatchingDaylightSavingRules() {
        assertExact("Europe/London", 1)
        assertExact("Europe/Lisbon", 1)
        assertExact("Europe/Berlin", 2)
        assertExact("Europe/Athens", 3)
        assertExact("America/New_York", 4)
        assertExact("America/Toronto", 4)
        assertExact("America/Chicago", 5)
        assertExact("America/Denver", 6)
        assertExact("America/Phoenix", 7)
        assertExact("America/Los_Angeles", 8)
        assertExact("Australia/Sydney", 11)
        assertExact("Pacific/Auckland", 12)
        assertExact("Australia/Adelaide", 17)
        assertExact("America/Anchorage", 20)
    }

    @Test
    fun fixedOffsetZonesPreferTheirNamedEntry() {
        assertExact("Asia/Manila", 9)
        assertExact("Asia/Tokyo", 10)
        assertExact("Asia/Kolkata", 14)
        assertExact("Pacific/Honolulu", 18)
        assertExact("Asia/Karachi", 22)
    }

    @Test
    fun fixedOffsetZoneWithoutNamedEntryUsesNearestPlusOffset() {
        // Nepal is UTC+05:45: India (+05:30) plus 15 minutes.
        assertExact("Asia/Kathmandu", 14, 900)
        assertExact("Asia/Dubai", 22, -3_600)
    }

    @Test
    fun unsupportedDaylightSavingRulesAreFlagged() {
        val chile = matchFor("America/Santiago")
        assertFalse(chile.exact)
        assertEquals(0, chile.index)
        assertEquals(
            TimeZone.getTimeZone("America/Santiago").getOffset(from * 1000 + 43_200_000) / 1000,
            chile.offsetSeconds,
        )
    }
}
