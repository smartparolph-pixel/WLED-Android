package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import java.io.IOException

/**
 * Rebuilds a damaged presets.json from the entries that are still intact.
 *
 * WLED writes presets.json in place, one entry at a time, and two writes at once (e.g. a preset saved
 * from the JSON API while a playlist is still being written) can leave stray or cut-off text in it. The
 * firmware still finds most presets by searching for `"id":`, but the file is no longer valid JSON, so
 * WLED's web page ("issue loading your presets") and the app cannot read it.
 *
 * This scans the file for `"id":{...}` entries, keeps every one that parses, and drops the rest. When an
 * id appears twice, the first one is kept, as that is the one the firmware loads.
 *
 * It also fixes numbers written as decimals (`"fx":80.0`), as an earlier app version did when repairing:
 * the firmware ignores a decimal effect, speed or palette, so presets would play without changing the
 * lights. The repaired file always has whole numbers.
 */
object PresetsFileRepair {
    private val moshi = Moshi.Builder().build()
    private val entryStart = Regex("\"(\\d{1,3})\"\\s*:\\s*\\{")

    /** A JSON number value written with a zero fraction, e.g. `:80.0` or `[1.0,` (WLED never writes these). */
    private val decimalWhole = Regex("[:\\[,]\\s*-?\\d+\\.0+\\s*(?=[,\\]}])")

    /**
     * @param presets The entries kept, by id, in file order.
     * @param dropped Damaged or repeated parts left out.
     * @param problem Where and why the file could not be read as is, or null if it reads fine.
     */
    data class Result(val presets: Map<String, Any?>, val dropped: Int, val problem: String?) {
        /** The repaired file, always valid JSON. */
        fun toJson(): String = moshi.adapter(Any::class.java).toJson(wholeNumbers(presets))

        /** Presets and playlists kept, not counting the empty entry "0". */
        val kept get() = presets.keys.count { it != "0" }
    }

    /** Why the file cannot be used as is, or null if it parses and the firmware can read its numbers. */
    fun problem(text: String): String? = try {
        moshi.adapter(Map::class.java).fromJson(text)
        decimalWhole.find(text)?.let { "Numbers saved as decimals (e.g. ${it.value.trimStart(':', '[', ',').trim()})" }
    } catch (e: JsonDataException) {
        e.message ?: e.toString()
    } catch (e: IOException) {
        e.message ?: e.toString()
    }

    fun repair(text: String): Result {
        val presets = linkedMapOf<String, Any?>("0" to emptyMap<String, Any>())
        var dropped = 0
        var position = 0
        while (true) {
            val match = entryStart.find(text, position) ?: break
            val valueStart = match.range.last
            val valueEnd = objectEnd(text, valueStart)
            val value = valueEnd?.let { parseObject(text.substring(valueStart, it + 1)) }
            val id = match.groupValues[1]
            when {
                value == null -> dropped++
                id in presets && id != "0" -> dropped++
                id.toInt() in 1..PresetPackPlanner.MAX_PRESET_ID || id == "0" -> presets[id] = value
                else -> dropped++
            }
            // An entry that parsed is skipped as a whole (nested ids such as "ps" lists hold no `"n":{`).
            position = valueEnd?.takeIf { value != null }?.plus(1) ?: (valueStart + 1)
        }
        return Result(presets, dropped, problem(text))
    }

    /** Index of the `}` closing the object that opens at [start], or null if the text ends first. */
    private fun objectEnd(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return i
            }
        }
        return null
    }

    /** The entry, or null if it is not valid JSON (any parse error means "damaged" here). */
    private fun parseObject(json: String): Map<*, *>? =
        runCatching { moshi.adapter(Map::class.java).fromJson(json) }.getOrNull()

    /** Copies JSON as parsed by Moshi (every number a Double), turning whole values back into integers. */
    private fun wholeNumbers(value: Any?): Any? = when (value) {
        is Double -> if (value % 1.0 == 0.0 && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
            value.toLong()
        } else {
            value
        }

        is Map<*, *> -> value.entries.associate { (key, item) -> key.toString() to wholeNumbers(item) }

        is List<*> -> value.map { wholeNumbers(it) }

        else -> value
    }
}
