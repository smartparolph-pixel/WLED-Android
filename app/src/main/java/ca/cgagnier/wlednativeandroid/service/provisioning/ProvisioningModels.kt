package ca.cgagnier.wlednativeandroid.service.provisioning

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.timezone.TimezonePayloads
import ca.cgagnier.wlednativeandroid.service.timezone.WledTimezoneSetting
import com.squareup.moshi.Moshi

/**
 * Defaults for first-time setup of a WLED device over its own access point.
 *
 * Factory-flashed products (e.g. lanterns shipping with a custom AP name/password) can override
 * these through [ProvisioningRequest.apSsidPrefix] / [ProvisioningRequest.apPassword].
 */
object ProvisioningDefaults {
    /** Stock WLED AP SSID. */
    const val AP_SSID_PREFIX = "WLED-AP"

    /** Stock WLED AP WPA2 passphrase. */
    const val AP_PASSWORD = "wled1234"

    /** Stock WLED AP address (also the captive portal address). */
    const val AP_HOST = "4.3.2.1"

    const val HTTP_TIMEOUT_SECONDS = 10L
    const val AP_JOIN_TIMEOUT_MS = 60_000
    const val DEVICE_REAPPEAR_TIMEOUT_MS = 90_000L

    /** Time given to WLED to persist cfg.json before we ask it to reboot. */
    const val CONFIG_WRITE_GRACE_MS = 2_000L

    /**
     * Brightness limiter for the lanterns' 5 V 3 A (15 W) power supply, set during setup: 2000 mA leaves
     * headroom below the supply's rating. WLED dims all LEDs to stay under it.
     */
    const val POWER_LIMIT_MA = 2000
}

data class ProvisioningRequest(
    /** SSID of the user's home router. */
    val homeSsid: String,
    /** Password of the user's home router. Empty for an open network. */
    val homePassword: String,
    val apSsidPrefix: String = ProvisioningDefaults.AP_SSID_PREFIX,
    /** Null means the setup AP is open (no passphrase). */
    val apPassword: String? = ProvisioningDefaults.AP_PASSWORD,
    /** Sent along with the credentials so the device shows local time right away. Null leaves it unchanged. */
    val timezone: WledTimezoneSetting? = null,
)

/** Steps surfaced to the UI. */
sealed interface ProvisioningProgress {
    /** System dialog asking the user to pick/confirm the device's setup network. */
    data object JoiningSetupNetwork : ProvisioningProgress
    data object SendingCredentials : ProvisioningProgress
    data object RebootingDevice : ProvisioningProgress
    data object WaitingForDevice : ProvisioningProgress
    data class Done(val device: Device) : ProvisioningProgress
}

sealed class ProvisioningException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Android < 10: the user must join the setup network in Settings first. */
    class ManualJoinRequired : ProvisioningException("Manual join required")
    class SetupNetworkUnavailable : ProvisioningException("Setup network not found or declined")
    class NotAWledDevice(cause: Throwable? = null) : ProvisioningException("Not a WLED device", cause)
    class CredentialsRejected(val httpCode: Int) : ProvisioningException("Device rejected credentials ($httpCode)")
    class DeviceDidNotReappear : ProvisioningException("Device did not appear on the home network")
}

/** Pure helpers, kept free of Android types so they can be unit-tested on the JVM. */
object ProvisioningPayloads {
    private val moshi: Moshi = Moshi.Builder().build()

    /**
     * Body for `POST /json/cfg`.
     *
     * Only `nw.ins[0]` is sent. WLED's deserializeConfig leaves every field that is absent from
     * the JSON untouched, so unlike posting the whole /settings/wifi HTML form this cannot reset
     * AP behaviour, static IP, mDNS name, etc. [timezone] adds `if.ntp`, which is equally partial.
     */
    fun wifiConfigJson(
        ssid: String,
        password: String,
        timezone: WledTimezoneSetting? = null,
        led: Map<String, Any>? = null,
    ): String {
        val payload = mutableMapOf<String, Any>(
            "nw" to mapOf(
                "ins" to listOf(
                    mapOf("ssid" to ssid, "psk" to password),
                ),
            ),
        )
        if (timezone != null) payload["if"] = mapOf("ntp" to TimezonePayloads.ntpConfig(timezone))
        if (led != null) payload["hw"] = mapOf("led" to led)
        return moshi.adapter(Any::class.java).toJson(payload)
    }

    /**
     * `hw.led` for `POST /json/cfg` that sets the brightness limiter to [limitMa], built from the
     * device's current `/json/cfg`.
     *
     * WLED 0.15 enforces the limit per LED output (`hw.led.ins[].maxpwr`); `hw.led.maxpwr` is only the
     * total shown on the settings page. Its settings page splits the total across outputs by LED count,
     * and so does this. Sending `ins` makes the firmware rebuild every output from the list, so each one
     * is sent back whole, with only `maxpwr` changed. Numbers are kept whole: WLED reads `160.0` as "not
     * an integer" and would fall back to its defaults (e.g. 1 LED).
     *
     * @return null if the config has no LED outputs, so nothing is changed.
     */
    fun powerLimitConfig(cfg: Map<*, *>, limitMa: Int = ProvisioningDefaults.POWER_LIMIT_MA): Map<String, Any>? {
        val led = (cfg["hw"] as? Map<*, *>)?.get("led") as? Map<*, *>
        val outputs = (led?.get("ins") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val lengths = outputs.map { (it["len"] as? Number)?.toInt() ?: 0 }
        val total = lengths.sum()
        if (outputs.isEmpty() || total <= 0) return null
        var left = limitMa
        val ins = outputs.mapIndexed { index, output ->
            val share = if (index == outputs.lastIndex) left else (limitMa.toLong() * lengths[index] / total).toInt()
            left -= share
            wholeNumbers(output) as Map<*, *> + ("maxpwr" to share)
        }
        return mapOf("maxpwr" to limitMa, "ins" to ins)
    }

    /** Copies JSON as parsed by Moshi, turning whole-valued doubles back into integers. */
    private fun wholeNumbers(value: Any?): Any? = when (value) {
        is Double -> if (value % 1.0 == 0.0 && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
            value.toLong()
        } else {
            value
        }

        is Map<*, *> -> value.entries.associate { (key, item) -> key.toString() to wholeNumbers(item) }

        is List<*> -> value.map { wholeNumbers(it) }

        else -> value
    }

    /** Body for `POST /json/state` that makes WLED reboot. */
    const val REBOOT_JSON = """{"rb":true}"""

    /** mDNS gives "aabbccddeeff", `/json/info` gives the same, but be lenient about case and colons. */
    fun normalizeMac(mac: String?): String = mac.orEmpty().replace(":", "").replace("-", "").lowercase()

    fun sameMac(a: String?, b: String?): Boolean {
        val na = normalizeMac(a)
        return na.isNotEmpty() && na == normalizeMac(b)
    }
}
