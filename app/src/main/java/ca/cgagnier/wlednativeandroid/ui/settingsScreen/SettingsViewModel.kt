package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.AppLockTimeout
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.applock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val appLock: AppLockManager,
) : ViewModel() {

    private val theme = preferencesRepository.themeMode
    private val autoDiscovery = preferencesRepository.autoDiscovery
    private val showOfflineDevicesLast = preferencesRepository.showOfflineDevicesLast
    private val showHiddenDevices = preferencesRepository.showHiddenDevices

    private val listing = combine(
        autoDiscovery,
        showOfflineDevicesLast,
        showHiddenDevices,
        theme,
    ) { autoDiscovery, showOfflineDevicesLast, showHiddenDevices, theme ->
        SettingsState(
            isAutoDiscoveryEnabled = autoDiscovery,
            showOfflineLast = showOfflineDevicesLast,
            showHiddenDevices = showHiddenDevices,
            theme = theme,
        )
    }

    val settingsState = combine(
        listing,
        preferencesRepository.appLockEnabled,
        preferencesRepository.appLockTimeout,
    ) { state, lockEnabled, lockTimeout ->
        state.copy(appLockEnabled = lockEnabled, appLockTimeout = lockTimeout)
    }.stateIn(viewModelScope, WhileSubscribed(5000), SettingsState())

    /** The phone has a screen lock (PIN, pattern, password, fingerprint or face), so App lock can be used. */
    fun isAppLockAvailable(): Boolean = appLock.isAvailable()

    /** Call only after the user confirmed with the phone's unlock prompt. */
    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.updateAppLockEnabled(enabled)
    }

    fun setAppLockTimeout(timeout: AppLockTimeout) = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.updateAppLockTimeout(timeout)
    }

    /** The prompt may send the app to the background (PIN screen); that must not count as leaving the app. */
    fun setAuthenticating(authenticating: Boolean) {
        appLock.authenticating = authenticating
    }

    fun setAutoDiscover(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.updateAutoDiscovery(enabled)
    }
    fun setShowOfflineDevicesLast(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.updateShowOfflineDeviceLast(enabled)
    }
    fun setShowHiddenDevices(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.updateShowHiddenDevices(enabled)
    }
    fun setTheme(theme: ThemeSettings) = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.updateThemeMode(theme)
    }
}

@Stable
data class SettingsState(
    val isAutoDiscoveryEnabled: Boolean = true,
    val showOfflineLast: Boolean = true,
    val showHiddenDevices: Boolean = false,
    val theme: ThemeSettings = ThemeSettings.Auto,
    val appLockEnabled: Boolean = false,
    val appLockTimeout: AppLockTimeout = AppLockTimeout.TWO_MINUTES,
)
