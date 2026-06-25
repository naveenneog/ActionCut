package com.actioncut.core.media.export

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.Project
import com.actioncut.core.model.Resolution
import com.actioncut.core.model.TextProperties
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
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
}
