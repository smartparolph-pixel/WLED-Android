package ca.cgagnier.wlednativeandroid.service.status

/**
 * What the lantern status card shows: what is playing and how long the lantern has been on. Built from
 * WLED's own state (`ps`, `pl`), info (`uptime`) and presets.json, so it works on any WLED 0.15.3 lantern.
 */
object LanternStatus {
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val HOURS_PER_DAY = 24L

    /** The preset / playlist names in presets.json (`n`), by id. Unnamed entries are left out. */
    fun presetNames(presets: Map<*, *>): Map<Int, String> = presets.mapNotNull { (id, entry) ->
        val number = id.toString().toIntOrNull() ?: return@mapNotNull null
        val name = ((entry as? Map<*, *>)?.get("n") as? String)?.trim()
        if (number > 0 && !name.isNullOrEmpty()) number to name else null
    }.toMap()

    /**
     * The id to name as "now playing": the running playlist ([playlistId], `pl`) over the preset it is
     * showing ([presetId], `ps`). Null when nothing saved is playing (WLED reports -1 or 0).
     */
    fun playingId(presetId: Int?, playlistId: Int?): Int? = playlistId?.takeIf { it > 0 } ?: presetId?.takeIf { it > 0 }

    /** How long the lantern has been on, from WLED's uptime in seconds, split for display. */
    fun onFor(uptimeSeconds: Long): Duration {
        val minutes = (uptimeSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE)
        val hours = minutes / MINUTES_PER_HOUR
        return Duration(
            days = hours / HOURS_PER_DAY,
            hours = hours % HOURS_PER_DAY,
            minutes = minutes % MINUTES_PER_HOUR,
        )
    }

    data class Duration(val days: Long, val hours: Long, val minutes: Long)
}
