package com.actioncut.core.domain.editor

import com.actioncut.core.common.id.Ids
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.MediaItem
import com.actioncut.core.model.MediaType
import com.actioncut.core.model.TextAlignment
import com.actioncut.core.model.TextProperties

/** Builds [Clip]s from raw media / user actions with sensible defaults. */
object ClipFactory {

    /** How long a still image occupies the timeline by default. */
    const val DEFAULT_IMAGE_DURATION_MS = 3_000L

    /** Default duration for a freshly added text clip. */
    const val DEFAULT_TEXT_DURATION_MS = 2_000L

    fun fromMedia(media: MediaItem, timelineStartMs: Long): Clip {
        val type = when (media.type) {
            MediaType.VIDEO -> ClipType.VIDEO
            MediaType.IMAGE -> ClipType.IMAGE
            MediaType.AUDIO -> ClipType.AUDIO
        }
        val hasSource = media.type == MediaType.VIDEO || media.type == MediaType.AUDIO
        val duration = if (media.type == MediaType.IMAGE) {
            DEFAULT_IMAGE_DURATION_MS
        } else {
            media.durationMs.coerceAtLeast(TimelineEditor.MIN_CLIP_DURATION_MS)
        }
        return Clip(
            id = Ids.clip(),
            type = type,
            mediaUri = media.uri,
            timelineStartMs = timelineStartMs,
            timelineEndMs = timelineStartMs + duration,
            sourceInMs = 0L,
            sourceOutMs = if (hasSource) media.durationMs else 0L,
        )
    }

    fun text(content: String, timelineStartMs: Long): Clip = Clip(
        id = Ids.clip(),
        type = ClipType.TEXT,
        timelineStartMs = timelineStartMs,
        timelineEndMs = timelineStartMs + DEFAULT_TEXT_DURATION_MS,
        text = TextProperties(text = content, alignment = TextAlignment.CENTER),
    )

    fun sticker(uri: String, timelineStartMs: Long, durationMs: Long = DEFAULT_IMAGE_DURATION_MS): Clip =
        Clip(
            id = Ids.clip(),
            type = ClipType.STICKER,
            mediaUri = uri,
            timelineStartMs = timelineStartMs,
            timelineEndMs = timelineStartMs + durationMs,
        )
}
