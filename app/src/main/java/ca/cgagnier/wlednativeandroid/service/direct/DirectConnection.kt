package ca.cgagnier.wlednativeandroid.service.direct

import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import ca.cgagnier.wlednativeandroid.model.Device

/**
 * Rules for the "Direct connection": the phone is on a lantern's own WiFi (no home WiFi where the lantern
 * is), and the app talks to it at [DEFAULT_WLED_AP_IP] without changing what is saved for it.
 */
object DirectConnection {
    /** The lantern's own WiFi, as the lantern creates it unless changed in its settings. */
    const val AP_SSID = "WLED-AP"
    const val AP_PASSWORD = "wled1234"

    /**
     * Whether a WiFi looks like a lantern's own: the lantern is its router and DNS server.
     * [addresses] are the network's DNS servers and gateways.
     */
    fun isLanternNetwork(addresses: Collection<String?>): Boolean = addresses.any { it == DEFAULT_WLED_AP_IP }

    /** True when [device] is being reached over the lantern's own WiFi rather than the home WiFi. */
    fun isDirect(device: Device): Boolean =
        device.address == DEFAULT_WLED_AP_IP && device.macAddress != AP_MODE_MAC_ADDRESS

    /**
     * The devices as the app should reach them: the one whose MAC is [directMac] at the lantern's own
     * address, the rest as saved. Nothing here is saved.
     */
    fun reachable(devices: List<Device>, directMac: String?): List<Device> {
        if (directMac.isNullOrEmpty()) return devices
        return devices.map { if (sameMac(it.macAddress, directMac)) it.copy(address = DEFAULT_WLED_AP_IP) else it }
    }

    /**
     * The address to save when a device is seen at [newAddress]: the lantern's own address is only
     * reachable on its WiFi, so it never replaces the home one.
     */
    fun addressToSave(saved: String, newAddress: String): String =
        if (newAddress == DEFAULT_WLED_AP_IP && saved.isNotEmpty()) saved else newAddress

    /**
     * Sets the lantern's clock to the phone's ([epochSeconds], UTC). Without internet the lantern has no
     * time after a power-up, and its schedules don't run. WLED applies it through its normal state API and
     * keeps its own time zone.
     */
    fun timeJson(epochSeconds: Long): String = """{"time":$epochSeconds}"""

    fun sameMac(a: String, b: String): Boolean = normalize(a) == normalize(b)

    private fun normalize(mac: String) = mac.lowercase().filter { it.isLetterOrDigit() }
}
