package ca.cgagnier.wlednativeandroid.service.direct

import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectConnectionTest {
    private val parol = Device(macAddress = "a0b1c2d3e4f5", address = "192.168.1.60")
    private val other = Device(macAddress = "112233445566", address = "192.168.1.61")

    @Test
    fun `a WiFi whose router or DNS is the lantern is a lantern WiFi`() {
        assertTrue(DirectConnection.isLanternNetwork(listOf("4.3.2.1")))
        assertTrue(DirectConnection.isLanternNetwork(listOf(null, "192.168.1.1", "4.3.2.1")))
        assertFalse(DirectConnection.isLanternNetwork(listOf("192.168.1.1", "8.8.8.8")))
        assertFalse(DirectConnection.isLanternNetwork(emptyList()))
    }

    @Test
    fun `only the matching lantern is reached at the lantern address`() {
        val devices = DirectConnection.reachable(listOf(parol, other), "A0:B1:C2:D3:E4:F5")
        assertEquals("4.3.2.1", devices[0].address)
        assertEquals(parol.copy(address = "4.3.2.1"), devices[0])
        assertEquals(other, devices[1])
    }

    @Test
    fun `without a direct connection devices are unchanged`() {
        val devices = listOf(parol, other)
        assertEquals(devices, DirectConnection.reachable(devices, null))
        assertEquals(devices, DirectConnection.reachable(devices, ""))
        assertEquals(devices, DirectConnection.reachable(devices, "ffffffffffff"))
    }

    @Test
    fun `the lantern address never replaces the saved home address`() {
        assertEquals("192.168.1.60", DirectConnection.addressToSave("192.168.1.60", "4.3.2.1"))
        assertEquals("192.168.1.75", DirectConnection.addressToSave("192.168.1.60", "192.168.1.75"))
        assertEquals("4.3.2.1", DirectConnection.addressToSave("", "4.3.2.1"))
    }

    @Test
    fun `direct means a saved lantern at the lantern address, not the setup item`() {
        assertTrue(DirectConnection.isDirect(parol.copy(address = "4.3.2.1")))
        assertFalse(DirectConnection.isDirect(parol))
        assertFalse(DirectConnection.isDirect(Device(macAddress = AP_MODE_MAC_ADDRESS, address = "4.3.2.1")))
    }

    @Test
    fun `time is sent as whole seconds`() {
        assertEquals("""{"time":1763251200}""", DirectConnection.timeJson(1763251200L))
    }
}
