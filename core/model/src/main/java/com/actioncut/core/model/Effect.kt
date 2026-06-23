package com.actioncut.core.model

import kotlinx.serialization.Serializable

/** Catalogue of real-time visual effects that can be stacked on a clip. */
@Serializable
enum class VisualEffectType(val displayName: String, val category: EffectCategory) {
    GLITCH("Glitch", EffectCategory.STYLIZE),
    RGB_SPLIT("RGB Split", EffectCategory.STYLIZE),
    SHAKE("Shake", EffectCategory.MOTION),
    ZOOM_PULSE("Zoom Pulse", EffectCategory.MOTION),
    GAUSSIAN_BLUR("Blur", EffectCategory.BLUR),
    RADIAL_BLUR("Radial Blur", EffectCategory.BLUR),
    BOKEH("Bokeh", EffectCategory.BLUR),
    FILM_GRAIN("Film Grain", EffectCategory.CINEMATIC),
    LIGHT_LEAK("Light Leak", EffectCategory.CINEMATIC),
    VHS("VHS", EffectCategory.RETRO),
    PIXELATE("Pixelate", EffectCategory.RETRO),
}

@Serializable
enum class EffectCategory {
    STYLIZE,
    MOTION,
    BLUR,
    CINEMATIC,
    RETRO,
}

/**
 * An applied visual effect instance with an intensity and optional time window relative
 * to the clip start (null window = whole clip).
 *
 * @property intensity 0f..1f effect strength.
 * @property startInClipMs Optional effect-on time relative to clip start.
 * @property endInClipMs Optional effect-off time relative to clip start.
 */
@Serializable
data class VisualEffect(
    val type: VisualEffectType,
    val intensity: Float = 1f,
    val startInClipMs: Long? = null,
    val endInClipMs: Long? = null,
)
