package com.actioncut.core.model

import kotlinx.serialization.Serializable

/** How a clip fills the project canvas. */
@Serializable
enum class FitMode(val displayName: String) {
    FIT("Fit"),       // whole frame visible (letterboxed)
    FILL("Fill"),     // crop to fill the canvas
    STRETCH("Stretch"), // distort to fill
}

/**
 * Project-level canvas appearance: how media fits, and the background shown behind it
 * (e.g. the letterbox area in [FitMode.FIT]).
 */
@Serializable
data class CanvasSettings(
    val fitMode: FitMode = FitMode.FILL,
    val backgroundColorArgb: Int = 0xFF000000.toInt(),
)

/** Background colour swatches offered in the Canvas panel (ARGB). */
object CanvasColors {
    val swatches: List<Int> = listOf(
        0xFF000000, 0xFFFFFFFF, 0xFF0A0A0B, 0xFF7C5CFF,
        0xFF00E5C0, 0xFFFF5C8A, 0xFFFFB02E, 0xFF13547A,
    ).map { it.toInt() }
}
