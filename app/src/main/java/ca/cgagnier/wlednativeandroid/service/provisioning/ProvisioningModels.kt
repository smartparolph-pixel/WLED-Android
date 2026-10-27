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
    /** Android < 10: the app cannot join networks programmatically. */
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
    fun wifiConfigJson(ssid: String, password: String, timezone: WledTimezoneSetting? = null): String {
        val payload = mutableMapOf<String, Any>(
            "nw" to mapOf(
                "ins" to listOf(
                    mapOf("ssid" to ssid, "psk" to password),
                ),
            ),
        )
        if (timezone != null) payload["if"] = mapOf("ntp" to TimezonePayloads.ntpConfig(timezone))
        return moshi.adapter(Any::class.java).toJson(payload)
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
