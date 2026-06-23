package com.actioncut.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards persistence integrity: a fully-populated timeline must survive a JSON
 * encode/decode round-trip unchanged (this is exactly how projects are stored in Room).
 */
class TimelineSerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun timeline_survivesJsonRoundTrip() {
        val timeline = Timeline(
            tracks = listOf(
                Track(
                    id = "video",
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            id = "c1",
                            type = ClipType.VIDEO,
                            mediaUri = "content://video/1",
                            timelineStartMs = 0,
                            timelineEndMs = 4000,
                            sourceInMs = 0,
                            sourceOutMs = 4000,
                            speed = 2f,
                            isReversed = true,
                            volume = 0.5f,
                            filter = Filters.catalogue[3],
                            adjustments = ColorAdjustments(brightness = 0.2f, contrast = -0.1f),
                            effects = listOf(VisualEffect(VisualEffectType.GLITCH, intensity = 0.8f)),
                            transitionToNext = Transition(TransitionType.FADE, 500),
                        ),
                    ),
                ),
                Track(
                    id = "text",
                    type = TrackType.TEXT,
                    index = 1,
                    clips = listOf(
                        Clip(
                            id = "t1",
                            type = ClipType.TEXT,
                            timelineStartMs = 1000,
                            timelineEndMs = 3000,
                            text = TextProperties(text = "Hello", fontSizeSp = 28f, bold = true),
                        ),
                    ),
                ),
            ),
            playheadMs = 1500,
        )

        val encoded = json.encodeToString(timeline)
        val decoded = json.decodeFromString<Timeline>(encoded)

        assertEquals(timeline, decoded)
    }

    @Test
    fun project_survivesJsonRoundTrip() {
        val project = Project(
            id = "p1",
            name = "Demo",
            aspectRatio = AspectRatio.RATIO_16_9,
            timeline = Timeline(tracks = listOf(Track(id = "v", type = TrackType.VIDEO))),
            thumbnailUri = "content://thumb/1",
            createdAtEpochMs = 100,
            updatedAtEpochMs = 200,
        )
        val decoded = json.decodeFromString<Project>(json.encodeToString(project))
        assertEquals(project, decoded)
    }
}
