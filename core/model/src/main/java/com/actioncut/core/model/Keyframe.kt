package com.actioncut.core.model

import kotlinx.serialization.Serializable

/**
 * A single animation keyframe capturing a clip's [Transform] + opacity at [timeMs]
 * (relative to the clip's start). Two or more keyframes animate the property over time.
 */
@Serializable
data class Keyframe(
    val timeMs: Long,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
)

/** Interpolated animation state at a point in time. */
data class AnimatedProps(
    val transform: Transform,
    val opacity: Float,
)

/** Pure keyframe interpolation, shared by the preview and the export render path. */
object Keyframes {

    /** Returns the interpolated [AnimatedProps] for [clip] at [timeInClipMs]. */
    fun propsAt(clip: Clip, timeInClipMs: Long): AnimatedProps {
        val kfs = clip.keyframes
        if (kfs.isEmpty()) return AnimatedProps(clip.transform, clip.opacity)
        val sorted = kfs.sortedBy { it.timeMs }
        if (timeInClipMs <= sorted.first().timeMs) return sorted.first().toProps()
        if (timeInClipMs >= sorted.last().timeMs) return sorted.last().toProps()
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (timeInClipMs in a.timeMs..b.timeMs) {
                val f = if (b.timeMs == a.timeMs) 0f
                else (timeInClipMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs)
                return AnimatedProps(
                    Transform(
                        offsetX = lerp(a.offsetX, b.offsetX, f),
                        offsetY = lerp(a.offsetY, b.offsetY, f),
                        scale = lerp(a.scale, b.scale, f),
                        rotationDegrees = lerp(a.rotationDegrees, b.rotationDegrees, f),
                    ),
                    opacity = lerp(a.opacity, b.opacity, f),
                )
            }
        }
        return sorted.last().toProps()
    }

    private fun Keyframe.toProps() =
        AnimatedProps(Transform(offsetX, offsetY, scale, rotationDegrees), opacity)

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
}
