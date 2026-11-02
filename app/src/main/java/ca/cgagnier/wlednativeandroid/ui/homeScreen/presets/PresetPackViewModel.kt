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
import ca.cgagnier.wlednativeandroid.service.presets.PresetPacks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetPackViewModel @Inject constructor(private val installer: PresetPackInstaller) : ViewModel() {
    /** The pack being added, if any. */
    var working by mutableStateOf<PresetPack?>(null)
        private set
    var addingAll by mutableStateOf(false)
        private set

    /** Result of the last single "Add" tap. */
    var result by mutableStateOf<PackInstallResult?>(null)
        private set

    /** Results of the last "Add all", in [PresetPacks.ALL] order. */
    var allResults by mutableStateOf<List<Pair<PresetPack, PackInstallResult>>?>(null)
        private set

    val busy: Boolean get() = working != null || addingAll

    fun install(device: Device, pack: PresetPack) {
        if (busy) return
        working = pack
        result = null
        allResults = null
        viewModelScope.launch {
            result = installer.install(device, pack)
            working = null
        }
    }

    /** Adds every pack, ending on [PresetPacks.PLAYS_AFTER_SETUP] so that one is left playing. */
    fun installAll(device: Device) {
        if (busy) return
        addingAll = true
        result = null
        allResults = null
        viewModelScope.launch {
            allResults = installer.installAll(device)
            addingAll = false
        }
    }
}
