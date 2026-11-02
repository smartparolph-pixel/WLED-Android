package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.presets.PackSetupResult
import ca.cgagnier.wlednativeandroid.service.presets.PresetPackInstaller
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

    private var preparedFor: String? = null

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
