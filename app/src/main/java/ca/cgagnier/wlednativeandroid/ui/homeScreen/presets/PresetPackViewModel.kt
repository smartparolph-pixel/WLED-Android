package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.presets.PackInstallResult
import ca.cgagnier.wlednativeandroid.service.presets.PresetPack
import ca.cgagnier.wlednativeandroid.service.presets.PresetPackInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetPackViewModel @Inject constructor(private val installer: PresetPackInstaller) : ViewModel() {
    /** The pack being added, if any. */
    var working by mutableStateOf<PresetPack?>(null)
        private set
    var result by mutableStateOf<PackInstallResult?>(null)
        private set

    fun install(device: Device, pack: PresetPack) {
        if (working != null) return
        working = pack
        result = null
        viewModelScope.launch {
            result = installer.install(device, pack)
            working = null
        }
    }
}
