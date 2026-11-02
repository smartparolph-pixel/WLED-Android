package ca.cgagnier.wlednativeandroid.service.presets

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.api.DeviceApi
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PresetPackInstaller"
private val JSON = "application/json".toMediaType()
private const val HTTP_NOT_FOUND = 404
private const val HTTP_UNAUTHORIZED = 401

/** Gives the device time to write presets.json after a preset is saved (written right away). */
private const val PRESET_GAP_MS = 500L

/** Playlists are written in the device's main loop after the reply, so they get longer. */
private const val PLAYLIST_GAP_MS = 1000L

enum class RepairResult {
    Repaired,

    /** The file reads fine (or could not be fetched); nothing was uploaded. */
    NotNeeded,

    /** The device has a settings PIN that is not unlocked. */
    Locked,
    Failed,
}

sealed interface PackSetupResult {
    /**
     * @param playlistIds Preset id of each pack's playlist on the device.
     * @param custom The user's own playlists (made in the app or on WLED's web page).
     * @param created Number of presets and playlists saved now (0 when everything was already there).
     * @param unavailable Effects that could not be added (missing in the firmware or no free slot).
     * @param verified Everything was found in presets.json afterwards.
     */
    data class Ready(
        val playlistIds: Map<PresetPack, Int>,
        val custom: List<SavedPlaylist> = emptyList(),
        val created: Int,
        val unavailable: List<String>,
        val verified: Boolean,
    ) : PackSetupResult

    /** None of the packs' effects exist on the device, or it has no free preset slots. */
    data object NothingAvailable : PackSetupResult

    /**
     * The device answered but its presets.json is not valid JSON (damaged). Nothing is written then,
     * since saving into a file that cannot be read can lose every preset in it. [repairPresets] can
     * rebuild it from the [kept] entries that are intact, leaving out [dropped] damaged parts.
     *
     * @param problem Where the file stops being valid, for diagnosis.
     */
    data class PresetsUnreadable(val kept: Int, val dropped: Int, val problem: String) : PackSetupResult

    data object Unreachable : PackSetupResult
}

/**
 * Makes sure every [PresetPack] is on a device, without asking: missing presets and playlists are added,
 * those already there (matched by name) are left alone, edits included, so it is safe to run every time.
 */
@Singleton
class PresetPackInstaller @Inject constructor(private val deviceApiFactory: DeviceApiFactory) {
    private val moshi = Moshi.Builder().build()

    /** Last result per device, valid while the device's presets.json is unchanged (`info.fs.pmt`). */
    private val checked = ConcurrentHashMap<String, Pair<Int, PackSetupResult.Ready>>()

    /**
     * Saving a preset or playlist also applies it to the lights, so the lights change while anything is
     * added. Afterwards [play] is started if given, or [PresetPacks.PLAYS_AFTER_SETUP] if anything was
     * added, so the device never ends on a random effect.
     */
    suspend fun ensureAll(device: Device, play: PresetPack? = null): PackSetupResult = withContext(Dispatchers.IO) {
        val api = deviceApiFactory.create(device)
        try {
            val modified = presetsModified(api)
            val cached = checked[device.macAddress]?.takeIf { modified != null && it.first == modified }?.second
            val result = cached ?: ensureAll(api)
            val ready = result as? PackSetupResult.Ready
            val start = play ?: PresetPacks.PLAYS_AFTER_SETUP.takeIf { cached == null && ready?.created != 0 }
            start?.let { ready?.playlistIds?.get(it) }
                ?.let { api.postState(PresetPackPayloads.playJson(it).toRequestBody(JSON)) }
            if (cached == null && ready != null && ready.verified) {
                presetsModified(api)?.let { checked[device.macAddress] = it to ready.copy(created = 0) }
            }
            result
        } catch (e: IOException) {
            Log.w(TAG, "Could not set up preset packs", e)
            PackSetupResult.Unreachable
        }
    }

    /** Adds what is missing. Does not start anything; [ensureAll] for a device does. */
    suspend fun ensureAll(api: DeviceApi, packs: List<PresetPack> = PresetPacks.ALL): PackSetupResult {
        val effects = api.getEffects().body()
        val text = effects?.let { presetsText(api) }
        val presets = text?.let { parsePresets(it) }
        return when {
            effects == null || text == null -> PackSetupResult.Unreachable

            presets == null -> PresetsFileRepair.repair(text).let {
                PackSetupResult.PresetsUnreadable(it.kept, it.dropped, it.problem.orEmpty())
            }

            else -> install(api, PresetPackPlanner.planAll(packs, presets, effects), presets)
        }
    }

    /**
     * Replaces a damaged presets.json with the entries that are still intact ([PresetsFileRepair]),
     * uploading it the way WLED's own backup restore does. Nothing is changed if the file reads fine.
     */
    suspend fun repairPresets(device: Device): RepairResult = withContext(Dispatchers.IO) {
        val api = deviceApiFactory.create(device)
        try {
            val text = presetsText(api)
            if (text == null || parsePresets(text) != null) return@withContext RepairResult.NotNeeded
            val repaired = PresetsFileRepair.repair(text).toJson()
            val file = MultipartBody.Part.createFormData(
                "data",
                "/presets.json",
                repaired.toRequestBody(JSON),
            )
            val response = api.uploadFile(file)
            checked.remove(device.macAddress)
            when {
                response.code() == HTTP_UNAUTHORIZED -> RepairResult.Locked

                !response.isSuccessful -> RepairResult.Failed

                else -> {
                    delay(PLAYLIST_GAP_MS)
                    RepairResult.Repaired
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not repair presets.json", e)
            RepairResult.Failed
        }
    }

    private suspend fun install(
        api: DeviceApi,
        plans: List<Pair<PresetPack, PackPlan>>,
        presets: Map<*, *>,
    ): PackSetupResult = when {
        plans.all { it.second.playlist == PlaylistStep.Skip } -> PackSetupResult.NothingAvailable

        !save(api, plans) -> PackSetupResult.Unreachable

        else -> {
            val created = plans.sumOf { (_, plan) ->
                plan.created.size + if (plan.playlist is PlaylistStep.Save) 1 else 0
            }
            PackSetupResult.Ready(
                playlistIds = plans.mapNotNull { (pack, plan) -> plan.playlist.playlistId?.let { pack to it } }.toMap(),
                custom = DevicePresets.parse(presets).custom,
                created = created,
                unavailable = plans.flatMap { (_, plan) -> plan.unavailable.map { it.name } }.distinct(),
                verified = created == 0 || verify(api, plans),
            )
        }
    }

    /**
     * Puts a built-in playlist and its effects back as the app defines them, then plays it.
     * @return false if the device could not be reached or its presets.json could not be read.
     */
    suspend fun reset(device: Device, pack: PresetPack): Boolean = withContext(Dispatchers.IO) {
        val api = deviceApiFactory.create(device)
        try {
            val effects = api.getEffects().body() ?: return@withContext false
            val presets = readPresets(api) ?: return@withContext false
            val plan = PresetPackPlanner.reset(pack, presets, effects)
            save(api, listOf(pack to plan))
        } catch (e: IOException) {
            Log.w(TAG, "Could not reset ${pack.playlist.name}", e)
            false
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

    /**
     * Carries out the plans: every new preset first, then the playlists, which refer to them. Each save
     * gets time to be written, since back-to-back writes can damage presets.json.
     */
    private suspend fun save(api: DeviceApi, plans: List<Pair<PresetPack, PackPlan>>): Boolean {
        val presets = plans.flatMap { (_, plan) -> plan.created.map { PresetPackPayloads.presetJson(it) } }
        val playlists = plans.mapNotNull { (pack, plan) ->
            (plan.playlist as? PlaylistStep.Save)?.let { PresetPackPayloads.playlistJson(pack.playlist, it) }
        }
        return presets.all { post(api, it, PRESET_GAP_MS) } && playlists.all { post(api, it, PLAYLIST_GAP_MS) }
    }

    private suspend fun post(api: DeviceApi, body: String, gapMs: Long): Boolean {
        if (!api.postState(body.toRequestBody(JSON)).isSuccessful) return false
        delay(gapMs)
        return true
    }

    /**
     * Reads presets.json back once. Planning again without any effects can only reuse what is on the
     * device, so every playlist that was planned should now be found as is.
     */
    private suspend fun verify(api: DeviceApi, plans: List<Pair<PresetPack, PackPlan>>): Boolean {
        val presets = readPresets(api) ?: return false
        val after = PresetPackPlanner.planAll(plans.map { it.first }, presets, effects = emptyList())
        return plans.zip(after).all { (before, now) ->
            before.second.playlist == PlaylistStep.Skip || now.second.playlist is PlaylistStep.Play
        }
    }

    private suspend fun presetsModified(api: DeviceApi): Int? =
        api.getInfo().body()?.fileSystem?.presetLastModification?.takeIf { it > 0 }

    /**
     * The device's presets, or null if presets.json exists but cannot be parsed (an empty or cut-off file
     * included). A device that never had a preset has no presets.json, which counts as empty.
     */
    suspend fun readPresets(api: DeviceApi): Map<*, *>? = presetsText(api)?.let { parsePresets(it) }

    /** presets.json as text; `{}` when the device has none yet. Null only if the response was empty. */
    private suspend fun presetsText(api: DeviceApi): String? {
        val response = api.getPresets()
        if (response.code() == HTTP_NOT_FOUND) return "{}"
        if (!response.isSuccessful) throw IOException("presets.json: HTTP ${response.code()}")
        return response.body()?.string()
    }

    private fun parsePresets(body: String): Map<*, *>? = try {
        moshi.adapter(Map::class.java).fromJson(body)
    } catch (e: JsonDataException) {
        Log.w(TAG, "presets.json is not valid", e)
        null
    } catch (e: IOException) {
        // JsonEncodingException and EOFException (a cut-off file) are IOExceptions.
        Log.w(TAG, "presets.json is not valid", e)
        null
    }
}
