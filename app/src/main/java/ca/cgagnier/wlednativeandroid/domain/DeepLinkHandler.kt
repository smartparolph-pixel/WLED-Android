package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import ca.cgagnier.wlednativeandroid.ui.MainActivity
import javax.inject.Inject

/**
 * Represents a parsed deep link result.
 */
sealed class DeepLink {
    /** Deep link with a MAC address identifier. */
    data class MacAddress(val mac: String) : DeepLink()

    /** Deep link with a network address (IP or hostname). */
    data class Address(val address: String) : DeepLink()

    /** Deep link to AP mode (4.3.2.1). */
    data object ApMode : DeepLink()
}

/**
 * Handles parsing of deep links from intents.
 *
 * Supports:
 * - wled://{mac_address} - e.g., wled://AABBCCDDEEFF
 * - wled://{ip_or_hostname} - e.g., wled://192.168.1.50 or wled://wled.local
 * - http://4.3.2.1 - Default WLED AP mode IP
 * - https://sinagtechlabs.com/smartparol.html?a={ip_or_hostname} - a "Share Parol" web link
 */
class DeepLinkHandler @Inject constructor() {

    companion object {
        private const val SCHEME_WLED = "wled"
        private const val SCHEME_HTTP = "http"
        private const val SCHEME_HTTPS = "https"
        private val WEB_HOSTS = setOf("sinagtechlabs.com", "www.sinagtechlabs.com")
        private const val WEB_SHARE_PATH = "/smartparol.html"
        private const val WEB_SHARE_PAGE = "https://sinagtechlabs.com$WEB_SHARE_PATH"
        private const val WEB_ADDRESS_PARAM = "a"
        private val ADDRESS_REGEX = Regex("^[A-Za-z0-9.-]{1,253}$")

        /**
         * The QR code uses the web link once sinagtechlabs.com/smartparol.html and the app-link files are
         * live (see docs/website); until then it uses the wled:// link, which needs Sinag installed.
         */
        const val SHARE_QR_USES_WEB_LINK = false

        /** The Sinag download page, also shown to people without the app who open a share link. */
        const val DOWNLOAD_PAGE = WEB_SHARE_PAGE

        /** Web link to a lantern: opens Sinag when installed, the download page otherwise. */
        fun webShareLink(address: String): String = "$WEB_SHARE_PAGE?$WEB_ADDRESS_PARAM=${Uri.encode(address)}"

        /** The link the "Share Parol" QR code holds. */
        fun qrLink(address: String): String = if (SHARE_QR_USES_WEB_LINK) webShareLink(address) else shareLink(address)
        private val MAC_ADDRESS_REGEX = Regex("^[0-9A-Fa-f]{12}$")

        /** The link a "Share Parol" QR code holds; opening it on another phone adds the lantern there. */
        fun shareLink(address: String): String = "$SCHEME_WLED://$address"

        /** Opens [uri] (e.g. a scanned share link) in [MainActivity], which adds and shows the lantern. */
        fun createViewIntent(context: Context, uri: Uri): Intent = Intent(Intent.ACTION_VIEW, uri)
            .setClass(context, MainActivity::class.java)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * Creates an Intent to open the app with the specified device.
         * This is shared between widgets, device controls, and other launch points.
         *
         * @param context The context to create the intent with
         * @param macAddress The device's MAC address
         * @return An Intent configured to open the device in the app
         */

        fun createDeviceIntent(context: Context, macAddress: String): Intent =
            Intent(Intent.ACTION_VIEW, "$SCHEME_WLED://$macAddress".toUri())
                .setClass(context, MainActivity::class.java)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /**
     * Parses an Intent and returns the corresponding [DeepLink] if valid.
     *
     * @param intent The intent to parse
     * @return The parsed [DeepLink] or null if not a valid deep link
     */
    fun parseIntent(intent: Intent?): DeepLink? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        return parseUri(intent.data)
    }

    /**
     * Parses a URI and returns the corresponding [DeepLink] if valid.
     *
     * @param uri The URI to parse
     * @return The parsed [DeepLink] or null if not a valid deep link URI
     */
    fun parseUri(uri: Uri?): DeepLink? {
        uri ?: return null

        return when (uri.scheme?.lowercase()) {
            SCHEME_WLED -> parseWledScheme(uri)
            SCHEME_HTTP -> parseHttpScheme(uri)
            SCHEME_HTTPS -> parseWebShareLink(uri)
            else -> null
        }
    }

    private fun parseWledScheme(uri: Uri): DeepLink? {
        // wled://identifier where identifier is the host
        val identifier = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return if (isMacAddress(identifier)) {
            DeepLink.MacAddress(identifier.uppercase())
        } else {
            DeepLink.Address(identifier)
        }
    }

    private fun parseWebShareLink(uri: Uri): DeepLink? {
        if (uri.host?.lowercase() !in WEB_HOSTS || uri.path != WEB_SHARE_PATH) return null
        val address = uri.getQueryParameter(WEB_ADDRESS_PARAM)?.trim()
        return address?.takeIf { ADDRESS_REGEX.matches(it) }?.let { DeepLink.Address(it) }
    }

    private fun parseHttpScheme(uri: Uri): DeepLink? {
        val host = uri.host ?: return null

        return if (host == DEFAULT_WLED_AP_IP) {
            DeepLink.ApMode
        } else {
            null
        }
    }

    /**
     * Checks if the given string is a valid MAC address format (12 hex characters).
     *
     * @param identifier The string to check
     * @return true if it's a valid MAC address format
     */
    fun isMacAddress(identifier: String): Boolean = MAC_ADDRESS_REGEX.matches(identifier)
}
