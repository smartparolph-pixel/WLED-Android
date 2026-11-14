package ca.cgagnier.wlednativeandroid.service.applock

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ca.cgagnier.wlednativeandroid.repository.AppLockTimeout
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/** What App lock covers. */
enum class AppLockMode {
    Off,

    /** The lights stay free to use; opening or changing settings needs the phone's unlock. */
    SettingsOnly,

    /** Opening the app needs the phone's unlock. */
    WholeApp,
    ;

    companion object {
        fun of(enabled: Boolean, settingsOnly: Boolean): AppLockMode = when {
            !enabled -> Off
            settingsOnly -> SettingsOnly
            else -> WholeApp
        }
    }
}

/**
 * App lock: the phone's own fingerprint, face or screen lock is needed to open the app ([AppLockMode.WholeApp])
 * or to change settings ([AppLockMode.SettingsOnly]), and again once [AppLockPolicy] says enough time has
 * passed. Nothing is stored but the setting; the phone does the checking. The home-screen widget is not
 * affected.
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

    private var mode = AppLockMode.Off
    private var timeout = AppLockTimeout.TWO_MINUTES
    private var backgroundedAt: Long? = null

    /** When settings were last unlocked ([AppLockMode.SettingsOnly]); they stay open for the chosen time. */
    private var settingsUnlockedAt: Long? = null

    /** While the unlock prompt is up the app may go to the background (the PIN screen); that must not relock. */
    var authenticating = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            mode = AppLockMode.of(preferences.appLockEnabled.first(), preferences.appLockSettingsOnly.first())
            timeout = preferences.appLockTimeout.first()
            if (mode == AppLockMode.WholeApp) lockOrTurnOff() else _state.value = AppLockState.Unlocked
            combine(
                preferences.appLockEnabled,
                preferences.appLockSettingsOnly,
                preferences.appLockTimeout,
            ) { enabled, settingsOnly, newTimeout ->
                mode = AppLockMode.of(enabled, settingsOnly)
                timeout = newTimeout
            }.collect { }
        }
    }

    /** The phone has a PIN, pattern, password, fingerprint or face, so the lock can be used. */
    fun isAvailable(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun unlocked() {
        authenticating = false
        _state.value = AppLockState.Unlocked
    }

    /**
     * True when opening or changing settings must ask for the phone's unlock first. If the phone's screen
     * lock was removed, App lock turns itself off instead (with a notice) and nothing is asked.
     */
    fun settingsNeedUnlock(): Boolean {
        val usable = mode == AppLockMode.SettingsOnly && isAvailable()
        if (mode == AppLockMode.SettingsOnly && !usable) turnOff()
        val since = settingsUnlockedAt
        val stillOpen = since != null && AppLockPolicy.stillUnlocked(since, SystemClock.elapsedRealtime(), timeout)
        return usable && !stillOpen
    }

    fun settingsUnlocked() {
        authenticating = false
        settingsUnlockedAt = SystemClock.elapsedRealtime()
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
        val wholeApp = mode == AppLockMode.WholeApp
        if (AppLockPolicy.shouldLockOnReturn(wholeApp, since, SystemClock.elapsedRealtime(), timeout)) {
            scope.launch { lockOrTurnOff() }
        }
    }

    private suspend fun lockOrTurnOff() {
        if (isAvailable()) {
            _state.value = AppLockState.Locked
            _lockCount.value++
        } else {
            preferences.updateAppLockMode(enabled = false, settingsOnly = false)
            _turnedOffNoScreenLock.value = true
            _state.value = AppLockState.Unlocked
        }
    }

    /** The screen lock was removed: turn App lock off rather than lock the user out. */
    private fun turnOff() {
        mode = AppLockMode.Off
        _turnedOffNoScreenLock.value = true
        scope.launch { preferences.updateAppLockMode(enabled = false, settingsOnly = false) }
    }
}
