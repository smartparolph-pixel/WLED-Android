package ca.cgagnier.wlednativeandroid.service.applock

import ca.cgagnier.wlednativeandroid.repository.AppLockTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    private val minute = 60_000L

    @Test
    fun defaultRelocksAfterTwoMinutesInTheBackground() {
        val timeout = AppLockTimeout.TWO_MINUTES
        assertFalse(AppLockPolicy.shouldLockOnReturn(true, 0, 2 * minute - 1, timeout))
        assertTrue(AppLockPolicy.shouldLockOnReturn(true, 0, 2 * minute, timeout))
    }

    @Test
    fun immediatelyAndFiveMinutes() {
        assertTrue(AppLockPolicy.shouldLockOnReturn(true, 1_000, 1_000, AppLockTimeout.IMMEDIATELY))
        assertFalse(AppLockPolicy.shouldLockOnReturn(true, 0, 5 * minute - 1, AppLockTimeout.FIVE_MINUTES))
        assertTrue(AppLockPolicy.shouldLockOnReturn(true, 0, 5 * minute, AppLockTimeout.FIVE_MINUTES))
    }

    @Test
    fun neverLocksWhenOffOrNotBackgrounded() {
        assertFalse(AppLockPolicy.shouldLockOnReturn(false, 0, 10 * minute, AppLockTimeout.IMMEDIATELY))
        assertFalse(AppLockPolicy.shouldLockOnReturn(true, null, 10 * minute, AppLockTimeout.IMMEDIATELY))
    }
}
