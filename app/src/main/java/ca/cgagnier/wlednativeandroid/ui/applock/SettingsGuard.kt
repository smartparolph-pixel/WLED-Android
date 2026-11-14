package ca.cgagnier.wlednativeandroid.ui.applock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.applock.AppLockManager
import ca.cgagnier.wlednativeandroid.service.applock.AppLockPrompt

/** The app's [AppLockManager], provided by [AppLockGate] to everything inside it. */
val LocalAppLockManager = staticCompositionLocalOf<AppLockManager?> { null }

/**
 * Runs an action that opens or changes settings. With App lock set to "Settings only", it first asks for the
 * phone's fingerprint, face or screen lock (once per the chosen time); otherwise it just runs the action.
 */
@Composable
fun rememberSettingsGuard(): (action: () -> Unit) -> Unit {
    val manager = LocalAppLockManager.current
    val activity = LocalContext.current.findFragmentActivity()
    val title = stringResource(R.string.app_lock_settings_prompt_title)
    return remember(manager, activity, title) {
        { action ->
            if (manager == null || activity == null || !manager.settingsNeedUnlock()) {
                action()
            } else if (!manager.authenticating) {
                manager.authenticating = true
                AppLockPrompt.show(activity, title) { ok ->
                    manager.authenticating = false
                    if (ok) {
                        manager.settingsUnlocked()
                        action()
                    }
                }
            }
        }
    }
}
