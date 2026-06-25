package com.actioncut.core.media.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-device playback test. Plays a two-clip timeline through the actual [PlayerController]
 * /ExoPlayer and asserts the reported playhead advances *past the first clip* — i.e. it does
 * NOT reset to 0 at the clip boundary (the "jumps back to first / restarts" bug, caused by
 * treating ExoPlayer's per-item position as the absolute timeline position).
 */
@RunWith(AndroidJUnit4::class)
class PlaybackInstrumentedTest {

    private val instr get() = InstrumentationRegistry.getInstrumentation()
    private val appContext: Context get() = instr.targetContext

    private fun copyAsset(name: String): String {
        val out = File(appContext.cacheDir, name)
        instr.context.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
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

    @Test
    fun playheadAdvancesCumulativelyAcrossClipBoundary() {
        val a = copyAsset("clipA.mp4")
        val b = copyAsset("clipB.mp4")
        val da = durationMs(a)
        val db = durationMs(b)
        val timeline = Timeline(
            tracks = listOf(
                Track(
                    id = "v", type = TrackType.VIDEO,
                    clips = listOf(
                        Clip("a", ClipType.VIDEO, a, 0L, da, 0L, da),
                        Clip("b", ClipType.VIDEO, b, da, da + db, 0L, db),
                    ),
                ),
            ),
        )

        lateinit var pc: PlayerController
        instr.runOnMainSync {
            pc = PlayerController(appContext)
            pc.setTimeline(timeline)
            pc.play()
        }

        // Sample the absolute playhead for slightly longer than clip A.
        val samples = mutableListOf<Long>()
        val deadline = System.currentTimeMillis() + da + 3_000L
        while (System.currentTimeMillis() < deadline) {
            var pos = 0L
            instr.runOnMainSync { pos = pc.masterPositionMs() }
            samples.add(pos)
            Thread.sleep(100)
        }
        instr.runOnMainSync { pc.release() }

        val maxPos = samples.maxOrNull() ?: 0L
        assertTrue(
            "Playhead should advance past the first clip (da=$da ms) but peaked at $maxPos ms — " +
                "it is resetting at the clip boundary.",
            maxPos > da + 200L,
        )
    }
}
