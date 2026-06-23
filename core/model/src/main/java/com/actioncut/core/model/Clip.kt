package com.actioncut.core.model

/**
 * The kind of content a [Clip] holds. Determines which editing tools apply.
 */
enum class ClipType {
    VIDEO,
    IMAGE,
    AUDIO,
    TEXT,
    STICKER,
}

/**
 * A single placed segment on a [Track] — the atomic editable unit of the timeline.
 *
 * Two time spaces are tracked:
 *  - **Timeline space** ([timelineStartMs]..[timelineEndMs]) — where the clip sits on the canvas.
 *  - **Source space** ([sourceInMs]..[sourceOutMs]) — the trimmed window inside the original media.
 *
 * The two differ when [speed] != 1.0 (a 2s source window at 0.5x fills 4s of timeline).
 *
 * @property speed Playback rate; <1 = slow motion, >1 = fast motion.
 * @property isReversed Play the source backwards.
 * @property volume Per-clip gain 0f..2f (1f = original).
 * @property rotationDegrees 0/90/180/270 rotation.
 * @property opacity 0f..1f, used for overlays and fades.
 * @property crop Optional crop rectangle in normalized [0,1] coordinates.
 * @property transform Position/scale/rotation for overlays & text on the canvas.
 * @property adjustments Brightness/contrast/saturation etc.
 * @property filter Optional LUT-based color filter.
 * @property effects Stacked visual effects (glitch, blur, ...).
 * @property text Text payload when [type] == TEXT.
 * @property audioFade Fade-in/out envelope.
 * @property transitionToNext Transition blended into the following clip.
 */
data class Clip(
    val id: String,
    val type: ClipType,
    val mediaUri: String? = null,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val sourceInMs: Long = 0L,
    val sourceOutMs: Long = 0L,
    val speed: Float = 1f,
    val isReversed: Boolean = false,
    val volume: Float = 1f,
    val rotationDegrees: Int = 0,
    val opacity: Float = 1f,
    val crop: CropRect? = null,
    val transform: Transform = Transform(),
    val adjustments: ColorAdjustments = ColorAdjustments(),
    val filter: Filter? = null,
    val effects: List<VisualEffect> = emptyList(),
    val text: TextProperties? = null,
    val audioFade: AudioFade = AudioFade(),
    val transitionToNext: Transition? = null,
) {
    /** Length occupied on the timeline. */
    val timelineDurationMs: Long get() = (timelineEndMs - timelineStartMs).coerceAtLeast(0L)

    /** Length of the trimmed source window. */
    val sourceDurationMs: Long get() = (sourceOutMs - sourceInMs).coerceAtLeast(0L)

    val hasAudio: Boolean get() = type == ClipType.VIDEO || type == ClipType.AUDIO

    val isVisual: Boolean
        get() = type == ClipType.VIDEO || type == ClipType.IMAGE ||
            type == ClipType.TEXT || type == ClipType.STICKER

    fun contains(timeMs: Long): Boolean = timeMs in timelineStartMs until timelineEndMs
}
