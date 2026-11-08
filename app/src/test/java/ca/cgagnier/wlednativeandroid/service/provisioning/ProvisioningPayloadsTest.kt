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

    private val moshi = com.squareup.moshi.Moshi.Builder().build()

    private fun parse(json: String): Map<*, *> = moshi.adapter(Map::class.java).fromJson(json)!!

    @Test
    fun powerLimit_setsTheTotalAndEachOutputWithEverythingElseKept() {
        // hw.led as WLED 0.15.3 writes it for a lantern: one output of 160 LEDs, limited to 850 mA.
        val cfg = parse(
            """{"hw":{"led":{"total":160,"maxpwr":850,"ledma":0,"ins":[{"start":0,"len":160,"pin":[2],""" +
                """"order":0,"rev":false,"skip":0,"type":22,"ref":false,"rgbwm":0,"freq":0,""" +
                """"maxpwr":850,"ledma":55}]}}}""",
        )
        val led = ProvisioningPayloads.powerLimitConfig(cfg)!!
        val json = ProvisioningPayloads.wifiConfigJson("Home", "secret123", led = led)
        assertEquals(
            """{"nw":{"ins":[{"ssid":"Home","psk":"secret123"}]},"hw":{"led":{"maxpwr":2000,"ins":[{"start":0,""" +
                """"len":160,"pin":[2],"order":0,"rev":false,"skip":0,"type":22,"ref":false,"rgbwm":0,"freq":0,""" +
                """"maxpwr":2000,"ledma":55}]}}}""",
            json,
        )
    }

    @Test
    fun powerLimit_isSplitAcrossOutputsByLedCount() {
        val cfg = parse("""{"hw":{"led":{"ins":[{"len":100,"pin":[2]},{"len":60,"pin":[4]},{"len":40,"pin":[5]}]}}}""")
        val ins = ProvisioningPayloads.powerLimitConfig(cfg)!!["ins"] as List<*>
        assertEquals(listOf(1000, 600, 400), ins.map { (it as Map<*, *>)["maxpwr"] })
    }

    @Test
    fun powerLimit_changesNothingWithoutOutputs() {
        assertEquals(null, ProvisioningPayloads.powerLimitConfig(parse("""{"hw":{"led":{}}}""")))
        assertEquals(null, ProvisioningPayloads.powerLimitConfig(parse("""{}""")))
    }
}
