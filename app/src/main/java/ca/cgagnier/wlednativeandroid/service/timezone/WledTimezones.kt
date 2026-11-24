package ca.cgagnier.wlednativeandroid.service.timezone

import kotlin.math.abs

/**
 * One daylight-saving transition rule, in the format of the Arduino Timezone library that WLED
 * uses (`TimeChangeRule` in wled00/ntp.cpp).
 *
 * @param week [WledTimezones.LAST] or 1..4 for First..Fourth.
 * @param dayOfWeek 1 = Sunday .. 7 = Saturday.
 * @param month 1 = January .. 12 = December.
 * @param hour Local hour at which the change happens.
 * @param offsetMinutes UTC offset in effect after the change.
 */
data class WledTimeChangeRule(val week: Int, val dayOfWeek: Int, val month: Int, val hour: Int, val offsetMinutes: Int)

/** One entry of WLED's built-in timezone dropdown. [index] is the value stored as `if.ntp.tz`. */
data class WledTimezone(val index: Int, val label: String, val dst: WledTimeChangeRule, val std: WledTimeChangeRule)

/**
 * What to send to the device for a given IANA zone.
 *
 * @param index Value for `if.ntp.tz`.
 * @param offsetSeconds Value for `if.ntp.offset`, added on top of the zone's own offset.
 * @param exact False when no built-in zone follows the same daylight-saving rules, in which case
 * the device shows the right time now but will not change its clock automatically.
 */
data class WledTimezoneSetting(val index: Int, val offsetSeconds: Int, val exact: Boolean)

/**
 * WLED firmware only knows the 25 zones below. This maps any IANA zone (from the phone's own copy
 * of the tz database) onto the closest built-in zone plus a fixed UTC offset.
 */
object WledTimezones {
    const val LAST = 0
    private const val SUNDAY = 1
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
    private const val DAYS_PER_WEEK = 7
    private const val MONTHS_PER_YEAR = 12
    private const val SAMPLE_DAYS = 366
    private const val SAMPLE_HOUR_UTC = 12L

    /** 1970-01-01 was a Thursday, i.e. weekday index 4 counting from Sunday = 0. */
    private const val EPOCH_WEEKDAY = 4L

    /** `utcOffsetSecs` is limited to this range by the WLED settings page. */
    const val MAX_OFFSET_SECONDS = 65_500

    private fun rule(week: Int, month: Int, hour: Int, offsetMinutes: Int) =
        WledTimeChangeRule(week = week, dayOfWeek = SUNDAY, month = month, hour = hour, offsetMinutes = offsetMinutes)

    private fun fixed(offsetMinutes: Int) = rule(week = LAST, month = 3, hour = 1, offsetMinutes = offsetMinutes)

    /** Copied from TZ_TABLE in wled00/ntp.cpp and the labels in settings_time.htm. Order matters. */
    val ALL: List<WledTimezone> = listOf(
        WledTimezone(index = 0, label = "GMT(UTC)", dst = fixed(offsetMinutes = 0), std = fixed(offsetMinutes = 0)),
        WledTimezone(
            index = 1,
            label = "GMT/BST",
            dst = rule(week = LAST, month = 3, hour = 1, offsetMinutes = 60),
            std = rule(week = LAST, month = 10, hour = 2, offsetMinutes = 0),
        ),
        WledTimezone(
            index = 2,
            label = "CET/CEST",
            dst = rule(week = LAST, month = 3, hour = 2, offsetMinutes = 120),
            std = rule(week = LAST, month = 10, hour = 3, offsetMinutes = 60),
        ),
        WledTimezone(
            index = 3,
            label = "EET/EEST",
            dst = rule(week = LAST, month = 3, hour = 3, offsetMinutes = 180),
            std = rule(week = LAST, month = 10, hour = 4, offsetMinutes = 120),
        ),
        WledTimezone(
            index = 4,
            label = "US-EST/EDT",
            dst = rule(week = 2, month = 3, hour = 2, offsetMinutes = -240),
            std = rule(week = 1, month = 11, hour = 2, offsetMinutes = -300),
        ),
        WledTimezone(
            index = 5,
            label = "US-CST/CDT",
            dst = rule(week = 2, month = 3, hour = 2, offsetMinutes = -300),
            std = rule(week = 1, month = 11, hour = 2, offsetMinutes = -360),
        ),
        WledTimezone(
            index = 6,
            label = "US-MST/MDT",
            dst = rule(week = 2, month = 3, hour = 2, offsetMinutes = -360),
            std = rule(week = 1, month = 11, hour = 2, offsetMinutes = -420),
        ),
        WledTimezone(index = 7, label = "US-AZ", dst = fixed(offsetMinutes = -420), std = fixed(offsetMinutes = -420)),
        WledTimezone(
            index = 8,
            label = "US-PST/PDT",
            dst = rule(week = 2, month = 3, hour = 2, offsetMinutes = -420),
            std = rule(week = 1, month = 11, hour = 2, offsetMinutes = -480),
        ),
        WledTimezone(
            index = 9,
            label = "CST (AWST, PHST)",
            dst = fixed(offsetMinutes = 480),
            std = fixed(offsetMinutes = 480),
        ),
        WledTimezone(
            index = 10,
            label = "JST (KST)",
            dst = fixed(offsetMinutes = 540),
            std = fixed(offsetMinutes = 540),
        ),
        WledTimezone(
            index = 11,
            label = "AEST/AEDT",
            dst = rule(week = 1, month = 10, hour = 2, offsetMinutes = 660),
            std = rule(week = 1, month = 4, hour = 3, offsetMinutes = 600),
        ),
        WledTimezone(
            index = 12,
            label = "NZST/NZDT",
            dst = rule(week = LAST, month = 9, hour = 2, offsetMinutes = 780),
            std = rule(week = 1, month = 4, hour = 3, offsetMinutes = 720),
        ),
        WledTimezone(
            index = 13,
            label = "North Korea",
            dst = fixed(offsetMinutes = 510),
            std = fixed(offsetMinutes = 510),
        ),
        WledTimezone(
            index = 14,
            label = "IST (India)",
            dst = fixed(offsetMinutes = 330),
            std = fixed(offsetMinutes = 330),
        ),
        WledTimezone(
            index = 15,
            label = "CA-Saskatchewan",
            dst = fixed(offsetMinutes = -360),
            std = fixed(offsetMinutes = -360),
        ),
        WledTimezone(index = 16, label = "ACST", dst = fixed(offsetMinutes = 570), std = fixed(offsetMinutes = 570)),
        WledTimezone(
            index = 17,
            label = "ACST/ACDT",
            dst = rule(week = 1, month = 10, hour = 2, offsetMinutes = 630),
            std = rule(week = 1, month = 4, hour = 3, offsetMinutes = 570),
        ),
        WledTimezone(
            index = 18,
            label = "HST (Hawaii)",
            dst = fixed(offsetMinutes = -600),
            std = fixed(offsetMinutes = -600),
        ),
        WledTimezone(
            index = 19,
            label = "NOVT (Novosibirsk)",
            dst = fixed(offsetMinutes = 420),
            std = fixed(offsetMinutes = 420),
        ),
        WledTimezone(
            index = 20,
            label = "AKST/AKDT (Anchorage)",
            dst = rule(week = 2, month = 3, hour = 2, offsetMinutes = -480),
            std = rule(week = 1, month = 11, hour = 2, offsetMinutes = -540),
        ),
        WledTimezone(
            index = 21,
            label = "MX-CST",
            dst = fixed(offsetMinutes = -360),
            std = fixed(offsetMinutes = -360),
        ),
        WledTimezone(
            index = 22,
            label = "PKT (Pakistan)",
            dst = fixed(offsetMinutes = 300),
            std = fixed(offsetMinutes = 300),
        ),
        WledTimezone(
            index = 23,
            label = "BRT (Brasília)",
            dst = fixed(offsetMinutes = -180),
            std = fixed(offsetMinutes = -180),
        ),
        WledTimezone(
            index = 24,
            label = "AWST (Perth)",
            dst = fixed(offsetMinutes = 480),
            std = fixed(offsetMinutes = 480),
        ),
    )

    /**
     * Picks the built-in zone whose daylight-saving behaviour matches [zoneOffsetSecondsAt] over the
     * coming year, preferring an exact match with no extra offset.
     *
     * @param zoneOffsetSecondsAt UTC offset of the target zone at a given UTC instant (epoch seconds).
     * @param fromEpochSeconds Start of the year that is compared.
     */
    fun match(zoneOffsetSecondsAt: (Long) -> Int, fromEpochSeconds: Long): WledTimezoneSetting {
        val firstSample = Math.floorDiv(fromEpochSeconds, SECONDS_PER_DAY) * SECONDS_PER_DAY +
            SAMPLE_HOUR_UTC * SECONDS_PER_HOUR
        val samples = (0 until SAMPLE_DAYS).map { firstSample + it * SECONDS_PER_DAY }
        val zoneOffsets = samples.map(zoneOffsetSecondsAt)

        val best = ALL.mapNotNull { candidate ->
            val diffs = samples.indices.map { zoneOffsets[it] - offsetSecondsAt(candidate, samples[it]) }.toSet()
            val diff = diffs.singleOrNull()
            if (diff != null && abs(diff) <= MAX_OFFSET_SECONDS) candidate to diff else null
        }.minByOrNull { (candidate, diff) -> abs(diff).toLong() * ALL.size + candidate.index }

        return if (best != null) {
            WledTimezoneSetting(index = best.first.index, offsetSeconds = best.second, exact = true)
        } else {
            WledTimezoneSetting(index = 0, offsetSeconds = zoneOffsets.first(), exact = false)
        }
    }

    /** UTC offset WLED applies for [zone] at [epochSeconds], mirroring Timezone::toLocal. */
    fun offsetSecondsAt(zone: WledTimezone, epochSeconds: Long): Int {
        val inDst = isDst(zone, epochSeconds)
        return (if (inDst) zone.dst.offsetMinutes else zone.std.offsetMinutes) * SECONDS_PER_MINUTE
    }

    private fun isDst(zone: WledTimezone, epochSeconds: Long): Boolean {
        if (zone.dst.offsetMinutes == zone.std.offsetMinutes) return false
        val year = yearOf(epochSeconds)
        val dstStartUtc = localTransition(zone.dst, year) - zone.std.offsetMinutes * SECONDS_PER_MINUTE
        val stdStartUtc = localTransition(zone.std, year) - zone.dst.offsetMinutes * SECONDS_PER_MINUTE
        val inSummerWindow = epochSeconds in dstStartUtc until stdStartUtc
        val inWinterWindow = epochSeconds in stdStartUtc until dstStartUtc
        // Northern hemisphere: DST between start and end. Southern: DST outside the winter window.
        return if (stdStartUtc > dstStartUtc) inSummerWindow else !inWinterWindow
    }

    /** Mirrors Timezone::toTime_t: local time of [rule] in [year], as if it were UTC. */
    private fun localTransition(rule: WledTimeChangeRule, year: Int): Long {
        var month = rule.month
        var ruleYear = year
        var week = rule.week
        if (week == LAST) {
            month++
            if (month > MONTHS_PER_YEAR) {
                month = 1
                ruleYear++
            }
            week = 1
        }
        val firstOfMonth = daysFromCivil(ruleYear, month, 1)
        val dayShift = Math.floorMod(rule.dayOfWeek - weekday(firstOfMonth), DAYS_PER_WEEK) +
            (week - 1) * DAYS_PER_WEEK
        var days = firstOfMonth + dayShift
        if (rule.week == LAST) days -= DAYS_PER_WEEK
        return days * SECONDS_PER_DAY + rule.hour * SECONDS_PER_HOUR
    }

    /** 1 = Sunday .. 7 = Saturday. */
    private fun weekday(epochDay: Long): Int =
        Math.floorMod(epochDay + EPOCH_WEEKDAY, DAYS_PER_WEEK.toLong()).toInt() + 1

    private fun yearOf(epochSeconds: Long): Int = civilYear(Math.floorDiv(epochSeconds, SECONDS_PER_DAY))

    // Howard Hinnant's civil-calendar algorithms (proleptic Gregorian), avoiding java.time on API 24.
    @Suppress("MagicNumber")
    internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = Math.floorDiv(y, 400L)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    @Suppress("MagicNumber")
    private fun civilYear(epochDay: Long): Int {
        val z = epochDay + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val month = if (mp < 10) mp + 3 else mp - 9
        return (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    }
}
