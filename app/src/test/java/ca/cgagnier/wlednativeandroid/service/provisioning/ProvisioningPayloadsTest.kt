package ca.cgagnier.wlednativeandroid.service.provisioning

import ca.cgagnier.wlednativeandroid.service.timezone.WledTimezoneSetting
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
    fun wifiConfigJson_addsTimezoneWhenGiven() {
        val json = ProvisioningPayloads.wifiConfigJson("Home", "secret123", WledTimezoneSetting(9, 0, exact = true))
        assertEquals(
            """{"nw":{"ins":[{"ssid":"Home","psk":"secret123"}]},"if":{"ntp":{"en":true,"tz":9,"offset":0}}}""",
            json,
        )
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
