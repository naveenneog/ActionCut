package com.actioncut.core.media.export

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.ColorAdjustments
import com.actioncut.core.model.CropRect
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.Project
import com.actioncut.core.model.Resolution
import com.actioncut.core.model.SpeedRamp
import com.actioncut.core.model.TextProperties
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
import com.actioncut.core.model.Transform
import com.actioncut.core.model.Transition
import com.actioncut.core.model.TransitionType
import com.actioncut.core.model.VisualEffect
import com.actioncut.core.model.VisualEffectType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real on-device export test: runs the Media3 [Media3VideoExporter] over two actual MP4
 * clips and asserts it produces a playable file. This exercises the Transformer/MediaCodec
 * path that unit tests cannot, surfacing the real failure message when export breaks.
 */
@RunWith(AndroidJUnit4::class)
class ExportInstrumentedTest {

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun copyAsset(name: String): String {
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val out = File(appContext.cacheDir, name)
        testCtx.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(out).toString()
    }

    private fun durationMs(uri: String): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(appContext, Uri.parse(uri))
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2_000L
        } finally {
            r.release()
        }
    }

    private fun twoClipProject(): Project {
        val a = copyAsset("clipA.mp4")
        val b = copyAsset("clipB.mp4")
        val da = durationMs(a)
        val db = durationMs(b)
        val clipA = Clip(
            id = "a", type = ClipType.VIDEO, mediaUri = a,
            timelineStartMs = 0L, timelineEndMs = da, sourceInMs = 0L, sourceOutMs = da,
        )
        val clipB = Clip(
            id = "b", type = ClipType.VIDEO, mediaUri = b,
            timelineStartMs = da, timelineEndMs = da + db, sourceInMs = 0L, sourceOutMs = db,
        )
        return Project(
            id = "p", name = "ExportTest",
            timeline = Timeline(tracks = listOf(Track(id = "v", type = TrackType.VIDEO, clips = listOf(clipA, clipB)))),
        )
    }

    @Test
    fun exportsTwoClipTimelineToPlayableFile() = runBlocking {
        val exporter = Media3VideoExporter(appContext)
        val out = File(appContext.cacheDir, "export_out.mp4")
        if (out.exists()) out.delete()

        val terminal = withTimeout(120_000) {
            exporter.export(twoClipProject(), ExportSettings(resolution = Resolution.P480), out.absolutePath)
                .first { it is ExportState.Completed || it is ExportState.Failed }
        }

        if (terminal is ExportState.Failed) {
            throw AssertionError("Export failed: ${terminal.message}")
        }
        assertTrue("Export file should exist", out.exists())
        assertTrue("Export file should be non-empty", out.length() > 0L)
    }

    // --- Feature-specific export coverage (each pinpoints a possible export breaker) ---

    private fun runExport(project: Project, tag: String, settings: ExportSettings = ExportSettings(resolution = Resolution.P480)) = runBlocking {
        val out = File(appContext.cacheDir, "export_$tag.mp4")
        if (out.exists()) out.delete()
        val terminal = withTimeout(120_000) {
            Media3VideoExporter(appContext).export(project, settings, out.absolutePath)
                .first { it is ExportState.Completed || it is ExportState.Failed }
        }
        if (terminal is ExportState.Failed) throw AssertionError("[$tag] Export failed: ${terminal.message}")
        assertTrue("[$tag] export file missing", out.exists() && out.length() > 0L)
    }

    private fun baseClips(): Pair<Clip, Clip> {
        val a = copyAsset("clipA.mp4"); val b = copyAsset("clipB.mp4")
        val da = durationMs(a); val db = durationMs(b)
        return Clip("a", ClipType.VIDEO, a, 0L, da, 0L, da) to
            Clip("b", ClipType.VIDEO, b, da, da + db, 0L, db)
    }

    private fun projectOf(vararg tracks: Track) = Project(id = "p", name = "T", timeline = Timeline(tracks = tracks.toList()))

    // --- Mixed audio-track scenarios (the "track of type 1" export error) ---

    @Test
    fun exportsImageThenVideoWithAudio() {
        val image = Clip("img", ClipType.IMAGE, copyAsset("still.png"), 0L, 1_500L, 0L, 0L)
        val av = copyAsset("clip_audio.mp4"); val d = durationMs(av)
        val video = Clip("vid", ClipType.VIDEO, av, 1_500L, 1_500L + d, 0L, d)
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(image, video))), "img_then_av")
    }

    @Test
    fun exportsMutedVideoThenAudioVideo() {
        val av = copyAsset("clip_audio.mp4"); val d = durationMs(av)
        val muted = Clip("m", ClipType.VIDEO, av, 0L, d, 0L, d, volume = 0f)
        val loud = Clip("l", ClipType.VIDEO, av, d, 2 * d, 0L, d)
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(muted, loud))), "muted_then_av")
    }

    @Test
    fun exportsAudioVideoThenSilentVideo() {
        val av = copyAsset("clip_audio.mp4"); val d = durationMs(av)
        val silent = copyAsset("clipA.mp4"); val ds = durationMs(silent)
        val withAudio = Clip("a", ClipType.VIDEO, av, 0L, d, 0L, d)
        val noAudio = Clip("b", ClipType.VIDEO, silent, d, d + ds, 0L, ds)
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(withAudio, noAudio))), "av_then_silent")
    }

    @Test
    fun exportsWithVisualEffectShader() {
        val (a, b) = baseClips()
        val withEffect = a.copy(effects = listOf(VisualEffect(VisualEffectType.VHS, 0.8f)))
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(withEffect, b))), "vhs")
    }

    @Test
    fun exportsWithGlitchOnly() {
        val (a, b) = baseClips()
        val fx = a.copy(effects = listOf(VisualEffect(VisualEffectType.GLITCH, 0.7f)))
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(fx, b))), "glitch_only")
    }

    @Test
    fun exportsWithPixelateOnly() {
        val (a, b) = baseClips()
        val fx = a.copy(effects = listOf(VisualEffect(VisualEffectType.PIXELATE, 0.5f)))
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(fx, b))), "pixelate_only")
    }

    @Test
    fun exportsWithGlitchAndPixelate() {
        val (a, b) = baseClips()
        val fx = a.copy(effects = listOf(VisualEffect(VisualEffectType.GLITCH, 0.7f), VisualEffect(VisualEffectType.PIXELATE, 0.5f)))
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(fx, b))), "glitch")
    }

    @Test
    fun exportsWithTransition() {
        val (a, b) = baseClips()
        val withTr = a.copy(transitionToNext = Transition(TransitionType.FADE, 400L))
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(withTr, b))), "fade")
    }

    @Test
    fun exportsWithTextOverlay() {
        val (a, b) = baseClips()
        val text = Clip(
            id = "t", type = ClipType.TEXT, mediaUri = null,
            timelineStartMs = 0L, timelineEndMs = a.timelineEndMs,
            text = TextProperties(text = "Hello"),
        )
        runExport(
            projectOf(
                Track("v", TrackType.VIDEO, listOf(a, b)),
                Track("o", TrackType.OVERLAY, listOf(text)),
            ),
            "text",
        )
    }

    @Test
    fun exportsAtTargetResolution1080() {
        runExport(twoClipProject(), "1080p", ExportSettings(resolution = Resolution.P1080))
    }

    // --- Per-feature export coverage (find crashes vs. silent no-ops) ---

    @Test
    fun exportsWithSpeedHalfAndDouble() {
        val av = copyAsset("clip_audio.mp4"); val d = durationMs(av)
        val slow = Clip("s", ClipType.VIDEO, av, 0L, 2 * d, 0L, d, speed = 0.5f)
        val fast = Clip("f", ClipType.VIDEO, av, 2 * d, 2 * d + d / 2, 0L, d, speed = 2f)
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(slow, fast))), "speed")
    }

    @Test
    fun exportsWithSpeedRamp() {
        val av = copyAsset("clip_audio.mp4"); val d = durationMs(av)
        val ramp = Clip("r", ClipType.VIDEO, av, 0L, d, 0L, d, speedRamp = SpeedRamp.SLOW_MIDDLE)
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(ramp))), "speedramp")
    }

    @Test
    fun exportsWithPictureInPicture() {
        val (a, b) = baseClips()
        val pip = Clip("p", ClipType.VIDEO, copyAsset("clip_audio.mp4"), 0L, 1_500L, 0L, 1_500L,
            transform = Transform(0.4f, -0.4f, 0.4f))
        runExport(
            projectOf(Track("v", TrackType.VIDEO, listOf(a, b)), Track("o", TrackType.OVERLAY, listOf(pip))),
            "pip",
        )
    }

    @Test
    fun exportsWithMusicLane() {
        val (a, b) = baseClips()
        val music = Clip("mus", ClipType.AUDIO, copyAsset("clip_audio.mp4"), 0L, 2_000L, 0L, 2_000L)
        runExport(
            projectOf(Track("v", TrackType.VIDEO, listOf(a, b)), Track("aud", TrackType.AUDIO, listOf(music))),
            "music",
        )
    }

    @Test
    fun exportsRotatedClip() {
        val (a, b) = baseClips()
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(a.copy(rotationDegrees = 90), b))), "rotate")
    }

    @Test
    fun exportsCroppedClip() {
        val (a, b) = baseClips()
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(a.copy(crop = CropRect(0.1f, 0.1f, 0.9f, 0.9f)), b))), "crop")
    }

    @Test
    fun exportsWithColorAdjustments() {
        val (a, b) = baseClips()
        val adj = a.copy(adjustments = ColorAdjustments(brightness = 0.2f, contrast = 0.1f, saturation = 0.3f, warmth = 0.2f))
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(adj, b))), "adjust")
    }

    @Test
    fun exportsReversedClip() {
        val (a, b) = baseClips()
        runExport(projectOf(Track("v", TrackType.VIDEO, listOf(a.copy(isReversed = true), b))), "reverse")
    }

    @Test
    fun exportsEverythingCombined() {
        val av = copyAsset("clip_audio.mp4"); val d = durationMs(av)
        val image = Clip("img", ClipType.IMAGE, copyAsset("still.png"), 0L, 1_000L, 0L, 0L,
            transitionToNext = Transition(TransitionType.FADE, 300L))
        val video = Clip("v2", ClipType.VIDEO, av, 1_000L, 1_000L + d, 0L, d,
            effects = listOf(VisualEffect(VisualEffectType.VHS, 0.7f)),
            adjustments = ColorAdjustments(brightness = 0.1f))
        val music = Clip("m", ClipType.AUDIO, av, 0L, 2_000L, 0L, 2_000L)
        runExport(
            projectOf(
                Track("v", TrackType.VIDEO, listOf(image, video)),
                Track("a", TrackType.AUDIO, listOf(music)),
            ),
            "combined",
        )
    }
}
