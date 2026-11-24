package ca.cgagnier.wlednativeandroid.service.lanternwifi

import com.squareup.moshi.Moshi
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** A lantern's own WiFi, the one it opens when the home WiFi isn't there. */
data class LanternWifiCredentials(val ssid: String, val password: String)

/**
 * Rules for giving each lantern its own WiFi name and password instead of WLED's shared `WLED-AP` /
 * `wled1234`. Set with WLED's normal config API (`ap.ssid`, `ap.psk`); no firmware change.
 *
 * - The name shows the end of the MAC so lanterns are easy to tell apart; it is only a label.
 * - The password is random and never derived from the MAC (the lantern's WiFi broadcasts its MAC).
 * - WLED never returns the password (`/json/cfg` only gives its length), so the app keeps it.
 */
object LanternWifi {
    const val SSID_PREFIX = "Parol-"
    const val PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_LENGTH = 63
    private const val MAC_CHARACTERS = 4

    /** WLED's own WiFi name until a lantern is set up. */
    const val DEFAULT_SSID = "WLED-AP"
    const val DEFAULT_PASSWORD = "wled1234"

    /** Easy to read and type: no 0/o, 1/l/i. */
    const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

    /** e.g. "Parol-E4F5" for MAC a0b1c2d3e4f5. */
    fun ssidFor(macAddress: String): String {
        val hex = macAddress.filter { it.isLetterOrDigit() }.uppercase()
        return SSID_PREFIX + hex.takeLast(MAC_CHARACTERS)
    }

    fun newPassword(random: Random = SecureRandom().asKotlinRandom()): String =
        (1..PASSWORD_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** New credentials for the lantern with [macAddress]. */
    fun newCredentials(macAddress: String, random: Random = SecureRandom().asKotlinRandom()) =
        LanternWifiCredentials(ssidFor(macAddress), newPassword(random))

    /** `ap` for `POST /json/cfg`. WLED leaves the rest of its WiFi settings as they are. */
    fun apConfig(credentials: LanternWifiCredentials): Map<String, Any> =
        mapOf("ssid" to credentials.ssid, "psk" to credentials.password)

    /** Status of a lantern's own WiFi, from its `/json/cfg` (`ap.ssid`, `ap.pskl`) and what this phone saved. */
    fun status(lanternSsid: String?, passwordLength: Int?, saved: LanternWifiCredentials?): LanternWifiStatus = when {
        lanternSsid == null -> LanternWifiStatus.Unknown

        // Our passwords are as long as the default one, so the name tells them apart.
        lanternSsid == DEFAULT_SSID -> LanternWifiStatus.Shared

        saved != null && saved.ssid == lanternSsid && passwordLength == saved.password.length ->
            LanternWifiStatus.Secured

        else -> LanternWifiStatus.NotOnThisPhone
    }

    /** Body for `POST /json/cfg` that sets the lantern's own WiFi; [pin] if it has a settings PIN. */
    fun configJson(credentials: LanternWifiCredentials, pin: String? = null): String {
        val payload = mutableMapOf<String, Any>("ap" to apConfig(credentials))
        if (!pin.isNullOrEmpty()) payload["pin"] = pin
        return Moshi.Builder().build().adapter(Any::class.java).toJson(payload)
    }

    /** The lantern's own WiFi name and password length from its `/json/cfg`. */
    fun fromConfig(cfg: Map<*, *>): Pair<String?, Int?> {
        val ap = cfg["ap"] as? Map<*, *>
        return (ap?.get("ssid") as? String) to (ap?.get("pskl") as? Number)?.toInt()
    }

    /** Whether [password] is one WLED's WiFi can use (WPA2 needs 8 to 63 characters). */
    fun isValidPassword(password: String) = password.length in PASSWORD_LENGTH..MAX_PASSWORD_LENGTH
}

enum class LanternWifiStatus {
    /** The lantern couldn't be read. */
    Unknown,

    /** Still WLED's shared `WLED-AP` / `wled1234`. */
    Shared,

    /** Its own name and the password this phone has saved. */
    Secured,

    /** Changed, but this phone doesn't have the password (another phone set it, or the app was reinstalled). */
    NotOnThisPhone,
}
