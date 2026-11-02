package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.presets.PackSetupResult
import ca.cgagnier.wlednativeandroid.service.presets.PresetPackInstaller
import ca.cgagnier.wlednativeandroid.service.presets.RepairResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetPackViewModel @Inject constructor(private val installer: PresetPackInstaller) : ViewModel() {
    /** Null while the playlists are being checked (and added if missing). */
    var setup by mutableStateOf<PackSetupResult?>(null)
        private set

    /** Id of the playlist being started, if any. */
    var starting by mutableStateOf<Int?>(null)
        private set
    var playFailed by mutableStateOf(false)
        private set

    var repairing by mutableStateOf(false)
        private set

    /** Outcome of the last repair, when it did not work. */
    var repairProblem by mutableStateOf<RepairResult?>(null)
        private set

    private var preparedFor: String? = null

    /** Rebuilds a damaged presets.json from its intact entries, then checks the playlists again. */
    fun repair(device: Device) {
        if (repairing) return
        repairing = true
        repairProblem = null
        viewModelScope.launch {
            val result = installer.repairPresets(device)
            repairing = false
            if (result == RepairResult.Repaired || result == RepairResult.NotNeeded) {
                retry(device)
            } else {
                repairProblem = result
            }
        }
    }

    /** Adds any missing playlists without asking; done once per device unless it failed. */
    fun prepare(device: Device) {
        if (preparedFor == device.macAddress && setup is PackSetupResult.Ready) return
        preparedFor = device.macAddress
        setup = null
        viewModelScope.launch { setup = installer.ensureAll(device) }
    }

    fun retry(device: Device) {
        preparedFor = null
        prepare(device)
    }

    /** Checks again, e.g. after the editor changed something (cheap when nothing changed). */
    fun refresh(device: Device) {
        viewModelScope.launch { installer.ensureAll(device).let { if (it is PackSetupResult.Ready) setup = it } }
    }

    fun play(device: Device, id: Int) {
        if (starting != null) return
        starting = id
        playFailed = false
        viewModelScope.launch {
            playFailed = !installer.play(device, id)
            starting = null
        }
    }
}
