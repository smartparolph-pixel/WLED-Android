package ca.cgagnier.wlednativeandroid.service.timezone

import com.squareup.moshi.Moshi
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** One row of the timezone picker. */
data class TimezoneOption(
    val id: String,
    val city: String,
    val region: String,
    val displayName: String,
    val offsetSeconds: Int,
) {
    val offsetLabel: String get() = TimezonePayloads.formatOffset(offsetSeconds)
}

/**
 * Builds the picker list from the phone's own copy of the IANA tz database (eggert/tz), so the app
 * never ships or updates timezone data itself.
 */
object TimezoneCatalog {
    private const val MILLIS_PER_SECOND = 1_000

    private val REGIONS = setOf(
        "Africa",
        "America",
        "Antarctica",
        "Asia",
        "Atlantic",
        "Australia",
        "Europe",
        "Indian",
        "Pacific",
    )

    /**
     * @param ids Zone IDs to offer. Non-geographic IDs (Etc/..., SystemV/..., US/...) are dropped.
     * @param cityName Localized city name for an ID, or null to derive it from the ID.
     */
    fun options(
        ids: Collection<String>,
        nowMillis: Long,
        locale: Locale,
        cityName: (String) -> String? = { null },
    ): List<TimezoneOption> = ids
        .filter { it.substringBefore('/') in REGIONS && it.contains('/') }
        .map { id ->
            val zone = TimeZone.getTimeZone(id)
            TimezoneOption(
                id = id,
                city = cityName(id) ?: id.substringAfterLast('/').replace('_', ' '),
                region = id.substringBefore('/'),
                displayName = zone.getDisplayName(zone.inDaylightTime(Date(nowMillis)), TimeZone.LONG, locale),
                offsetSeconds = zone.getOffset(nowMillis) / MILLIS_PER_SECOND,
            )
        }
        .sortedWith(compareBy({ it.offsetSeconds }, { it.city }))

    /** Case-insensitive match on city, zone ID, zone name or "UTC+hh:mm". */
    fun filter(options: List<TimezoneOption>, query: String): List<TimezoneOption> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return options
        return options.filter { option ->
            listOf(option.city, option.id, option.displayName, option.offsetLabel)
                .any { it.lowercase(Locale.ROOT).contains(needle) }
        }
    }

    /** What to send to the device for zone [id], comparing rules over the year starting at [nowMillis]. */
    fun settingFor(id: String, nowMillis: Long): WledTimezoneSetting {
        val zone = TimeZone.getTimeZone(id)
        return WledTimezones.match(
            zoneOffsetSecondsAt = { zone.getOffset(it * MILLIS_PER_SECOND) / MILLIS_PER_SECOND },
            fromEpochSeconds = nowMillis / MILLIS_PER_SECOND,
        )
    }
}

/** JSON bodies for `POST /json/cfg`. */
object TimezonePayloads {
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
    private val moshi: Moshi = Moshi.Builder().build()

    /** `if.ntp` object. Turns NTP on, since WLED ships with it off and the zone is useless without it. */
    fun ntpConfig(setting: WledTimezoneSetting): Map<String, Any> = mapOf(
        "en" to true,
        "tz" to setting.index,
        "offset" to setting.offsetSeconds,
    )

    /**
     * @param pin Settings PIN, if the device has one. WLED answers 401 to config changes without it.
     */
    fun configJson(setting: WledTimezoneSetting, pin: String? = null): String {
        val payload = mutableMapOf<String, Any>("if" to mapOf("ntp" to ntpConfig(setting)))
        if (!pin.isNullOrEmpty()) payload["pin"] = pin
        return moshi.adapter(Any::class.java).toJson(payload)
    }

    /** "UTC+05:45", "UTC−03:00", "UTC+00:00". */
    fun formatOffset(seconds: Int): String {
        val sign = if (seconds < 0) "−" else "+"
        val minutes = abs(seconds) / SECONDS_PER_MINUTE
        return "UTC%s%02d:%02d".format(Locale.ROOT, sign, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }
}
