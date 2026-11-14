package ca.cgagnier.wlednativeandroid.service.applock

import ca.cgagnier.wlednativeandroid.repository.AppLockTimeout

/** When App lock asks to unlock again. Pure, so it can be unit-tested. */
object AppLockPolicy {
    private const val TWO_MINUTES_MS = 2 * 60 * 1000L
    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L

    fun timeoutMillis(timeout: AppLockTimeout): Long = when (timeout) {
        AppLockTimeout.IMMEDIATELY -> 0L

        AppLockTimeout.FIVE_MINUTES -> FIVE_MINUTES_MS

        // TWO_MINUTES, and any value written by a newer version of the app.
        else -> TWO_MINUTES_MS
    }

    /**
     * @param backgroundedAt When the app went to the background (a monotonic clock), or null if it has not.
     * @param now The same clock now.
     */
    fun shouldLockOnReturn(enabled: Boolean, backgroundedAt: Long?, now: Long, timeout: AppLockTimeout): Boolean =
        enabled && backgroundedAt != null && now - backgroundedAt >= timeoutMillis(timeout)

    /** Settings unlocked at [unlockedAt] stay unlocked until the chosen time has passed ("Immediately": never). */
    fun stillUnlocked(unlockedAt: Long, now: Long, timeout: AppLockTimeout): Boolean =
        timeout != AppLockTimeout.IMMEDIATELY && now - unlockedAt < timeoutMillis(timeout)
}
