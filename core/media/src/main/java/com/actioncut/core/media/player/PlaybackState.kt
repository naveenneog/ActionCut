package com.actioncut.core.media.player

/** Immutable snapshot of the preview player, surfaced to the editor as StateFlow. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isEnded: Boolean = false,
)
