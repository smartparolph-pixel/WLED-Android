package ca.cgagnier.wlednativeandroid.ui.applock

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.applock.AppLockManager
import ca.cgagnier.wlednativeandroid.service.applock.AppLockPrompt
import ca.cgagnier.wlednativeandroid.service.applock.AppLockState
import ca.cgagnier.wlednativeandroid.ui.components.SinagLockup

/**
 * Covers [content] while the app is locked. The content stays composed underneath, so whatever the user was
 * doing (e.g. setting up a device) is still there after unlocking; it is hidden from accessibility services
 * while covered.
 */
@Composable
fun AppLockGate(manager: AppLockManager, content: @Composable () -> Unit) {
    val state by manager.state.collectAsStateWithLifecycle()
    val turnedOff by manager.turnedOffNoScreenLock.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = if (state == AppLockState.Unlocked) Modifier else Modifier.clearAndSetSemantics { },
        ) {
            content()
        }
        if (state != AppLockState.Unlocked) LockScreen(manager, showUnlock = state == AppLockState.Locked)
    }
    if (turnedOff) {
        AlertDialog(
            onDismissRequest = manager::dismissTurnedOffNotice,
            title = { Text(stringResource(R.string.app_lock_turned_off_title)) },
            text = { Text(stringResource(R.string.app_lock_turned_off)) },
            confirmButton = {
                TextButton(onClick = manager::dismissTurnedOffNotice) { Text(stringResource(R.string.dismiss)) }
            },
        )
    }
}

@Composable
private fun LockScreen(manager: AppLockManager, showUnlock: Boolean) {
    val activity = LocalContext.current.findFragmentActivity()
    val lockCount by manager.lockCount.collectAsStateWithLifecycle()
    val title = stringResource(R.string.app_lock_prompt_title)
    val unlock = {
        if (activity != null && !manager.authenticating) {
            manager.authenticating = true
            AppLockPrompt.show(activity, title) { ok ->
                manager.authenticating = false
                if (ok) manager.unlocked()
            }
        }
    }
    // Open the prompt by itself once per lock; after a cancel, the Unlock button tries again.
    LaunchedEffect(lockCount, showUnlock) { if (showUnlock) unlock() }
    // Back leaves the app instead of going around the lock.
    BackHandler { activity?.moveTaskToBack(true) }

    // A Surface without onClick still takes all touches, so nothing underneath can be tapped.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!showUnlock) return@Surface
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SinagLockup(modifier = Modifier.height(72.dp))
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.app_lock_locked),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = unlock, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.app_lock_unlock))
            }
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
