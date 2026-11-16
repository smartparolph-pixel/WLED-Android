package ca.cgagnier.wlednativeandroid.service.lanternwifi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LanternWifiStore"
private const val PREFS = "lantern_wifi"
private const val KEY_ALIAS = "lantern_wifi"
private const val KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_BITS = 256
private const val TAG_BITS = 128
private const val SEPARATOR = '\n'

/**
 * Each lantern's own WiFi name and password, by MAC. The password is encrypted with a key kept in the
 * Android Keystore, which never leaves the phone; it is also left out of backups (see backup rules).
 */
@Singleton
class LanternWifiStore @Inject constructor(@param:ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _all = MutableStateFlow(readAll())

    /** Saved credentials by MAC (lowercase, no separators). */
    val all: StateFlow<Map<String, LanternWifiCredentials>> = _all.asStateFlow()

    fun get(macAddress: String): LanternWifiCredentials? = _all.value[key(macAddress)]

    fun save(macAddress: String, credentials: LanternWifiCredentials) {
        val sealed = encrypt(credentials.ssid + SEPARATOR + credentials.password) ?: return
        prefs.edit().putString(key(macAddress), sealed).apply()
        _all.value = _all.value + (key(macAddress) to credentials)
    }

    fun remove(macAddress: String) {
        prefs.edit().remove(key(macAddress)).apply()
        _all.value = _all.value - key(macAddress)
    }

    private fun readAll(): Map<String, LanternWifiCredentials> = prefs.all.mapNotNull { (mac, value) ->
        val text = (value as? String)?.let(::decrypt) ?: return@mapNotNull null
        val ssid = text.substringBefore(SEPARATOR)
        val password = text.substringAfter(SEPARATOR, "")
        if (password.isEmpty()) null else mac to LanternWifiCredentials(ssid, password)
    }.toMap()

    private fun key(macAddress: String) = macAddress.lowercase().filter { it.isLetterOrDigit() }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    /** "iv:ciphertext", both Base64. */
    private fun encrypt(text: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
    } catch (e: GeneralSecurityException) {
        Log.e(TAG, "Could not encrypt the lantern WiFi password", e)
        null
    }

    private fun decrypt(value: String): String? = try {
        val iv = Base64.decode(value.substringBefore(":"), Base64.NO_WRAP)
        val sealed = Base64.decode(value.substringAfter(":"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        String(cipher.doFinal(sealed), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        // E.g. restored onto another phone, where the Keystore key doesn't exist.
        Log.w(TAG, "Could not read a saved lantern WiFi password", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Saved lantern WiFi entry is damaged", e)
        null
    }
}
