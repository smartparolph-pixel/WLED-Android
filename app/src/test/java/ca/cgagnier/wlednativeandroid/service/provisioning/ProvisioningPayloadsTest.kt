package ca.cgagnier.wlednativeandroid.service.provisioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningPayloadsTest {

    @Test
    fun wifiConfigJson_onlyTouchesFirstNetworkEntry() {
        val json = ProvisioningPayloads.wifiConfigJson("Home WiFi", "p@ss\"word")
        assertEquals("""{"nw":{"ins":[{"ssid":"Home WiFi","psk":"p@ss\"word"}]}}""", json)
    }

    @Test
    fun sameMac_ignoresCaseAndSeparators() {
        assertTrue(ProvisioningPayloads.sameMac("AA:BB:CC:DD:EE:FF", "aabbccddeeff"))
        assertTrue(ProvisioningPayloads.sameMac("aa-bb-cc-dd-ee-ff", "AABBCCDDEEFF"))
    }

    @Test
    fun sameMac_rejectsMissingOrDifferent() {
        assertFalse(ProvisioningPayloads.sameMac(null, "aabbccddeeff"))
        assertFalse(ProvisioningPayloads.sameMac("", ""))
        assertFalse(ProvisioningPayloads.sameMac("aabbccddeeff", "aabbccddee00"))
    }
}
