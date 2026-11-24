package ca.cgagnier.wlednativeandroid.service.update

/**
 * Firmware versions Sinag may offer to install on Parol lanterns. Only versions tested with the app go
 * here: a version that isn't listed is never offered, however new it is (WLED 16 changes the ESP32 platform
 * and bootloader, colours and effects, and hasn't been tested with Sinag's playlists, power limit, schedules
 * and Parol WiFi).
 *
 * To approve a version after testing it on a spare lantern, add its release tag (e.g. "v16.0.1").
 */
object ApprovedFirmware {
    val VERSIONS: List<String> = listOf("v0.15.3")

    /**
     * The newest approved version that is newer than [current] (the lantern's own version, e.g. "0.15.3"),
     * or null when there is none. Pre-releases and versions that can't be compared are never offered.
     */
    fun updateFor(current: String?, approved: List<String> = VERSIONS): String? {
        val installed = current?.let(::parse) ?: return null
        return approved
            .mapNotNull { tag -> parse(tag)?.let { tag to it } }
            .filter { (_, version) -> compare(version, installed) > 0 }
            .maxWithOrNull { a, b -> compare(a.second, b.second) }
            ?.first
    }

    fun isApproved(tag: String, approved: List<String> = VERSIONS): Boolean {
        val version = parse(tag) ?: return false
        return approved.any { parse(it) == version }
    }

    /** "v0.15.3" or "0.15.3" → [0, 15, 3]; null for anything else, including "-b1" pre-releases. */
    private fun parse(version: String): List<Int>? {
        val parts = version.trim().removePrefix("v").removePrefix("V").split(".")
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return numbers.takeIf { it.isNotEmpty() }?.dropLastWhile { it == 0 }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }
}
