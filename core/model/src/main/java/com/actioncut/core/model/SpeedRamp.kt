package com.actioncut.core.model

import kotlinx.serialization.Serializable
import kotlin.math.sin

/**
 * A variable-speed (speed-ramp) preset applied across a clip's duration. Unlike the
 * constant [Clip.speed], a ramp changes the multiplier over the clip's progress to
 * create cinematic slow-mo / fast-mo effects.
 *
 * Best-effort on export (video lane); audio is sped uniformly so long ramps may drift.
 */
@Serializable
enum class SpeedRamp(val label: String) {
    NONE("Off"),
    SLOW_IN("Slow → Fast"),
    SLOW_OUT("Fast → Slow"),
    SLOW_MIDDLE("Bullet time"),
    MONTAGE("Montage 2x"),
}

/** Pure speed-curve math, shared by the preview and the export render path. */
object SpeedRamps {

    /**
     * Speed multiplier at normalized [progress] (0..1) through the clip. [base] is the
     * clip's constant [Clip.speed], so a ramp modulates around the user's chosen speed.
     */
    fun multiplierAt(ramp: SpeedRamp, progress: Float, base: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val m = when (ramp) {
            SpeedRamp.NONE -> base
            SpeedRamp.SLOW_IN -> lerp(0.3f, 1.6f, p) * base
            SpeedRamp.SLOW_OUT -> lerp(1.6f, 0.3f, p) * base
            SpeedRamp.SLOW_MIDDLE -> {
                // Fast at the ends, slow in the middle (classic "bullet time").
                val ends = 1f - sin(p * Math.PI).toFloat()
                lerp(0.3f, 2f, ends) * base
            }
            SpeedRamp.MONTAGE -> 2f * base
        }
        return m.coerceIn(0.1f, 8f)
    }

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
}
