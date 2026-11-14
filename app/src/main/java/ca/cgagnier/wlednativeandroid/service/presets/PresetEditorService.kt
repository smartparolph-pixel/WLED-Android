package ca.cgagnier.wlednativeandroid.service.presets

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

private const val TAG = "PresetEditorService"
private val JSON = "application/json".toMediaType()

/** Everything the preset editor shows for one device. */
data class PresetEditorData(
    val presets: DevicePresets,
    /** Effect names, index = effect id. */
    val effects: List<String>,
    /** Per effect id; missing entries mean the device did not say. */
    val controls: List<EffectControls>,
    val palettes: List<String>,
    /** The playlist the lantern starts when power comes back (`def.ps`); 0 means none. */
    val powerOnId: Int = 0,
) {
    fun effectName(id: Int?) = id?.let { effects.getOrNull(it) }

    fun controls(effectId: Int?) = effectId?.let { controls.getOrNull(it) } ?: EffectControls.parse("")
}

sealed interface PresetEditorLoad {
    data class Loaded(val data: PresetEditorData) : PresetEditorLoad

    data object Unreadable : PresetEditorLoad

    data object Unreachable : PresetEditorLoad
}

/** Reads and writes a device's presets and playlists for the editor, through WLED's JSON API. */
class PresetEditorService @Inject constructor(
    private val deviceApiFactory: DeviceApiFactory,
    private val installer: PresetPackInstaller,
) {
    private val moshi = Moshi.Builder().build()

    suspend fun load(device: Device): PresetEditorLoad = withContext(Dispatchers.IO) {
        val api = deviceApiFactory.create(device)
        try {
            val effects = api.getEffects().body() ?: return@withContext PresetEditorLoad.Unreachable
            val presets = installer.readPresets(api) ?: return@withContext PresetEditorLoad.Unreadable
            // Optional: without them the editor still works, with standard labels and no palette names.
            val controls = api.getEffectData().body().orEmpty().map { EffectControls.parse(it) }
            val palettes = api.getPalettes().body().orEmpty()
            val powerOn = api.getConfig().body()?.string()
                ?.let { runCatching { moshi.adapter(Map::class.java).fromJson(it) }.getOrNull() }
                ?.let { PowerOn.bootPreset(it) } ?: 0
            PresetEditorLoad.Loaded(
                PresetEditorData(DevicePresets.parse(presets), effects, controls, palettes, powerOn),
            )
        } catch (e: IOException) {
            Log.w(TAG, "Could not load presets", e)
            PresetEditorLoad.Unreachable
        }
    }

    /** Makes the lantern start playlist [id] when power comes back. */
    suspend fun setPowerOn(device: Device, id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            deviceApiFactory.create(device).postConfig(PowerOn.configJson(id).toRequestBody(JSON)).isSuccessful
        } catch (e: IOException) {
            Log.w(TAG, "Could not set the power-on playlist", e)
            false
        }
    }

    /** Sends one state call (save, delete or preview). */
    suspend fun post(device: Device, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            deviceApiFactory.create(device).postState(body.toRequestBody(JSON)).isSuccessful
        } catch (e: IOException) {
            Log.w(TAG, "Could not send to the device", e)
            false
        }
    }

    suspend fun reset(device: Device, pack: PresetPack): Boolean = installer.reset(device, pack)
}
