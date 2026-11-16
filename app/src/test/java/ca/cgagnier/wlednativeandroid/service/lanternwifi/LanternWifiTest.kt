package ca.cgagnier.wlednativeandroid.service.lanternwifi

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LanternWifiTest {
    private val mac = "a0b1c2d3e4f5"

    @Test
    fun `name shows the end of the MAC`() {
        assertEquals("Parol-E4F5", LanternWifi.ssidFor(mac))
        assertEquals("Parol-E4F5", LanternWifi.ssidFor("A0:B1:C2:D3:E4:F5"))
    }

    @Test
    fun `password is 8 easy-to-read characters, not taken from the MAC`() {
        repeat(200) {
            val password = LanternWifi.newPassword()
            assertEquals(8, password.length)
            assertTrue(password.all { it in LanternWifi.ALPHABET })
            assertTrue(LanternWifi.isValidPassword(password))
        }
        assertFalse(LanternWifi.ALPHABET.any { it in "0o1li" })
        assertFalse(LanternWifi.newPassword().contains("e4f5"))
    }

    @Test
    fun `passwords differ between lanterns`() {
        val passwords = (1..50).map { LanternWifi.newCredentials(mac).password }.toSet()
        assertTrue(passwords.size > 45)
        assertNotEquals(LanternWifi.newPassword(Random(1)), LanternWifi.newPassword(Random(2)))
    }

    @Test
    fun `WLED needs at least 8 characters`() {
        assertFalse(LanternWifi.isValidPassword("12345"))
        assertTrue(LanternWifi.isValidPassword("k7m2q9xd"))
    }

    @Test
    fun `config sets only the lantern WiFi, with the PIN when given`() {
        val credentials = LanternWifiCredentials("Parol-E4F5", "k7m2q9xd")
        val adapter = Moshi.Builder().build().adapter(Map::class.java)
        assertEquals(
            mapOf("ap" to mapOf("ssid" to "Parol-E4F5", "psk" to "k7m2q9xd")),
            adapter.fromJson(LanternWifi.configJson(credentials)),
        )
        assertEquals("1234", adapter.fromJson(LanternWifi.configJson(credentials, "1234"))!!["pin"])
    }

    @Test
    fun `reads the name and password length WLED reports`() {
        val cfg = mapOf("ap" to mapOf("ssid" to "WLED-AP", "pskl" to 8.0, "chan" to 1.0))
        assertEquals("WLED-AP" to 8, LanternWifi.fromConfig(cfg))
        assertEquals(null to null, LanternWifi.fromConfig(emptyMap<String, Any>()))
    }

    @Test
    fun `status tells shared, secured and not-on-this-phone apart`() {
        val saved = LanternWifiCredentials("Parol-E4F5", "k7m2q9xd")
        assertEquals(LanternWifiStatus.Unknown, LanternWifi.status(null, null, saved))
        assertEquals(LanternWifiStatus.Shared, LanternWifi.status("WLED-AP", 8, null))
        assertEquals(LanternWifiStatus.Shared, LanternWifi.status("WLED-AP", 8, saved))
        assertEquals(LanternWifiStatus.Secured, LanternWifi.status("Parol-E4F5", 8, saved))
        assertEquals(LanternWifiStatus.NotOnThisPhone, LanternWifi.status("Parol-E4F5", 8, null))
        assertEquals(LanternWifiStatus.NotOnThisPhone, LanternWifi.status("Parol-E4F5", 12, saved))
        assertEquals(LanternWifiStatus.NotOnThisPhone, LanternWifi.status("MyLantern", 10, saved))
    }
}
