package ca.cgagnier.wlednativeandroid.service.applock

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLockState {
    /** Reading the setting at start-up; nothing is shown yet. */
    Checking,
    Locked,
    Unlocked,
}

/**
 * App lock: when on, the phone's own fingerprint, face or screen lock is needed to open the app, at start and
 * after [AppLockPolicy] says the app was in the background long enough. Nothing is stored but the setting;
 * the phone does the checking. The home-screen widget is not affected.
 */
@Singleton
class AppLockManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AppLockState.Checking)
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    /** Counts lock events, so the unlock prompt opens by itself once per lock. */
    private val _lockCount = MutableStateFlow(0)
    val lockCount: StateFlow<Int> = _lockCount.asStateFlow()

    /** True once the lock was turned off because the phone no longer has a screen lock. */
    private val _turnedOffNoScreenLock = MutableStateFlow(false)
    val turnedOffNoScreenLock: StateFlow<Boolean> = _turnedOffNoScreenLock.asStateFlow()

    private var backgroundedAt: Long? = null

    /** While the unlock prompt is up the app may go to the background (the PIN screen); that must not relock. */
    var authenticating = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            if (preferences.appLockEnabled.first()) lockOrTurnOff() else _state.value = AppLockState.Unlocked
        }
    }

    /** The phone has a PIN, pattern, password, fingerprint or face, so the lock can be used. */
    fun isAvailable(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun unlocked() {
        authenticating = false
        _state.value = AppLockState.Unlocked
    }

    fun dismissTurnedOffNotice() {
        _turnedOffNoScreenLock.value = false
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!authenticating && _state.value == AppLockState.Unlocked) backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        scope.launch {
            val enabled = preferences.appLockEnabled.first()
            val timeout = preferences.appLockTimeout.first()
            if (AppLockPolicy.shouldLockOnReturn(enabled, since, SystemClock.elapsedRealtime(), timeout)) {
                lockOrTurnOff()
            }
        }
    }

    private suspend fun lockOrTurnOff() {
        if (isAvailable()) {
            _state.value = AppLockState.Locked
            _lockCount.value++
        } else {
            // The screen lock was removed: turn App lock off rather than lock the user out.
            preferences.updateAppLockEnabled(false)
            _turnedOffNoScreenLock.value = true
            _state.value = AppLockState.Unlocked
        }
    }
}
