package ca.cgagnier.wlednativeandroid.service.presets

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.api.DeviceApi
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

private const val TAG = "PresetPackInstaller"
private val JSON = "application/json".toMediaType()

/** Gives the device time to write presets.json between saves; back-to-back writes can corrupt it. */
private const val SAVE_GAP_MS = 500L

sealed interface PackInstallResult {
    /**
     * @param created Names of the presets added now.
     * @param unavailable Names that could not be added (effect missing or no free slot).
     * @param verified The presets and playlist were found on the device afterwards.
     */
    data class Done(
        val playlistName: String,
        val created: List<String>,
        val unavailable: List<String>,
        val verified: Boolean,
    ) : PackInstallResult

    /** None of the pack's effects exist on the device, or it has no free preset slots. */
    data object NothingAvailable : PackInstallResult

    data object Unreachable : PackInstallResult
}

/**
 * Adds a [PresetPack] to a device and starts its playlist. Safe to run again: presets and playlists
 * already on the device (matched by name) are reused, so nothing is duplicated.
 */
class PresetPackInstaller @Inject constructor(private val deviceApiFactory: DeviceApiFactory) {
    private val moshi = Moshi.Builder().build()

    suspend fun install(device: Device, pack: PresetPack): PackInstallResult =
        install(deviceApiFactory.create(device), pack)

    suspend fun install(api: DeviceApi, pack: PresetPack): PackInstallResult = withContext(Dispatchers.IO) {
        try {
            val effects = api.getEffects().body() ?: return@withContext PackInstallResult.Unreachable
            val plan = PresetPackPlanner.plan(pack, readPresets(api), effects)
            if (plan.playlist == PlaylistStep.Skip) return@withContext PackInstallResult.NothingAvailable

            plan.created.forEach { step ->
                if (!api.postState(PresetPackPayloads.presetJson(step).toRequestBody(JSON)).isSuccessful) {
                    return@withContext PackInstallResult.Unreachable
                }
                delay(SAVE_GAP_MS)
            }
            val playBody = when (val playlist = plan.playlist) {
                is PlaylistStep.Save -> PresetPackPayloads.playlistJson(pack.playlist, playlist)
                is PlaylistStep.Play -> PresetPackPayloads.playJson(playlist.id)
                PlaylistStep.Skip -> null
            }
            playBody?.let { api.postState(it.toRequestBody(JSON)) }
            delay(SAVE_GAP_MS)

            PackInstallResult.Done(
                playlistName = pack.playlist.name,
                created = plan.created.map { it.name },
                unavailable = plan.unavailable.map { it.name },
                verified = verify(api, plan, pack),
            )
        } catch (e: IOException) {
            Log.w(TAG, "Could not add preset pack", e)
            PackInstallResult.Unreachable
        }
    }

    /**
     * Reads presets.json back. Planning again without any effects can only reuse what is on the device,
     * so every preset should come back as reused and the playlist as "just play it".
     */
    private suspend fun verify(api: DeviceApi, plan: PackPlan, pack: PresetPack): Boolean {
        val after = PresetPackPlanner.plan(pack, readPresets(api), effects = emptyList())
        val expected = (plan.created + plan.reused).map { it.name }.toSet()
        return after.reused.map { it.name }.toSet() == expected && after.playlist is PlaylistStep.Play
    }

    private suspend fun readPresets(api: DeviceApi): Map<*, *> {
        val body = api.getPresets().body()?.string() ?: return emptyMap<Any, Any>()
        return runCatching { moshi.adapter(Map::class.java).fromJson(body) }.getOrNull().orEmpty()
    }
}
