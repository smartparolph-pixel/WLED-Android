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

sealed interface PackSetupResult {
    /**
     * @param playlistIds Preset id of each pack's playlist on the device.
     * @param created Number of presets and playlists saved now (0 when everything was already there).
     * @param unavailable Effects that could not be added (missing in the firmware or no free slot).
     * @param verified Everything was found in presets.json afterwards.
     */
    data class Ready(
        val playlistIds: Map<PresetPack, Int>,
        val created: Int,
        val unavailable: List<String>,
        val verified: Boolean,
    ) : PackSetupResult

    /** None of the packs' effects exist on the device, or it has no free preset slots. */
    data object NothingAvailable : PackSetupResult

    data object Unreachable : PackSetupResult
}

/**
 * Makes sure every [PresetPack] is on a device, without asking: missing presets and playlists are added,
 * those already there (matched by name) are left alone, so it is safe to run every time.
 */
class PresetPackInstaller @Inject constructor(private val deviceApiFactory: DeviceApiFactory) {
    private val moshi = Moshi.Builder().build()

    suspend fun ensureAll(device: Device, play: PresetPack? = null): PackSetupResult =
        ensureAll(deviceApiFactory.create(device), play)

    /**
     * Saving a preset or playlist also applies it to the lights, so the lights change while anything is
     * added. Afterwards [play] is started if given, or [PresetPacks.PLAYS_AFTER_SETUP] if anything was
     * added, so the device never ends on a random effect.
     */
    suspend fun ensureAll(
        api: DeviceApi,
        play: PresetPack? = null,
        packs: List<PresetPack> = PresetPacks.ALL,
    ): PackSetupResult = withContext(Dispatchers.IO) {
        try {
            val effects = api.getEffects().body() ?: return@withContext PackSetupResult.Unreachable
            val plans = PresetPackPlanner.planAll(packs, readPresets(api), effects)
            if (plans.all { it.second.playlist == PlaylistStep.Skip }) {
                return@withContext PackSetupResult.NothingAvailable
            }
            if (!save(api, plans)) return@withContext PackSetupResult.Unreachable

            val ids = plans.mapNotNull { (pack, plan) -> plan.playlist.playlistId?.let { pack to it } }.toMap()
            val created = plans.sumOf { (_, plan) ->
                plan.created.size + if (plan.playlist is PlaylistStep.Save) 1 else 0
            }
            (play ?: PresetPacks.PLAYS_AFTER_SETUP.takeIf { created > 0 })
                ?.let { ids[it] }
                ?.let { api.postState(PresetPackPayloads.playJson(it).toRequestBody(JSON)) }

            PackSetupResult.Ready(
                playlistIds = ids,
                created = created,
                unavailable = plans.flatMap { (_, plan) -> plan.unavailable.map { it.name } }.distinct(),
                verified = created == 0 || verify(api, plans),
            )
        } catch (e: IOException) {
            Log.w(TAG, "Could not set up preset packs", e)
            PackSetupResult.Unreachable
        }
    }

    /** Starts a playlist (or preset) by id. */
    suspend fun play(device: Device, id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            deviceApiFactory.create(device)
                .postState(PresetPackPayloads.playJson(id).toRequestBody(JSON))
                .isSuccessful
        } catch (e: IOException) {
            Log.w(TAG, "Could not start preset $id", e)
            false
        }
    }

    /** Carries out the plans: new presets first, then each playlist. */
    private suspend fun save(api: DeviceApi, plans: List<Pair<PresetPack, PackPlan>>): Boolean {
        for ((pack, plan) in plans) {
            val bodies = plan.created.map { PresetPackPayloads.presetJson(it) } +
                listOfNotNull(
                    (plan.playlist as? PlaylistStep.Save)?.let {
                        PresetPackPayloads.playlistJson(pack.playlist, it)
                    },
                )
            for (body in bodies) {
                if (!api.postState(body.toRequestBody(JSON)).isSuccessful) return false
                delay(SAVE_GAP_MS)
            }
        }
        return true
    }

    /**
     * Reads presets.json back once. Planning again without any effects can only reuse what is on the
     * device, so every playlist that was planned should now be found as is.
     */
    private suspend fun verify(api: DeviceApi, plans: List<Pair<PresetPack, PackPlan>>): Boolean {
        val after = PresetPackPlanner.planAll(plans.map { it.first }, readPresets(api), effects = emptyList())
        return plans.zip(after).all { (before, now) ->
            before.second.playlist == PlaylistStep.Skip || now.second.playlist is PlaylistStep.Play
        }
    }

    private suspend fun readPresets(api: DeviceApi): Map<*, *> {
        val body = api.getPresets().body()?.string() ?: return emptyMap<Any, Any>()
        return runCatching { moshi.adapter(Map::class.java).fromJson(body) }.getOrNull().orEmpty()
    }
}
