package ca.cgagnier.wlednativeandroid.ui.homeScreen.provision

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningDefaults
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningException
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningProgress
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningRequest
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
        val apSsidError: Int? = null,
        val apPasswordError: Int? = null,
    ) : ProvisionStep
    data class Working(val progress: ProvisioningProgress) : ProvisionStep
    data class Success(val device: Device) : ProvisionStep
}

data class ProvisionState(
    val homeSsid: String = "",
    val homePassword: String = "",
    /** Start of the device's setup network name. Factory-flashed products may use their own. */
    val apSsidPrefix: String = ProvisioningDefaults.AP_SSID_PREFIX,
    /** Setup network password. Empty means the setup network is open. */
    val apPassword: String = ProvisioningDefaults.AP_PASSWORD,
    val step: ProvisionStep = ProvisionStep.Form(),
)

@HiltViewModel
class ProvisionViewModel @Inject constructor(private val provisioningService: WledProvisioningService) : ViewModel() {

    var state by mutableStateOf(ProvisionState())
        private set
    private var job: Job? = null

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
        )
        state = state.copy(step = ProvisionStep.Working(ProvisioningProgress.JoiningSetupNetwork))
        job = viewModelScope.launch {
            provisioningService.provision(request)
                .catch { e ->
                    Log.w(TAG, "Provisioning failed", e)
                    state = state.copy(step = ProvisionStep.Form(errorMessage = messageFor(e)))
                }
                .collect { progress ->
                    state = state.copy(
                        step = when (progress) {
                            is ProvisioningProgress.Done -> ProvisionStep.Success(progress.device)
                            else -> ProvisionStep.Working(progress)
                        },
                    )
                }
        }
    }

    /** Returns the form with its errors, or null when everything is valid. */
    private fun validate(): ProvisionStep.Form? {
        val form = ProvisionStep.Form(
            ssidError = R.string.provision_error_ssid_required.takeIf { state.homeSsid.isBlank() },
            apSsidError = R.string.provision_error_ap_name_required.takeIf { state.apSsidPrefix.isBlank() },
            apPasswordError = R.string.provision_error_ap_password_length.takeIf {
                state.apPassword.isNotEmpty() && state.apPassword.length !in WPA2_MIN_LENGTH..WPA2_MAX_LENGTH
            },
        )
        return form.takeIf { it.ssidError != null || it.apSsidError != null || it.apPasswordError != null }
    }

    fun clear() {
        job?.cancel()
        state = ProvisionState()
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
