package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.presets.PackSetupResult
import ca.cgagnier.wlednativeandroid.service.presets.PresetPack
import ca.cgagnier.wlednativeandroid.service.presets.PresetPackInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetPackViewModel @Inject constructor(private val installer: PresetPackInstaller) : ViewModel() {
    /** Null while the playlists are being checked (and added if missing). */
    var setup by mutableStateOf<PackSetupResult?>(null)
        private set

    /** The playlist being started, if any. */
    var starting by mutableStateOf<PresetPack?>(null)
        private set
    var playFailed by mutableStateOf(false)
        private set

    private var preparedFor: String? = null

    /** Adds any missing playlists without asking; done once per device unless it failed. */
    fun prepare(device: Device) {
        if (preparedFor == device.macAddress && setup != null && setup != PackSetupResult.Unreachable) return
        preparedFor = device.macAddress
        setup = null
        viewModelScope.launch { setup = installer.ensureAll(device) }
    }

    fun retry(device: Device) {
        preparedFor = null
        prepare(device)
    }

    fun play(device: Device, pack: PresetPack) {
        val id = (setup as? PackSetupResult.Ready)?.playlistIds?.get(pack) ?: return
        if (starting != null) return
        starting = pack
        playFailed = false
        viewModelScope.launch {
            playFailed = !installer.play(device, id)
            starting = null
        }
    }
}
