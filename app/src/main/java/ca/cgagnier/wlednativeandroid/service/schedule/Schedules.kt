package ca.cgagnier.wlednativeandroid.service.schedule

import com.squareup.moshi.Moshi
import java.text.DateFormatSymbols
import java.util.Locale

enum class ScheduleTrigger { TIME, SUNRISE, SUNSET }

/**
 * One entry of WLED's "Time-controlled presets" (`timers.ins` in cfg.json). The same list is shown
 * under Config → Time & Macros on the device, so nothing is stored in the app.
 *
 * @param hour 0..23 for [ScheduleTrigger.TIME] (24 means every hour). Unused for sunrise/sunset.
 * @param minute 0..59 for [ScheduleTrigger.TIME], otherwise the offset in minutes from sunrise/sunset.
 * @param presetId Preset applied when the schedule fires. 0 means none.
 * @param days Bit mask, bit 0 = Monday .. bit 6 = Sunday (WLED's `dow`).
 */
data class Schedule(
    val trigger: ScheduleTrigger,
    val hour: Int,
    val minute: Int,
    val presetId: Int,
    val days: Int,
    val enabled: Boolean,
    val startMonth: Int = 1,
    val startDay: Int = 1,
    val endMonth: Int = LAST_MONTH,
    val endDay: Int = LAST_DAY,
) {
    companion object {
        const val ALL_DAYS = 0x7F
        const val LAST_MONTH = 12
        const val LAST_DAY = 31
    }
}

/** How many schedules a device can hold. */
data class ScheduleLimits(val maxTotal: Int, val maxTime: Int, val maxSun: Int, val maxOffsetMinutes: Int) {

    fun canAdd(schedules: List<Schedule>, trigger: ScheduleTrigger): Boolean {
        if (schedules.size >= maxTotal) return false
        val sameKind = schedules.count { it.trigger == trigger }
        return sameKind < if (trigger == ScheduleTrigger.TIME) maxTime else maxSun
    }

    companion object {
        /** WLED 0.14/0.15: 8 time slots, then one sunrise and one sunset slot. */
        val LEGACY = ScheduleLimits(maxTotal = 10, maxTime = 8, maxSun = 1, maxOffsetMinutes = 59)

        /** WLED 16.x: a free list of WLED_MAX_TIMERS entries (16 on ESP8266, 64 otherwise). */
        fun current(isEsp8266: Boolean): ScheduleLimits {
            val max = if (isEsp8266) MAX_TIMERS_ESP8266 else MAX_TIMERS
            return ScheduleLimits(maxTotal = max, maxTime = max, maxSun = max, maxOffsetMinutes = MAX_SUN_OFFSET)
        }

        private const val MAX_TIMERS_ESP8266 = 16
        private const val MAX_TIMERS = 64
        private const val MAX_SUN_OFFSET = 120
    }
}

/**
 * Reads and writes `timers.ins` in both firmware formats.
 *
 * Legacy firmware (before build [CURRENT_FORMAT_BUILD]) keeps 10 fixed slots and only overwrites the
 * slots present in the JSON, so a shorter list would leave deleted schedules behind. It is therefore
 * always written as exactly 10 entries. Sunrise and sunset both use hour 255 there, told apart by
 * position. Newer firmware clears the list on every write and uses 255 / 254.
 */
object ScheduleCodec {
    const val CURRENT_FORMAT_BUILD = 2_605_010
    private const val HOUR_SUNRISE = 255
    private const val HOUR_SUNSET = 254
    private const val LEGACY_TIME_SLOTS = 8

    fun isCurrentFormat(buildId: Int?): Boolean = (buildId ?: 0) >= CURRENT_FORMAT_BUILD

    fun decode(ins: List<*>, currentFormat: Boolean): List<Schedule> {
        var legacySunSeen = 0
        return ins.filterIsInstance<Map<*, *>>().mapNotNull { timer ->
            val hour = timer.int("hour")
            val trigger = when {
                hour == HOUR_SUNSET && currentFormat -> ScheduleTrigger.SUNSET
                hour != HOUR_SUNRISE -> ScheduleTrigger.TIME
                currentFormat || legacySunSeen++ == 0 -> ScheduleTrigger.SUNRISE
                else -> ScheduleTrigger.SUNSET
            }
            toSchedule(timer, trigger, hour)
        }
    }

    private fun toSchedule(timer: Map<*, *>, trigger: ScheduleTrigger, hour: Int): Schedule? {
        val preset = timer.int("macro")
        val minute = timer.int("min")
        val isEmptySlot = preset == 0 && (trigger != ScheduleTrigger.TIME || (hour == 0 && minute == 0))
        if (isEmptySlot) return null
        val start = timer["start"] as? Map<*, *>
        val end = timer["end"] as? Map<*, *>
        return Schedule(
            trigger = trigger,
            hour = if (trigger == ScheduleTrigger.TIME) hour else 0,
            minute = minute,
            presetId = preset,
            days = timer.int("dow", Schedule.ALL_DAYS) and Schedule.ALL_DAYS,
            enabled = timer.int("en") != 0,
            startMonth = month(start, 1),
            startDay = day(start, 1),
            endMonth = month(end, Schedule.LAST_MONTH),
            endDay = day(end, Schedule.LAST_DAY),
        )
    }

    private fun month(date: Map<*, *>?, default: Int) =
        date?.int("mon", default)?.takeIf { it in 1..Schedule.LAST_MONTH } ?: default

    private fun day(date: Map<*, *>?, default: Int) =
        date?.int("day", default)?.takeIf { it in 1..Schedule.LAST_DAY } ?: default

    /** Callers must respect [ScheduleLimits] first; legacy slots that do not fit are dropped. */
    fun encode(schedules: List<Schedule>, currentFormat: Boolean): List<Map<String, Any>> {
        if (currentFormat) return schedules.map { entry(it, withDates = true) }

        val time = schedules.filter { it.trigger == ScheduleTrigger.TIME }.take(LEGACY_TIME_SLOTS)
        val padding = List(LEGACY_TIME_SLOTS - time.size) { emptySlot(hour = 0, withDates = true) }
        val sunrise = schedules.firstOrNull { it.trigger == ScheduleTrigger.SUNRISE }
        val sunset = schedules.firstOrNull { it.trigger == ScheduleTrigger.SUNSET }
        return time.map { entry(it, withDates = true) } + padding +
            listOf(
                sunrise?.let { entry(it, withDates = false, legacy = true) }
                    ?: emptySlot(HOUR_SUNRISE, withDates = false),
                sunset?.let { entry(it, withDates = false, legacy = true) }
                    ?: emptySlot(HOUR_SUNRISE, withDates = false),
            )
    }

    private fun entry(schedule: Schedule, withDates: Boolean, legacy: Boolean = false): Map<String, Any> {
        val hour = when (schedule.trigger) {
            ScheduleTrigger.TIME -> schedule.hour
            ScheduleTrigger.SUNRISE -> HOUR_SUNRISE
            ScheduleTrigger.SUNSET -> if (legacy) HOUR_SUNRISE else HOUR_SUNSET
        }
        val map = mutableMapOf<String, Any>(
            "en" to if (schedule.enabled) 1 else 0,
            "hour" to hour,
            "min" to schedule.minute,
            "macro" to schedule.presetId,
            "dow" to schedule.days,
        )
        if (withDates) {
            map["start"] = mapOf("mon" to schedule.startMonth, "day" to schedule.startDay)
            map["end"] = mapOf("mon" to schedule.endMonth, "day" to schedule.endDay)
        }
        return map
    }

    private fun emptySlot(hour: Int, withDates: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "en" to 0,
            "hour" to hour,
            "min" to 0,
            "macro" to 0,
            "dow" to Schedule.ALL_DAYS,
        )
        if (withDates) {
            map["start"] = mapOf("mon" to 1, "day" to 1)
            map["end"] = mapOf("mon" to Schedule.LAST_MONTH, "day" to Schedule.LAST_DAY)
        }
        return map
    }

    private fun Map<*, *>.int(key: String, default: Int = 0): Int = (this[key] as? Number)?.toInt() ?: default
}

/** The two command-only presets used by "Turn device On/Off" schedules. */
object SchedulePresets {
    const val ON_NAME = "Schedule: On"
    const val OFF_NAME = "Schedule: Off"
    private const val MAX_PRESET_ID = 250
    private val moshi: Moshi = Moshi.Builder().build()

    /** Preset id to name, from `/presets.json`. Unnamed presets get "Preset N". */
    fun names(presetsJson: Map<*, *>): Map<Int, String> = presetsJson.entries.mapNotNull { (key, value) ->
        val id = key.toString().toIntOrNull() ?: return@mapNotNull null
        val preset = value as? Map<*, *> ?: return@mapNotNull null
        if (id !in 1..MAX_PRESET_ID || preset.isEmpty()) return@mapNotNull null
        id to ((preset["n"] as? String)?.takeIf { it.isNotBlank() } ?: "Preset $id")
    }.toMap()

    fun find(presets: Map<Int, String>, name: String): Int? = presets.entries.firstOrNull { it.value == name }?.key

    /** Highest unused preset slot, so the presets stay out of the way of the user's own. */
    fun freeSlot(presets: Map<Int, String>, taken: Set<Int> = emptySet()): Int? =
        (MAX_PRESET_ID downTo 1).firstOrNull { it !in presets && it !in taken }

    /** Body for `POST /json/state` that saves a preset containing only `{"on": on}`. */
    fun saveJson(id: Int, name: String, on: Boolean): String =
        moshi.adapter(Any::class.java).toJson(mapOf("psave" to id, "n" to name, "on" to on, "o" to true))
}

/** Body for `POST /json/cfg` that replaces the schedule list and optionally fixes time settings. */
object SchedulePayloads {
    private val moshi: Moshi = Moshi.Builder().build()

    fun configJson(timers: List<Map<String, Any>>, ntp: Map<String, Any>? = null, pin: String? = null): String {
        val payload = mutableMapOf<String, Any>("timers" to mapOf("ins" to timers))
        if (!ntp.isNullOrEmpty()) payload["if"] = mapOf("ntp" to ntp)
        if (!pin.isNullOrEmpty()) payload["pin"] = pin
        return moshi.adapter(Any::class.java).toJson(payload)
    }
}

/** Texts shown under the day circles, e.g. "Every day" or "Every Monday, Tuesday". */
object ScheduleDays {
    private const val SUNDAY_BIT = 6
    private const val DAYS_PER_WEEK = 7

    /** WLED day bits in the order shown: Sunday first, like the reference design. */
    val DISPLAY_ORDER = listOf(SUNDAY_BIT) + (0 until SUNDAY_BIT)

    fun isSelected(days: Int, bit: Int): Boolean = (days and (1 shl bit)) != 0

    fun toggle(days: Int, bit: Int): Int = days xor (1 shl bit)

    /** Full localized day name for WLED day [bit] (0 = Monday). */
    fun name(bit: Int, locale: Locale): String = DateFormatSymbols.getInstance(locale).weekdays[calendarDay(bit)]

    /** Single-letter label for the day circles. */
    fun letter(bit: Int, locale: Locale): String =
        DateFormatSymbols.getInstance(locale).shortWeekdays[calendarDay(bit)].take(1).uppercase(locale)

    /**
     * @param everyDay Text for all seven days.
     * @param everyList Formats the comma-separated day names, e.g. "Every %s".
     * @return Empty when no day is selected.
     */
    fun summary(days: Int, locale: Locale, everyDay: String, everyList: (String) -> String): String = when {
        (days and Schedule.ALL_DAYS) == Schedule.ALL_DAYS -> everyDay
        (days and Schedule.ALL_DAYS) == 0 -> ""
        else -> everyList(names(days, locale))
    }

    /** Selected day names, Sunday first, comma separated. */
    fun names(days: Int, locale: Locale): String =
        DISPLAY_ORDER.filter { isSelected(days, it) }.joinToString(", ") { name(it, locale) }

    // java.util.Calendar: SUNDAY = 1 .. SATURDAY = 7; WLED: Monday = 0 .. Sunday = 6.
    private fun calendarDay(bit: Int): Int = (bit + 1) % DAYS_PER_WEEK + 1
}
