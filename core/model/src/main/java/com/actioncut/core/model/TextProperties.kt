package com.actioncut.core.model

/** Horizontal alignment for text overlays. */
enum class TextAlignment { LEFT, CENTER, RIGHT }

/** Entrance/exit animation styles for text. */
enum class TextAnimation(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    TYPEWRITER("Typewriter"),
    POP("Pop"),
    SLIDE_UP("Slide Up"),
    BOUNCE("Bounce"),
    WAVE("Wave"),
}

/**
 * Payload for a TEXT clip.
 *
 * Colors are stored as ARGB [Int] so the model stays free of Android `Color`.
 *
 * @property text The string to render.
 * @property fontFamily Font key (resolved to a packaged font in the UI layer).
 * @property fontSizeSp Size in sp.
 * @property colorArgb Fill color (ARGB).
 * @property backgroundArgb Optional pill/background color (ARGB), 0 = transparent.
 * @property alignment Horizontal alignment.
 * @property bold / italic / underline Style flags.
 * @property letterSpacing Extra tracking in em.
 * @property strokeWidth Outline width in px (0 = none).
 * @property strokeArgb Outline color (ARGB).
 * @property shadow Whether a drop shadow is drawn.
 * @property inAnimation Entrance animation.
 * @property outAnimation Exit animation.
 */
data class TextProperties(
    val text: String,
    val fontFamily: String = "default",
    val fontSizeSp: Float = 24f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundArgb: Int = 0,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val letterSpacing: Float = 0f,
    val strokeWidth: Float = 0f,
    val strokeArgb: Int = 0xFF000000.toInt(),
    val shadow: Boolean = false,
    val inAnimation: TextAnimation = TextAnimation.NONE,
    val outAnimation: TextAnimation = TextAnimation.NONE,
)

/** Available font keys; mapped to packaged fonts in the design system. */
object Fonts {
    val keys = listOf("default", "poppins", "montserrat", "roboto_mono", "playfair", "bebas")
}
