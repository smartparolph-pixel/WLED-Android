package ca.cgagnier.wlednativeandroid.service.timezone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TimezoneCatalogTest {

    // 2026-01-15T12:00:00Z, northern winter.
    private val now = 1_768_478_400_000L

    private val options = TimezoneCatalog.options(
        ids = listOf(
            "Asia/Manila",
            "America/New_York",
            "Etc/GMT+5",
            "Europe/Berlin",
            "UTC",
            "America/Argentina/Buenos_Aires",
        ),
        nowMillis = now,
        locale = Locale.US,
    )

    @Test
    fun dropsNonGeographicIdsAndSortsByOffset() {
        assertEquals(
            listOf("America/New_York", "America/Argentina/Buenos_Aires", "Europe/Berlin", "Asia/Manila"),
            options.map { it.id },
        )
    }

    @Test
    fun derivesCityAndRegionFromId() {
        val buenosAires = options.first { it.id == "America/Argentina/Buenos_Aires" }
        assertEquals("Buenos Aires", buenosAires.city)
        assertEquals("America", buenosAires.region)
        assertEquals(-10_800, buenosAires.offsetSeconds)
    }

    @Test
    fun filterMatchesCityIdNameAndOffset() {
        assertEquals(listOf("Asia/Manila"), TimezoneCatalog.filter(options, "manila").map { it.id })
        assertEquals(listOf("Europe/Berlin"), TimezoneCatalog.filter(options, "europe/").map { it.id })
        assertEquals(listOf("Asia/Manila"), TimezoneCatalog.filter(options, "UTC+08").map { it.id })
        assertTrue(TimezoneCatalog.filter(options, "  ").size == options.size)
    }

    @Test
    fun formatsOffsets() {
        assertEquals("UTC+05:45", TimezonePayloads.formatOffset(20_700))
        assertEquals("UTC−03:30", TimezonePayloads.formatOffset(-12_600))
        assertEquals("UTC+00:00", TimezonePayloads.formatOffset(0))
    }

    @Test
    fun configJsonEnablesNtpAndOnlyTouchesTimezone() {
        val setting = WledTimezoneSetting(index = 14, offsetSeconds = 900, exact = true)
        assertEquals(
            """{"if":{"ntp":{"en":true,"tz":14,"offset":900}}}""",
            TimezonePayloads.configJson(setting),
        )
        assertEquals(
            """{"if":{"ntp":{"en":true,"tz":14,"offset":900}},"pin":"1234"}""",
            TimezonePayloads.configJson(setting, "1234"),
        )
    }

    @Test
    fun settingForUsesPhoneTzData() {
        assertEquals(WledTimezoneSetting(9, 0, exact = true), TimezoneCatalog.settingFor("Asia/Manila", now))
    }
}
