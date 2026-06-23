package com.actioncut.core.domain.editor

import com.actioncut.core.model.AudioFade
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.ColorAdjustments
import com.actioncut.core.model.Filter
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
import com.actioncut.core.model.Transform
import com.actioncut.core.model.Transition
import com.actioncut.core.model.VisualEffect
import com.actioncut.core.model.SpeedPresets
import java.util.UUID

/**
 * Pure, side-effect-free editing engine. Every operation takes an immutable [Timeline]
 * and returns a new one, which makes the whole thing trivially unit-testable and a
 * perfect fit for an undo/redo stack in the ViewModel.
 *
 * Two time spaces are reconciled here (see [Clip]):
 *  - **timeline** space (where the clip sits on the canvas)
 *  - **source** space (the trimmed window inside the original media)
 *
 * They relate through [Clip.speed]: `timelineDuration = sourceDuration / speed`.
 * [Clip.isReversed] flips the source mapping (high source value first).
 */
object TimelineEditor {

    /** Minimum length any clip may be trimmed/split to. */
    const val MIN_CLIP_DURATION_MS = 100L

    // ---------------------------------------------------------------------------------
    // Track operations
    // ---------------------------------------------------------------------------------

    /** Adds an empty track of [type]; returns the new timeline and the new track id. */
    fun addTrack(timeline: Timeline, type: TrackType): Pair<Timeline, String> {
        val id = "trk_${UUID.randomUUID().toString().take(8)}"
        val track = Track(id = id, type = type, index = timeline.tracks.size)
        return timeline.copy(tracks = timeline.tracks + track) to id
    }

    fun removeTrack(timeline: Timeline, trackId: String): Timeline =
        timeline.copy(tracks = timeline.tracks.filterNot { it.id == trackId })

    // ---------------------------------------------------------------------------------
    // Structural clip operations
    // ---------------------------------------------------------------------------------

    /**
     * Appends [clip] to the end of [trackId], preserving the clip's duration but moving
     * it to start exactly where the track currently ends (contiguous CapCut main lane).
     */
    fun appendClip(timeline: Timeline, trackId: String, clip: Clip): Timeline {
        val track = timeline.track(trackId) ?: return timeline
        val start = track.durationMs
        val placed = clip.copy(
            timelineStartMs = start,
            timelineEndMs = start + clip.timelineDurationMs,
        )
        return mapTrack(timeline, trackId) { it.copy(clips = it.clips + placed) }
    }

    /** Inserts [clip] at its own [Clip.timelineStartMs]; later clips ripple right. */
    fun insertClip(timeline: Timeline, trackId: String, clip: Clip): Timeline {
        return mapTrack(timeline, trackId) { track ->
            val shifted = track.clips.map { existing ->
                if (existing.timelineStartMs >= clip.timelineStartMs) {
                    existing.shiftedBy(clip.timelineDurationMs)
                } else {
                    existing
                }
            }
            track.copy(clips = (shifted + clip).sortedBy { it.timelineStartMs })
        }
    }

    /**
     * Removes [clipId]. When [ripple] is true, every later clip on the same track shifts
     * left to close the gap (default behaviour for the main lane).
     */
    fun removeClip(timeline: Timeline, clipId: String, ripple: Boolean = true): Timeline {
        val located = findClip(timeline, clipId) ?: return timeline
        val (track, clip) = located
        return mapTrack(timeline, track.id) { t ->
            val remaining = t.clips.filterNot { it.id == clipId }
            val result = if (ripple) {
                remaining.map { c ->
                    if (c.timelineStartMs >= clip.timelineEndMs) {
                        c.shiftedBy(-clip.timelineDurationMs)
                    } else {
                        c
                    }
                }
            } else {
                remaining
            }
            t.copy(clips = result)
        }
    }

    /**
     * Splits the clip containing [atTimelineMs] into two adjacent clips. No-op if the cut
     * falls within [MIN_CLIP_DURATION_MS] of either edge.
     */
    fun splitClip(timeline: Timeline, clipId: String, atTimelineMs: Long): Timeline {
        val located = findClip(timeline, clipId) ?: return timeline
        val (track, clip) = located
        if (atTimelineMs <= clip.timelineStartMs + MIN_CLIP_DURATION_MS) return timeline
        if (atTimelineMs >= clip.timelineEndMs - MIN_CLIP_DURATION_MS) return timeline

        val offsetTimeline = atTimelineMs - clip.timelineStartMs
        val sourceMid = sourceAt(clip, offsetTimeline)

        val first: Clip
        val second: Clip
        if (clip.hasSourceWindow) {
            if (clip.isReversed) {
                first = clip.copy(
                    id = newClipId(),
                    timelineEndMs = atTimelineMs,
                    sourceInMs = sourceMid,
                )
                second = clip.copy(
                    id = newClipId(),
                    timelineStartMs = atTimelineMs,
                    sourceOutMs = sourceMid,
                    transitionToNext = clip.transitionToNext,
                )
            } else {
                first = clip.copy(
                    id = newClipId(),
                    timelineEndMs = atTimelineMs,
                    sourceOutMs = sourceMid,
                    transitionToNext = null,
                )
                second = clip.copy(
                    id = newClipId(),
                    timelineStartMs = atTimelineMs,
                    sourceInMs = sourceMid,
                )
            }
        } else {
            // Image / text / sticker: no source window, just split the timeline span.
            first = clip.copy(id = newClipId(), timelineEndMs = atTimelineMs, transitionToNext = null)
            second = clip.copy(id = newClipId(), timelineStartMs = atTimelineMs)
        }

        return mapTrack(timeline, track.id) { t ->
            t.copy(clips = t.clips.flatMap { if (it.id == clipId) listOf(first, second) else listOf(it) })
        }
    }

    /** Drags the left edge to [newStartMs], adjusting the trimmed source window. */
    fun trimClipStart(timeline: Timeline, clipId: String, newStartMs: Long): Timeline {
        val (_, clip) = findClip(timeline, clipId) ?: return timeline
        val clamped = newStartMs.coerceIn(
            (clip.timelineStartMs - headRoom(clip)),
            clip.timelineEndMs - MIN_CLIP_DURATION_MS,
        )
        val delta = clamped - clip.timelineStartMs
        val sourceDelta = (delta * clip.speed).toLong()
        return updateClip(timeline, clipId) { c ->
            if (!c.hasSourceWindow) {
                c.copy(timelineStartMs = clamped)
            } else if (c.isReversed) {
                c.copy(timelineStartMs = clamped, sourceOutMs = c.sourceOutMs - sourceDelta)
            } else {
                c.copy(timelineStartMs = clamped, sourceInMs = c.sourceInMs + sourceDelta)
            }
        }
    }

    /** Drags the right edge to [newEndMs], adjusting the trimmed source window. */
    fun trimClipEnd(timeline: Timeline, clipId: String, newEndMs: Long): Timeline {
        val (_, clip) = findClip(timeline, clipId) ?: return timeline
        val clamped = newEndMs.coerceIn(
            clip.timelineStartMs + MIN_CLIP_DURATION_MS,
            clip.timelineEndMs + tailRoom(clip),
        )
        val delta = clip.timelineEndMs - clamped
        val sourceDelta = (delta * clip.speed).toLong()
        return updateClip(timeline, clipId) { c ->
            if (!c.hasSourceWindow) {
                c.copy(timelineEndMs = clamped)
            } else if (c.isReversed) {
                c.copy(timelineEndMs = clamped, sourceInMs = c.sourceInMs + sourceDelta)
            } else {
                c.copy(timelineEndMs = clamped, sourceOutMs = c.sourceOutMs - sourceDelta)
            }
        }
    }

    /** Moves a clip to [targetTrackId] starting at [newStartMs] (free positioning). */
    fun moveClip(
        timeline: Timeline,
        clipId: String,
        targetTrackId: String,
        newStartMs: Long,
    ): Timeline {
        val (sourceTrack, clip) = findClip(timeline, clipId) ?: return timeline
        if (timeline.track(targetTrackId) == null) return timeline
        val start = newStartMs.coerceAtLeast(0)
        val moved = clip.copy(
            timelineStartMs = start,
            timelineEndMs = start + clip.timelineDurationMs,
        )
        val withoutOriginal = mapTrack(timeline, sourceTrack.id) { t ->
            t.copy(clips = t.clips.filterNot { it.id == clipId })
        }
        return mapTrack(withoutOriginal, targetTrackId) { t ->
            t.copy(clips = (t.clips + moved).sortedBy { it.timelineStartMs })
        }
    }

    // ---------------------------------------------------------------------------------
    // Audio
    // ---------------------------------------------------------------------------------

    /**
     * Extracts (detaches) the audio of a video clip into a separate audio clip on an AUDIO
     * lane, aligned to the same timeline position, and mutes the original video clip so the
     * audio isn't doubled. CapCut/InShot-style "Extract audio".
     */
    fun detachAudio(timeline: Timeline, clipId: String): Timeline {
        val located = findClip(timeline, clipId) ?: return timeline
        val (_, clip) = located
        if (clip.type != ClipType.VIDEO || clip.mediaUri == null) return timeline

        val audioClip = Clip(
            id = newClipId(),
            type = ClipType.AUDIO,
            mediaUri = clip.mediaUri,
            timelineStartMs = clip.timelineStartMs,
            timelineEndMs = clip.timelineEndMs,
            sourceInMs = clip.sourceInMs,
            sourceOutMs = clip.sourceOutMs,
            speed = clip.speed,
            isReversed = clip.isReversed,
            volume = if (clip.volume <= 0f) 1f else clip.volume,
        )

        // Mute the original video clip's audio (it now lives on the audio lane).
        val muted = updateClip(timeline, clipId) { it.copy(volume = 0f) }

        val audioTrack = muted.tracks.firstOrNull { it.type == TrackType.AUDIO }
        return if (audioTrack != null) {
            mapTrack(muted, audioTrack.id) { t ->
                t.copy(clips = (t.clips + audioClip).sortedBy { it.timelineStartMs })
            }
        } else {
            val (withTrack, trackId) = addTrack(muted, TrackType.AUDIO)
            mapTrack(withTrack, trackId) { t -> t.copy(clips = t.clips + audioClip) }
        }
    }

    // ---------------------------------------------------------------------------------
    // Property updates
    // ---------------------------------------------------------------------------------

    /** Generic copy-on-write update for a single clip. */
    fun updateClip(timeline: Timeline, clipId: String, transform: (Clip) -> Clip): Timeline =
        timeline.copy(
            tracks = timeline.tracks.map { track ->
                if (track.clips.none { it.id == clipId }) {
                    track
                } else {
                    track.copy(clips = track.clips.map { if (it.id == clipId) transform(it) else it })
                }
            },
        )

    /**
     * Changes playback speed, recomputing the timeline length from the fixed source
     * window. Only applies to media with a real source timeline (video/audio).
     */
    fun setSpeed(timeline: Timeline, clipId: String, speed: Float): Timeline {
        val s = speed.coerceIn(SpeedPresets.MIN, SpeedPresets.MAX)
        return updateClip(timeline, clipId) { c ->
            if (!c.hasSourceWindow) {
                c.copy(speed = s)
            } else {
                val newDuration = (c.sourceDurationMs / s).toLong().coerceAtLeast(MIN_CLIP_DURATION_MS)
                c.copy(speed = s, timelineEndMs = c.timelineStartMs + newDuration)
            }
        }
    }

    fun setReversed(timeline: Timeline, clipId: String, reversed: Boolean): Timeline =
        updateClip(timeline, clipId) { it.copy(isReversed = reversed) }

    fun setVolume(timeline: Timeline, clipId: String, volume: Float): Timeline =
        updateClip(timeline, clipId) { it.copy(volume = volume.coerceIn(0f, 2f)) }

    fun setRotation(timeline: Timeline, clipId: String, degrees: Int): Timeline =
        updateClip(timeline, clipId) { it.copy(rotationDegrees = ((degrees % 360) + 360) % 360) }

    fun setOpacity(timeline: Timeline, clipId: String, opacity: Float): Timeline =
        updateClip(timeline, clipId) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }

    fun setAdjustments(timeline: Timeline, clipId: String, adjustments: ColorAdjustments): Timeline =
        updateClip(timeline, clipId) { it.copy(adjustments = adjustments) }

    fun setFilter(timeline: Timeline, clipId: String, filter: Filter?): Timeline =
        updateClip(timeline, clipId) { it.copy(filter = filter) }

    fun setTransform(timeline: Timeline, clipId: String, transform: Transform): Timeline =
        updateClip(timeline, clipId) { it.copy(transform = transform) }

    fun setAudioFade(timeline: Timeline, clipId: String, fade: AudioFade): Timeline =
        updateClip(timeline, clipId) { it.copy(audioFade = fade) }

    fun setTransition(timeline: Timeline, clipId: String, transition: Transition?): Timeline =
        updateClip(timeline, clipId) { it.copy(transitionToNext = transition) }

    fun addEffect(timeline: Timeline, clipId: String, effect: VisualEffect): Timeline =
        updateClip(timeline, clipId) { it.copy(effects = it.effects + effect) }

    fun removeEffect(timeline: Timeline, clipId: String, index: Int): Timeline =
        updateClip(timeline, clipId) { c ->
            if (index in c.effects.indices) c.copy(effects = c.effects.filterIndexed { i, _ -> i != index }) else c
        }

    // ---------------------------------------------------------------------------------
    // Lookup helpers
    // ---------------------------------------------------------------------------------

    /** Finds a clip and its owning track. */
    fun findClip(timeline: Timeline, clipId: String): Pair<Track, Clip>? {
        for (track in timeline.tracks) {
            val clip = track.clips.firstOrNull { it.id == clipId }
            if (clip != null) return track to clip
        }
        return null
    }

    // ---------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------

    private fun newClipId(): String = "clp_${UUID.randomUUID().toString().take(8)}"

    private fun mapTrack(timeline: Timeline, trackId: String, transform: (Track) -> Track): Timeline =
        timeline.copy(tracks = timeline.tracks.map { if (it.id == trackId) transform(it) else it })

    /** Source position for a timeline offset within a clip, honouring speed + reverse. */
    private fun sourceAt(clip: Clip, offsetTimelineMs: Long): Long {
        val sourceOffset = (offsetTimelineMs * clip.speed).toLong()
        return if (clip.isReversed) clip.sourceOutMs - sourceOffset else clip.sourceInMs + sourceOffset
    }

    /** How far the left edge can extend left (extra source available before sourceIn). */
    private fun headRoom(clip: Clip): Long {
        if (!clip.hasSourceWindow) return clip.timelineStartMs // images can slide to 0
        val availableSource = if (clip.isReversed) Long.MAX_VALUE else clip.sourceInMs
        return (availableSource / clip.speed).toLong().coerceAtMost(clip.timelineStartMs)
    }

    /** How far the right edge can extend right (extra trailing source after sourceOut). */
    private fun tailRoom(clip: Clip): Long {
        if (!clip.hasSourceWindow) return Long.MAX_VALUE / 2
        // Without knowing total media length we allow extension up to an extra source window.
        return (clip.sourceDurationMs / clip.speed).toLong()
    }

    private fun Clip.shiftedBy(deltaMs: Long): Clip =
        copy(timelineStartMs = timelineStartMs + deltaMs, timelineEndMs = timelineEndMs + deltaMs)

    private val Clip.hasSourceWindow: Boolean
        get() = type == ClipType.VIDEO || type == ClipType.AUDIO
}
