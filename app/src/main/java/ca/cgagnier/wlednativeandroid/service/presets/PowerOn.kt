package ca.cgagnier.wlednativeandroid.service.presets

import com.squareup.moshi.Moshi

/**
 * What the lantern does when power comes back (plugged in, after a power cut): WLED's "Apply preset at boot"
 * (`def.ps` in cfg.json), "Turn LEDs on after power up" (`def.on`) and its brightness (`def.bri`).
 * Turning the lights off and on from the app is different: a playlist pauses while off and carries on.
 */
object PowerOn {
    const val FULL_BRIGHTNESS = 255

    private val moshi: Moshi = Moshi.Builder().build()

    /** The preset or playlist the lantern starts at power-up; 0 means none (a static light). */
    fun bootPreset(cfg: Map<*, *>): Int = ((cfg["def"] as? Map<*, *>)?.get("ps") as? Number)?.toInt() ?: 0

    /**
     * The power-on playlist to set, or null to leave the lantern as it is: [defaultId] (🎆 Sky Full of Sparks)
     * when none is set yet, or when the one set no longer exists (e.g. the user deleted their playlist).
     */
    fun presetToSet(current: Int, existingIds: Set<Int>, defaultId: Int?): Int? = when {
        defaultId == null -> null
        current == 0 || current !in existingIds -> defaultId
        else -> null
    }

    /**
     * Body for `POST /json/cfg`: start [presetId] at power-up with the lights on, at [brightness] if given
     * (left unchanged otherwise). Only `def` is sent, so nothing else in the config changes.
     */
    fun configJson(presetId: Int, brightness: Int? = null): String {
        val def = mutableMapOf<String, Any>("ps" to presetId, "on" to true)
        if (brightness != null) def["bri"] = brightness
        return moshi.adapter(Any::class.java).toJson(mapOf("def" to def))
    }
}
