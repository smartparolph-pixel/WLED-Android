package ca.cgagnier.wlednativeandroid.service.provisioning

/** One access point from a WiFi scan, as Android reports it. */
data class ScannedAccessPoint(
    val ssid: String,
    /** Signal in dBm (e.g. -45 strong, -85 weak). */
    val level: Int,
    val frequencyMhz: Int,
    /** Android's description of its security, e.g. "[WPA2-PSK-CCMP][ESS]". */
    val capabilities: String,
)

/** A WiFi network to offer in setup: one row per name, however many access points share it. */
data class WifiChoice(
    val ssid: String,
    /** 0 to 4 bars. */
    val bars: Int,
    /** Needs a password. */
    val secured: Boolean,
    /** Has a 2.4 GHz band; the lanterns' ESP32 can't join 5 GHz-only networks. */
    val supported: Boolean,
)

/** Turns WiFi scan results into the list shown in "Set up a new device". */
object WifiNetworks {
    private const val MAX_2_4_GHZ_MHZ = 2500
    private const val EXCELLENT_DBM = -55
    private const val GOOD_DBM = -67
    private const val FAIR_DBM = -75
    private const val WEAK_DBM = -85
    private const val FULL_BARS = 4

    /** A lantern's own WiFi, not a home network. */
    private val lanternPrefixes = listOf("WLED-AP", "Parol-")
    private val securedMarkers = listOf("WPA", "WEP", "SAE", "PSK", "EAP", "OWE")

    /**
     * One entry per network name, without hidden networks or lanterns' own WiFi: networks the lantern can
     * join first, then by signal.
     */
    fun choices(scan: List<ScannedAccessPoint>): List<WifiChoice> = scan
        .filter { it.ssid.isNotBlank() && lanternPrefixes.none { prefix -> it.ssid.startsWith(prefix) } }
        .groupBy { it.ssid }
        .map { (ssid, points) ->
            val strongest = points.maxOf { it.level }
            WifiChoice(
                ssid = ssid,
                bars = bars(strongest),
                secured = points.any { point -> securedMarkers.any { point.capabilities.contains(it) } },
                supported = points.any { it.frequencyMhz in 1..MAX_2_4_GHZ_MHZ },
            ) to strongest
        }
        .sortedWith(compareByDescending<Pair<WifiChoice, Int>> { it.first.supported }.thenByDescending { it.second })
        .map { it.first }

    fun bars(levelDbm: Int): Int = when {
        levelDbm >= EXCELLENT_DBM -> FULL_BARS
        levelDbm >= GOOD_DBM -> FULL_BARS - 1
        levelDbm >= FAIR_DBM -> 2
        levelDbm >= WEAK_DBM -> 1
        else -> 0
    }
}
