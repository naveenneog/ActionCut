package com.actioncut.core.domain.editor

import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditorTest {

    private fun videoClip(
        id: String,
        start: Long,
        end: Long,
        sIn: Long = start,
        sOut: Long = end,
        speed: Float = 1f,
        reversed: Boolean = false,
    ) = Clip(
        id = id,
        type = ClipType.VIDEO,
        mediaUri = "uri://$id",
        timelineStartMs = start,
        timelineEndMs = end,
        sourceInMs = sIn,
        sourceOutMs = sOut,
        speed = speed,
        isReversed = reversed,
    )

    private fun timelineWith(vararg clips: Clip, trackId: String = "t1"): Timeline =
        Timeline(tracks = listOf(Track(id = trackId, type = TrackType.VIDEO, clips = clips.toList())))

    @Test
    fun appendClip_placesClipsContiguously() {
        var timeline = Timeline(tracks = listOf(Track(id = "t1", type = TrackType.VIDEO)))
        timeline = TimelineEditor.appendClip(timeline, "t1", videoClip("a", 0, 4000))
        timeline = TimelineEditor.appendClip(timeline, "t1", videoClip("b", 0, 2000))

        val clips = timeline.track("t1")!!.clips
        assertEquals(2, clips.size)
        assertEquals(0L, clips[0].timelineStartMs)
        assertEquals(4000L, clips[0].timelineEndMs)
        assertEquals(4000L, clips[1].timelineStartMs)
        assertEquals(6000L, clips[1].timelineEndMs)
    }

    @Test
    fun splitClip_dividesIntoTwoContiguousClips() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val result = TimelineEditor.splitClip(timeline, "a", 1500)

        val clips = result.track("t1")!!.clips
        assertEquals(2, clips.size)
        // Timeline spans are contiguous and cover the original range.
        assertEquals(0L, clips[0].timelineStartMs)
        assertEquals(1500L, clips[0].timelineEndMs)
        assertEquals(1500L, clips[1].timelineStartMs)
        assertEquals(4000L, clips[1].timelineEndMs)
        // Source windows split at the same point (speed 1.0).
        assertEquals(0L, clips[0].sourceInMs)
        assertEquals(1500L, clips[0].sourceOutMs)
        assertEquals(1500L, clips[1].sourceInMs)
        assertEquals(4000L, clips[1].sourceOutMs)
    }

    @Test
    fun splitClip_respectsSpeedWhenMappingSource() {
        // 2x speed: 4000ms source occupies 2000ms timeline.
        val timeline = timelineWith(videoClip("a", 0, 2000, sIn = 0, sOut = 4000, speed = 2f))
        val result = TimelineEditor.splitClip(timeline, "a", 1000)

        val clips = result.track("t1")!!.clips
        // Source split = timeline offset * speed = 1000 * 2 = 2000.
        assertEquals(2000L, clips[0].sourceOutMs)
        assertEquals(2000L, clips[1].sourceInMs)
    }

    @Test
    fun splitClip_isNoOpNearEdges() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val tooEarly = TimelineEditor.splitClip(timeline, "a", 50)
        val tooLate = TimelineEditor.splitClip(timeline, "a", 3990)
        assertEquals(1, tooEarly.track("t1")!!.clips.size)
        assertEquals(1, tooLate.track("t1")!!.clips.size)
    }

    @Test
    fun trimClipStart_adjustsSourceIn() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val result = TimelineEditor.trimClipStart(timeline, "a", 1000)

        val clip = result.track("t1")!!.clips.first()
        assertEquals(1000L, clip.timelineStartMs)
        assertEquals(4000L, clip.timelineEndMs)
        assertEquals(1000L, clip.sourceInMs)
        assertEquals(4000L, clip.sourceOutMs)
    }

    @Test
    fun trimClipEnd_adjustsSourceOut() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val result = TimelineEditor.trimClipEnd(timeline, "a", 3000)

        val clip = result.track("t1")!!.clips.first()
        assertEquals(0L, clip.timelineStartMs)
        assertEquals(3000L, clip.timelineEndMs)
        assertEquals(3000L, clip.sourceOutMs)
    }

    @Test
    fun setSpeed_recomputesTimelineDurationFromSource() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val faster = TimelineEditor.setSpeed(timeline, "a", 2f)
        val slower = TimelineEditor.setSpeed(timeline, "a", 0.5f)

        assertEquals(2000L, faster.track("t1")!!.clips.first().timelineDurationMs)
        assertEquals(8000L, slower.track("t1")!!.clips.first().timelineDurationMs)
    }

    @Test
    fun removeClip_withRipple_closesGap() {
        val timeline = timelineWith(videoClip("a", 0, 4000), videoClip("b", 4000, 6000))
        val result = TimelineEditor.removeClip(timeline, "a", ripple = true)

        val clips = result.track("t1")!!.clips
        assertEquals(1, clips.size)
        assertEquals("b", clips[0].id)
        assertEquals(0L, clips[0].timelineStartMs)
        assertEquals(2000L, clips[0].timelineEndMs)
    }

    @Test
    fun removeClip_withoutRipple_keepsPositions() {
        val timeline = timelineWith(videoClip("a", 0, 4000), videoClip("b", 4000, 6000))
        val result = TimelineEditor.removeClip(timeline, "a", ripple = false)

        val clips = result.track("t1")!!.clips
        assertEquals(1, clips.size)
        assertEquals(4000L, clips[0].timelineStartMs)
    }

    @Test
    fun moveClip_movesAcrossTracks() {
        val timeline = Timeline(
            tracks = listOf(
                Track(id = "t1", type = TrackType.VIDEO, clips = listOf(videoClip("a", 0, 4000))),
                Track(id = "t2", type = TrackType.OVERLAY, clips = emptyList()),
            ),
        )
        val result = TimelineEditor.moveClip(timeline, "a", "t2", 1000)

        assertTrue(result.track("t1")!!.clips.isEmpty())
        val moved = result.track("t2")!!.clips.first()
        assertEquals(1000L, moved.timelineStartMs)
        assertEquals(5000L, moved.timelineEndMs)
    }

    @Test
    fun setVolume_isClamped() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val result = TimelineEditor.setVolume(timeline, "a", 5f)
        assertEquals(2f, result.track("t1")!!.clips.first().volume)
    }

    @Test
    fun setVolume_zeroMutesClip() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val result = TimelineEditor.setVolume(timeline, "a", 0f)
        assertEquals(0f, result.track("t1")!!.clips.first().volume)
    }

    @Test
    fun findClip_returnsOwningTrackAndClip() {
        val timeline = timelineWith(videoClip("a", 0, 4000))
        val found = TimelineEditor.findClip(timeline, "a")
        assertNotNull(found)
        assertEquals("t1", found!!.first.id)
        assertEquals("a", found.second.id)
        assertNull(TimelineEditor.findClip(timeline, "missing"))
    }

    @Test
    fun splitClip_reversed_mapsSourceWindowsSymmetrically() {
        val timeline = timelineWith(videoClip("a", 0, 4000, sIn = 0, sOut = 4000, reversed = true))
        val result = TimelineEditor.splitClip(timeline, "a", 1500)

        val clips = result.track("t1")!!.clips
        assertEquals(2, clips.size)
        // Reversed: the first timeline segment plays the *tail* of the source.
        assertEquals(2500L, clips[0].sourceInMs)
        assertEquals(4000L, clips[0].sourceOutMs)
        assertEquals(0L, clips[1].sourceInMs)
        assertEquals(2500L, clips[1].sourceOutMs)
        // Timeline coverage stays contiguous.
        assertEquals(1500L, clips[0].timelineEndMs)
        assertEquals(1500L, clips[1].timelineStartMs)
    }
}
