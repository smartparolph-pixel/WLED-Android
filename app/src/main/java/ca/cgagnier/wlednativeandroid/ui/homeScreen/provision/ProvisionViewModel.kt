package ca.cgagnier.wlednativeandroid.ui.homeScreen.provision

import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.BuildConfig
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiCredentials
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiStore
import ca.cgagnier.wlednativeandroid.service.presets.PackSetupResult
import ca.cgagnier.wlednativeandroid.service.presets.PresetPackInstaller
import ca.cgagnier.wlednativeandroid.service.presets.PresetPacks
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningDefaults
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningException
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningProgress
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningRequest
import ca.cgagnier.wlednativeandroid.service.provisioning.WifiChoice
import ca.cgagnier.wlednativeandroid.service.provisioning.WifiScanner
import ca.cgagnier.wlednativeandroid.service.provisioning.WledProvisioningService
import ca.cgagnier.wlednativeandroid.service.timezone.TimezoneCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

private const val TAG = "ProvisionViewModel"

/** WPA2 passphrases are 8 to 63 characters; WifiNetworkSpecifier throws on anything else. */
private const val WPA2_MIN_LENGTH = 8
private const val WPA2_MAX_LENGTH = 63

sealed interface ProvisionStep {
    data class Form(
        val ssidError: Int? = null,
        val errorMessage: Int? = null,
        /** Debug builds only: the underlying error, for testers who cannot read Logcat. */
        val errorDetail: String? = null,
        val apSsidError: Int? = null,
        val apPasswordError: Int? = null,
        val nameError: Int? = null,
    ) : ProvisionStep
    data class Working(val progress: ProvisioningProgress) : ProvisionStep

    /**
     * @param addingPacks The playlists are still being added to the new device.
     * @param presets What adding the playlists did.
     */
    data class Success(
        val device: Device,
        val addingPacks: Boolean = true,
        val presets: PackSetupResult? = null,
        /** The lantern's own WiFi as set during setup. */
        val lanternWifi: LanternWifiCredentials? = null,
    ) : ProvisionStep
}

data class ProvisionState(
    /** The lantern's name, saved on it so every phone shows the same name. */
    val deviceName: String = ProvisioningDefaults.DEVICE_NAME,
    val homeSsid: String = "",
    val homePassword: String = "",
    /** Start of the device's setup network name. Factory-flashed products may use their own. */
    val apSsidPrefix: String = ProvisioningDefaults.AP_SSID_PREFIX,
    /** Setup network password. Empty means the setup network is open. */
    val apPassword: String = ProvisioningDefaults.AP_PASSWORD,
    val homeWifi: HomeWifiState = HomeWifiState(),
    val step: ProvisionStep = ProvisionStep.Form(),
)

/** Choosing the home WiFi: from a scan of nearby networks (default), or typed in. */
data class HomeWifiState(
    val manual: Boolean = false,
    val scanning: Boolean = false,
    /** A scan has finished at least once. */
    val scanned: Boolean = false,
    val networks: List<WifiChoice> = emptyList(),
    /** The network picked from the list; null until one is picked, and when typing it in. */
    val chosen: WifiChoice? = null,
    /** Why the list is empty (no permission, WiFi off, nothing found). */
    @StringRes val problem: Int? = null,
)

@HiltViewModel
class ProvisionViewModel @Inject constructor(
    private val provisioningService: WledProvisioningService,
    private val presetPackInstaller: PresetPackInstaller,
    lanternWifiStore: LanternWifiStore,
    private val wifiScanner: WifiScanner,
) : ViewModel() {
    /** The permissions to ask for before scanning for WiFi networks. */
    val wifiScanPermissions: Array<String> get() = wifiScanner.permissions

    fun canScanWifi(): Boolean = wifiScanner.hasPermission()

    /** Lists the WiFi networks around the phone. */
    fun scanWifi() {
        if (state.step !is ProvisionStep.Form || state.homeWifi.scanning) return
        val blocked = when {
            !wifiScanner.hasPermission() -> R.string.provision_wifi_permission
            !wifiScanner.isWifiOn -> R.string.provision_wifi_off
            !wifiScanner.isLocationOn -> R.string.provision_wifi_location_off
            else -> null
        }
        if (blocked != null) {
            updateWifi { it.copy(problem = blocked, scanned = true, networks = emptyList()) }
            return
        }
        updateWifi { it.copy(scanning = true, problem = null) }
        viewModelScope.launch {
            val networks = try {
                wifiScanner.scan()
            } catch (e: SecurityException) {
                Log.w(TAG, "WiFi scan not allowed", e)
                null
            }
            val problem = when {
                networks == null -> R.string.provision_wifi_permission
                networks.isNotEmpty() -> null
                else -> R.string.provision_wifi_none_found
            }
            updateWifi {
                it.copy(scanning = false, scanned = true, networks = networks.orEmpty(), problem = problem)
            }
        }
    }

    /** The answer to the scan permission request. */
    fun onWifiPermissionResult(granted: Boolean) {
        if (granted) scanWifi() else updateWifi { it.copy(problem = R.string.provision_wifi_permission) }
    }

    fun chooseWifi(choice: WifiChoice) {
        if (state.step !is ProvisionStep.Form || !choice.supported) return
        state = state.copy(
            homeSsid = choice.ssid,
            homePassword = if (choice.secured) state.homePassword else "",
            homeWifi = state.homeWifi.copy(chosen = choice),
        )
    }

    /** Back to the list to pick another network. */
    fun changeWifi() {
        if (state.step !is ProvisionStep.Form) return
        state = state.copy(homeSsid = "", homePassword = "", homeWifi = state.homeWifi.copy(chosen = null))
    }

    fun enterWifiManually() {
        if (state.step !is ProvisionStep.Form) return
        state = state.copy(homeWifi = state.homeWifi.copy(manual = true, chosen = null))
    }

    fun useWifiScan() {
        if (state.step !is ProvisionStep.Form) return
        state = state.copy(homeSsid = "", homeWifi = state.homeWifi.copy(manual = false, chosen = null))
        scanWifi()
    }

    private fun updateWifi(change: (HomeWifiState) -> HomeWifiState) {
        state = state.copy(homeWifi = change(state.homeWifi))
    }

    /** Lantern WiFis saved on this phone, offered as the setup network when setting a lantern up again. */
    val savedLanternWifis: List<LanternWifiCredentials> = lanternWifiStore.all.value.values.sortedBy { it.ssid }

    /** Sets the setup network to a lantern's own WiFi saved on this phone. */
    fun useLanternWifi(credentials: LanternWifiCredentials) {
        if (state.step is ProvisionStep.Form) {
            state = state.copy(apSsidPrefix = credentials.ssid, apPassword = credentials.password)
        }
    }

    var state by mutableStateOf(ProvisionState())
        private set
    private var job: Job? = null

    fun setDeviceName(value: String) {
        if (state.step is ProvisionStep.Form) state = state.copy(deviceName = value)
    }

    fun setSsid(value: String) {
        if (state.step is ProvisionStep.Form) state = state.copy(homeSsid = value)
    }

    fun setPassword(value: String) {
        if (state.step is ProvisionStep.Form) state = state.copy(homePassword = value)
    }

    fun setApSsidPrefix(value: String) {
        if (state.step is ProvisionStep.Form) state = state.copy(apSsidPrefix = value)
    }

    fun setApPassword(value: String) {
        if (state.step is ProvisionStep.Form) state = state.copy(apPassword = value)
    }

    fun start() {
        val formErrors = validate()
        if (formErrors != null) {
            state = state.copy(step = formErrors)
            return
        }
        val request = ProvisioningRequest(
            homeSsid = state.homeSsid.trim(),
            homePassword = state.homePassword,
            apSsidPrefix = state.apSsidPrefix.trim(),
            apPassword = state.apPassword.ifEmpty { null },
            timezone = TimezoneCatalog.settingFor(TimeZone.getDefault().id, System.currentTimeMillis()),
            deviceName = state.deviceName.trim(),
        )
        state = state.copy(step = ProvisionStep.Working(ProvisioningProgress.JoiningSetupNetwork))
        job = viewModelScope.launch {
            provisioningService.provision(request)
                .catch { e ->
                    Log.w(TAG, "Provisioning failed", e)
                    state = state.copy(
                        step = ProvisionStep.Form(
                            errorMessage = messageFor(e),
                            errorDetail = if (BuildConfig.DEBUG) debugDetail(e) else null,
                        ),
                    )
                }
                .collect { progress ->
                    state = state.copy(
                        step = when (progress) {
                            is ProvisioningProgress.Done ->
                                ProvisionStep.Success(progress.device, lanternWifi = progress.lanternWifi)

                            else -> ProvisionStep.Working(progress)
                        },
                    )
                    if (progress is ProvisioningProgress.Done) addPresetPacks(progress.device)
                }
        }
    }

    /** Returns the form with its errors, or null when everything is valid. */
    private fun validate(): ProvisionStep.Form? {
        val name = state.deviceName.trim()
        val form = ProvisionStep.Form(
            nameError = when {
                name.isEmpty() -> R.string.provision_error_name_required

                name.toByteArray(Charsets.UTF_8).size > ProvisioningDefaults.DEVICE_NAME_MAX_BYTES ->
                    R.string.provision_error_name_too_long

                else -> null
            },
            ssidError = when {
                state.homeSsid.isNotBlank() -> null
                state.homeWifi.manual -> R.string.provision_error_ssid_required
                else -> R.string.provision_error_choose_wifi
            },
            apSsidError = R.string.provision_error_ap_name_required.takeIf { state.apSsidPrefix.isBlank() },
            apPasswordError = R.string.provision_error_ap_password_length.takeIf {
                state.apPassword.isNotEmpty() && state.apPassword.length !in WPA2_MIN_LENGTH..WPA2_MAX_LENGTH
            },
        )
        val errors = listOf(form.nameError, form.ssidError, form.apSsidError, form.apPasswordError)
        return form.takeIf { errors.any { it != null } }
    }

    /** Preloads every preset pack on the new device, leaving [PresetPacks.PLAYS_AFTER_SETUP] playing. */
    private suspend fun addPresetPacks(device: Device) {
        val results = presetPackInstaller.ensureAll(device, play = PresetPacks.PLAYS_AFTER_SETUP)
        val step = state.step
        if (step is ProvisionStep.Success && step.device == device) {
            state = state.copy(step = step.copy(addingPacks = false, presets = results))
        }
    }

    fun clear() {
        job?.cancel()
        state = ProvisionState()
    }

    /** e.g. "ManualJoinRequired at WledProvisioningService.kt:84 · Android 14, API 34, join API found, …". */
    private fun debugDetail(e: Throwable): String {
        val origin = e.stackTrace.firstOrNull()?.let { " at ${it.fileName}:${it.lineNumber}" }.orEmpty()
        val cause = e.cause?.let { " (cause: ${it.javaClass.simpleName}: ${it.message})" }.orEmpty()
        val joinApi = runCatching { Class.forName("android.net.wifi.WifiNetworkSpecifier") }
            .fold({ "found" }, { it.javaClass.simpleName })
        return "${e.javaClass.simpleName}$origin$cause · Android ${Build.VERSION.RELEASE}, " +
            "API ${Build.VERSION.SDK_INT}, join API $joinApi, ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private fun messageFor(e: Throwable): Int = when (e) {
        is ProvisioningException.ManualJoinRequired -> R.string.provision_error_manual_join
        is ProvisioningException.SetupNetworkUnavailable -> R.string.provision_error_ap_unavailable
        is ProvisioningException.NotAWledDevice -> R.string.provision_error_not_wled
        is ProvisioningException.CredentialsRejected -> R.string.provision_error_rejected
        is ProvisioningException.DeviceDidNotReappear -> R.string.provision_error_not_found
        else -> R.string.provision_error_generic
    }
}
