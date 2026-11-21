package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningDefaults
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningPayloads
import ca.cgagnier.wlednativeandroid.service.update.DEFAULT_REPO
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

private const val TAG = "DeviceEditViewModel"
private val JSON = "application/json".toMediaType()

/**
 * ViewModel for the device edit screen.
 *
 * Manages update-related dialog state, device property mutations,
 * and update-check operations. Exposes a single [uiState] flow
 * consumed by the UI composable.
 */
@HiltViewModel
@Suppress("LongParameterList") // DI constructor requires multiple dependencies.
class DeviceEditViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val repositoryDao: RepositoryDao,
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val githubApi: GithubApi,
    private val releaseService: ReleaseService,
    private val widgetManager: WledWidgetManager,
    @param:ApplicationContext private val applicationContext: Context,
    private val deviceApiFactory: DeviceApiFactory,
) : ViewModel() {

    private val _renameStatus = MutableStateFlow(RenameStatus.Idle)

    /** How the last rename went; merged into the UI state by the screen. */
    val renameStatus: StateFlow<RenameStatus> = _renameStatus.asStateFlow()

    private val updateDetailsVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val updateDisclaimerVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val updateInstallVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val isCheckingUpdates = MutableStateFlow(false)
    private val repository = MutableStateFlow<Repository?>(null)

    /**
     * Single combined UI state for the device edit screen.
     *
     * The [DeviceEditUiState.updateTag] field is always `null` here because
     * the update tag comes from [DeviceWithState.updateVersionTagFlow], which
     * is owned by the caller and merged in the composable layer.
     */
    val uiState: StateFlow<DeviceEditUiState> = combine(
        isCheckingUpdates,
        repository,
        updateDetailsVersion,
        updateDisclaimerVersion,
        updateInstallVersion,
    ) { isChecking, repo, details, disclaimer, install ->
        DeviceEditUiState(
            isCheckingUpdates = isChecking,
            repository = repo,
            updateDetailsVersion = details,
            updateDisclaimerVersion = disclaimer,
            updateInstallVersion = install,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = DeviceEditUiState(),
    )

    /** Loads the [Repository] for the given [repositoryId] from the database. */
    fun loadRepository(repositoryId: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.value = repositoryDao.getRepositoryById(repositoryId)
    }

    /**
     * Renames the lantern: saved on the lantern itself (`id.name`), so every phone shows it, and any
     * name kept on this phone only is cleared. If the lantern can't be reached (or has a settings PIN), the
     * name is kept on this phone only, as before.
     */
    fun renameLantern(device: Device, name: String) = viewModelScope.launch(Dispatchers.IO) {
        val trimmed = name.trim()
        val tooLong = trimmed.toByteArray(Charsets.UTF_8).size > ProvisioningDefaults.DEVICE_NAME_MAX_BYTES
        if (trimmed.isEmpty() || tooLong) {
            _renameStatus.value = if (tooLong) RenameStatus.TooLong else RenameStatus.Empty
            return@launch
        }
        _renameStatus.value = RenameStatus.Saving
        val savedOnLantern = try {
            val body = ProvisioningPayloads.nameConfigJson(trimmed).toRequestBody(JSON)
            deviceApiFactory.create(device).postConfig(body).isSuccessful
        } catch (e: IOException) {
            Log.w(TAG, "Could not rename the lantern; keeping the name on this phone", e)
            false
        }
        // Start from the saved record: on a direct connection the device in use has a temporary address.
        val saved = deviceRepository.findDeviceByMacAddress(device.macAddress) ?: device
        val updatedDevice = if (savedOnLantern) {
            saved.copy(customName = "", originalName = trimmed)
        } else {
            saved.copy(customName = trimmed)
        }
        deviceRepository.update(updatedDevice)
        widgetManager.updateWidgetDeviceDetails(applicationContext, updatedDevice)
        _renameStatus.value = if (savedOnLantern) RenameStatus.SavedOnLantern else RenameStatus.SavedOnPhone
    }

    /** Toggles the hidden-from-list flag on [device]. */
    fun updateDeviceHidden(device: Device, isHidden: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "updateDeviceHidden: ${device.originalName}, isHidden: $isHidden")
        deviceRepository.update(device.copy(isHidden = isHidden))
    }

    /** Changes the update [branch] (stable / beta) for the [device]. */
    fun updateDeviceBranch(device: Device, branch: Branch) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "updateDeviceBranch: ${device.originalName}, updateChannel: $branch")
        deviceRepository.update(device.copy(branch = branch))
    }

    /** Fetches and shows the release details for a specific [version] tag. */
    fun showUpdateDetails(repositoryId: Long, version: String) = viewModelScope.launch(Dispatchers.IO) {
        updateDetailsVersion.value = versionWithAssetsRepository.getVersionByTag(repositoryId, version)
    }

    /** Dismisses the update-details dialog. */
    fun hideUpdateDetails() {
        updateDetailsVersion.value = null
    }

    /** Marks the given [version] as skipped for [device] and closes the dialog. */
    fun skipUpdate(device: Device, version: VersionWithAssets) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "Saving skipUpdateTag")
        deviceRepository.update(device.copy(skipUpdateTag = version.version.tagName))
        updateDetailsVersion.value = null
    }

    /** Shows the update disclaimer dialog for the given [version]. */
    fun showUpdateDisclaimer(version: VersionWithAssets) {
        updateDisclaimerVersion.value = version
    }

    /** Dismisses the update disclaimer dialog. */
    fun hideUpdateDisclaimer() {
        updateDisclaimerVersion.value = null
    }

    /** Begins the OTA install flow for [version]. */
    fun startUpdateInstall(version: VersionWithAssets) {
        updateInstallVersion.value = version
    }

    /**
     * Called when the OTA install dialog is dismissed.
     *
     * If [wasSuccessful], the device's in-memory [DeviceWithState.stateInfo]
     * is patched with the new version so the UI reflects it immediately
     * (before the next websocket refresh).
     */
    fun stopUpdateInstall(
        device: DeviceWithState? = null,
        version: VersionWithAssets? = null,
        wasSuccessful: Boolean = false,
    ) {
        updateInstallVersion.value = null
        if (wasSuccessful && device != null && version != null) {
            val currentState = device.stateInfo.value ?: return
            val updatedInfo = currentState.info.copy(
                version = version.version.tagName.removePrefix("v"),
            )
            device.stateInfo.value = currentState.copy(info = updatedInfo)
        }
    }

    /**
     * Clears any skipped-update tag on [device] and refreshes available
     * versions from GitHub.
     */
    fun checkForUpdates(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            isCheckingUpdates.value = true
            val updatedDevice = device.copy(skipUpdateTag = "")
            deviceRepository.update(updatedDevice)
            try {
                val repo = repositoryDao.getRepositoryById(device.repositoryId)
                val repoStr = repo?.ownerAndRepo ?: DEFAULT_REPO
                releaseService.refreshVersions(githubApi, setOf(repoStr))
            } finally {
                isCheckingUpdates.value = false
            }
        }
    }
}
