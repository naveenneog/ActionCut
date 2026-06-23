package com.actioncut.core.model

/** Available transition styles blended between two adjacent clips. */
enum class TransitionType(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    DISSOLVE("Dissolve"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    SLIDE_UP("Slide Up"),
    SLIDE_DOWN("Slide Down"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    WIPE("Wipe"),
    BLUR("Blur"),
}

/**
 * A transition placed at the boundary between a clip and the next one on the same track.
 *
 * @property durationMs How long the blend lasts (overlaps both clips by half).
 */
data class Transition(
    val type: TransitionType,
    val durationMs: Long = 500L,
) {
    companion object {
        val NONE = Transition(TransitionType.NONE, 0L)
        val DEFAULT_DURATION_MS = 500L
    }
}
