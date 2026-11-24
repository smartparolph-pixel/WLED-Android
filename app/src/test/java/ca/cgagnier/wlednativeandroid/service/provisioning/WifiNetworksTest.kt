package ca.cgagnier.wlednativeandroid.service.provisioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworksTest {
    private fun ap(ssid: String, level: Int, mhz: Int = 2437, caps: String = "[WPA2-PSK-CCMP][ESS]") =
        ScannedAccessPoint(ssid, level, mhz, caps)

    @Test
    fun `one row per name with the strongest signal`() {
        val choices = WifiNetworks.choices(listOf(ap("Home", -80), ap("Home", -50), ap("Neighbour", -70)))
        assertEquals(listOf("Home", "Neighbour"), choices.map { it.ssid })
        assertEquals(4, choices[0].bars)
    }

    @Test
    fun `hidden networks and lanterns' own WiFi are left out`() {
        val choices = WifiNetworks.choices(
            listOf(ap("", -40), ap("WLED-AP", -40), ap("Parol-E4F5", -40), ap("Home", -60)),
        )
        assertEquals(listOf("Home"), choices.map { it.ssid })
    }

    @Test
    fun `5 GHz-only networks are listed last and can't be chosen`() {
        val choices = WifiNetworks.choices(
            listOf(ap("Fast5G", -40, mhz = 5180), ap("Home", -70), ap("Dual", -75, mhz = 5240), ap("Dual", -80)),
        )
        assertEquals(listOf("Home", "Dual", "Fast5G"), choices.map { it.ssid })
        assertTrue(choices.first { it.ssid == "Dual" }.supported)
        assertFalse(choices.first { it.ssid == "Fast5G" }.supported)
    }

    @Test
    fun `open networks need no password`() {
        val choices = WifiNetworks.choices(listOf(ap("Cafe", -60, caps = "[ESS]"), ap("Home", -60)))
        assertFalse(choices.first { it.ssid == "Cafe" }.secured)
        assertTrue(choices.first { it.ssid == "Home" }.secured)
    }

    @Test
    fun `signal bars`() {
        assertEquals(listOf(4, 3, 2, 1, 0), listOf(-50, -60, -70, -80, -95).map(WifiNetworks::bars))
    }
}
