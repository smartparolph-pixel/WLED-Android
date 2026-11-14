package ca.cgagnier.wlednativeandroid.repository

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.map

class UserPreferencesRepository(private val dataStore: DataStore<UserPreferences>) {

    val themeMode get() = dataStore.data.map { it.theme }
    val autoDiscovery get() = dataStore.data.map { it.automaticDiscovery }
    val showOfflineDevicesLast get() = dataStore.data.map { it.showOfflineLast }
    val showHiddenDevices get() = dataStore.data.map { it.showHiddenDevices }
    val lastUpdateCheckDate get() = dataStore.data.map { it.lastUpdateCheckDate }
    val appLockEnabled get() = dataStore.data.map { it.appLockEnabled }
    val appLockTimeout get() = dataStore.data.map { it.appLockTimeout }
    val appLockSettingsOnly get() = dataStore.data.map { it.appLockSettingsOnly }

    /** Off, settings only, or the whole app (see [ca.cgagnier.wlednativeandroid.service.applock.AppLockMode]). */
    suspend fun updateAppLockMode(enabled: Boolean, settingsOnly: Boolean) {
        dataStore.updateData {
            it.toBuilder()
                .setAppLockEnabled(enabled)
                .setAppLockSettingsOnly(settingsOnly)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    suspend fun updateAppLockTimeout(timeout: AppLockTimeout) {
        dataStore.updateData {
            it.toBuilder()
                .setAppLockTimeout(timeout)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    suspend fun updateThemeMode(themeSettings: ThemeSettings) {
        Log.d(TAG, "updateThemeMode")
        dataStore.updateData {
            it.toBuilder()
                .setTheme(themeSettings)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    suspend fun updateAutoDiscovery(autoDiscover: Boolean) {
        Log.d(TAG, "updateAutoDiscovery")
        dataStore.updateData {
            it.toBuilder()
                .setAutomaticDiscovery(autoDiscover)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    suspend fun updateShowOfflineDeviceLast(showOfflineDeviceLast: Boolean) {
        Log.d(TAG, "updateShowOfflineDeviceLast")
        dataStore.updateData {
            it.toBuilder()
                .setShowOfflineLast(showOfflineDeviceLast)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    suspend fun updateShowHiddenDevices(showHiddenDevices: Boolean) {
        Log.d(TAG, "updateShowHiddenDevices")
        dataStore.updateData {
            it.toBuilder()
                .setShowHiddenDevices(showHiddenDevices)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    suspend fun updateLastUpdateCheckDate(lastUpdateCheckDate: Long) {
        Log.d(TAG, "updateLastUpdateCheckDate")
        dataStore.updateData {
            it.toBuilder()
                .setLastUpdateCheckDate(lastUpdateCheckDate)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    companion object {
        private const val TAG: String = "UserPreferencesRepo"
    }
}
