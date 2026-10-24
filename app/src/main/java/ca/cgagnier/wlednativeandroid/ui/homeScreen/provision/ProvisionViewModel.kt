package ca.cgagnier.wlednativeandroid.ui.homeScreen.provision

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningException
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningProgress
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningRequest
import ca.cgagnier.wlednativeandroid.service.provisioning.WledProvisioningService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ProvisionViewModel"

sealed interface ProvisionStep {
    data class Form(val ssidError: Int? = null, val errorMessage: Int? = null) : ProvisionStep
    data class Working(val progress: ProvisioningProgress) : ProvisionStep
    data class Success(val device: Device) : ProvisionStep
}

data class ProvisionState(
    val homeSsid: String = "",
    val homePassword: String = "",
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

    fun start() {
        if (state.homeSsid.isBlank()) {
            state = state.copy(step = ProvisionStep.Form(ssidError = R.string.provision_error_ssid_required))
            return
        }
        val request = ProvisioningRequest(state.homeSsid.trim(), state.homePassword)
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
