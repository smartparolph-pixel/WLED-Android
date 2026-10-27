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
    val encryptedSession get() = dataStore.data.map { it.encryptedSession }

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

    /** [encrypted] is already encrypted by the caller; never log it. */
    suspend fun updateEncryptedSession(encrypted: String) {
        dataStore.updateData {
            it.toBuilder()
                .setEncryptedSession(encrypted)
                .setDateLastWritten(System.currentTimeMillis())
                .build()
        }
    }

    companion object {
        private const val TAG: String = "UserPreferencesRepo"
    }
}
