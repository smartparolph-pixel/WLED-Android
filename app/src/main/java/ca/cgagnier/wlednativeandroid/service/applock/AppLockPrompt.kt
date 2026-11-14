package ca.cgagnier.wlednativeandroid.service.applock

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Shows the phone's own unlock prompt: fingerprint or face, with the phone's PIN, pattern or password. */
object AppLockPrompt {
    /** Strong biometrics plus screen lock; Android 9-10 only accept weak biometrics together with the screen lock. */
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    /**
     * @param onResult true when the user unlocked; false when they cancelled or it failed (they can try again).
     */
    fun show(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
