package com.actioncut.core.model

import kotlinx.serialization.Serializable

/**
 * The layer/lane a [Track] occupies. CapCut-style editors stack multiple lanes:
 * a main video lane, overlay (picture-in-picture) lanes, text lanes and audio lanes.
 */
@Serializable
enum class TrackType {
    VIDEO,    // main video/image lane
    OVERLAY,  // picture-in-picture video/image/sticker
    TEXT,     // text captions/titles
    AUDIO,    // music / voiceover / sfx
}

/**
 * A single horizontal lane in the timeline holding a time-ordered list of [Clip]s.
 *
 * @property index Stacking/display order (0 = topmost video lane).
 * @property isMuted Audio muted (audio/video lanes).
 * @property isLocked Editing locked (no drag/trim).
 */
@Serializable
data class Track(
    val id: String,
    val type: TrackType,
    val clips: List<Clip> = emptyList(),
    val index: Int = 0,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
) {
    /** End time of the last clip on this track. */
    val durationMs: Long get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L

    val isEmpty: Boolean get() = clips.isEmpty()

    fun clipAt(timeMs: Long): Clip? =
        clips.firstOrNull { timeMs in it.timelineStartMs until it.timelineEndMs }
}

/**
 * The complete editable structure of a project: a set of stacked [Track]s plus the
 * current playhead position.
 */
@Serializable
data class Timeline(
    val tracks: List<Track> = emptyList(),
    val playheadMs: Long = 0L,
) {
    /** Longest track duration = total timeline length. */
    val durationMs: Long get() = tracks.maxOfOrNull { it.durationMs } ?: 0L

    val videoTracks: List<Track> get() = tracks.filter { it.type == TrackType.VIDEO }
    val audioTracks: List<Track> get() = tracks.filter { it.type == TrackType.AUDIO }

    fun track(id: String): Track? = tracks.firstOrNull { it.id == id }

    /** All clips across every track. */
    val allClips: List<Clip> get() = tracks.flatMap { it.clips }

    companion object {
        fun empty(): Timeline = Timeline()
    }
}
