package com.actioncut.core.model

/**
 * Color/tone adjustments applied to a clip. All values are normalized so that 0f means
 * "no change", letting sliders default to centre. Ranges are clamped by the UI.
 *
 * @property brightness -1f..1f
 * @property contrast -1f..1f
 * @property saturation -1f..1f
 * @property warmth -1f..1f (color temperature)
 * @property sharpness 0f..1f
 * @property vignette 0f..1f
 */
data class ColorAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f,
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f &&
            warmth == 0f && sharpness == 0f && vignette == 0f

    companion object {
        val NONE = ColorAdjustments()
    }
}

/**
 * Normalized crop rectangle. All values in [0f, 1f] relative to source dimensions.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val isFull: Boolean get() = left == 0f && top == 0f && right == 1f && bottom == 1f
}

/**
 * 2D transform for overlays / text / stickers on the canvas.
 *
 * @property offsetX Normalized horizontal offset (-1f..1f, 0 = centre).
 * @property offsetY Normalized vertical offset (-1f..1f, 0 = centre).
 * @property scale Uniform scale (1f = fit).
 * @property rotationDegrees Free rotation.
 */
data class Transform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
)

/**
 * Audio fade envelope for a clip.
 */
data class AudioFade(
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
)

/** Common speed presets surfaced in the speed tool. */
object SpeedPresets {
    val values = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 4f)
    const val MIN = 0.1f
    const val MAX = 10f
}
