package ca.cgagnier.wlednativeandroid.service.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedFirmwareTest {
    @Test
    fun `no update is offered until a newer version is approved`() {
        assertNull(ApprovedFirmware.updateFor("0.15.3"))
        assertFalse(ApprovedFirmware.isApproved("v16.0.1"))
        assertTrue(ApprovedFirmware.isApproved("v0.15.3"))
    }

    @Test
    fun `an approved newer version is offered, the newest one`() {
        val approved = listOf("v0.15.3", "v16.0.1", "v16.0.2")
        assertEquals("v16.0.2", ApprovedFirmware.updateFor("0.15.3", approved))
        assertEquals("v16.0.2", ApprovedFirmware.updateFor("16.0.1", approved))
        assertNull(ApprovedFirmware.updateFor("16.0.2", approved))
    }

    @Test
    fun `older lanterns are brought up to the approved version`() {
        assertEquals("v0.15.3", ApprovedFirmware.updateFor("0.14.4"))
        assertEquals("v0.15.3", ApprovedFirmware.updateFor("0.15.0"))
    }

    @Test
    fun `never offers a downgrade, a pre-release or an unreadable version`() {
        assertNull(ApprovedFirmware.updateFor("16.0.1"))
        assertNull(ApprovedFirmware.updateFor("0.15.3", listOf("v16.0.0-b2")))
        assertNull(ApprovedFirmware.updateFor("0.16.0-b1"))
        assertNull(ApprovedFirmware.updateFor(null))
        assertNull(ApprovedFirmware.updateFor(""))
    }

    @Test
    fun `versions compare by number, with or without v and trailing zeros`() {
        assertTrue(ApprovedFirmware.isApproved("0.15.3"))
        assertTrue(ApprovedFirmware.isApproved("v16.0", listOf("v16.0.0")))
        assertEquals("v16.0.10", ApprovedFirmware.updateFor("16.0.9", listOf("v16.0.10", "v16.0.2")))
    }
}
