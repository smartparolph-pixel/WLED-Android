package ca.cgagnier.wlednativeandroid.service.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "KeystoreSessionStore"

/**
 * Keeps the session in the existing Proto DataStore, encrypted with an AES-GCM key that never leaves
 * the Android Keystore. Nothing about the session is logged.
 */
class KeystoreSessionStore(private val preferences: UserPreferencesRepository) : SessionStore {

    override suspend fun save(session: AuthSession) {
        val encrypted = encrypt(SessionCodec.encode(session).toByteArray())
        preferences.updateEncryptedSession(Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }

    override suspend fun load(): AuthSession? {
        val stored = preferences.encryptedSession.first()
        if (stored.isEmpty()) return null
        val session = try {
            SessionCodec.decode(String(decrypt(Base64.decode(stored, Base64.NO_WRAP))))
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Stored session could not be decrypted; signing out", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Stored session is malformed; signing out", e)
            null
        }
        if (session == null) clear()
        return session
    }

    override suspend fun clear() {
        preferences.updateEncryptedSession("")
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_LENGTH) { "Ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH))
        return cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sinag_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
