package com.actioncut.core.media.reverse

import android.content.Context
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sqrt

/**
 * Proves [VideoReverser] genuinely reverses: the first frame of the reversed output must be
 * closer (in colour) to the *last* frame of the source than to the source's first frame.
 * The test asset `clip_audio.mp4` animates its colour over time, so this is decisive.
 */
@RunWith(AndroidJUnit4::class)
class VideoReverserTest {

    private val appContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

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
        } finally { r.release() }
    }

    private fun avgColor(uri: String, atMs: Long): FloatArray {
        val r = MediaMetadataRetriever()
        r.setDataSource(appContext, Uri.parse(uri))
        val bmp = r.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        r.release()
        requireNotNull(bmp)
        var rr = 0L; var gg = 0L; var bb = 0L; var n = 0L
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                rr += Color.red(c); gg += Color.green(c); bb += Color.blue(c); n++
                x += 16
            }
            y += 16
        }
        bmp.recycle()
        return floatArrayOf(rr.toFloat() / n, gg.toFloat() / n, bb.toFloat() / n)
    }

    private fun dist(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in 0..2) { val d = a[i] - b[i]; s += d * d }
        return sqrt(s)
    }

    @Test
    fun reversedFirstFrameMatchesSourceLastFrame() {
        val src = copyAsset("clip_audio.mp4")
        val dur = durationMs(src)

        val reversedUri = VideoReverser(appContext).reverse(src, 0L, dur)
        assertNotNull("reverse() returned null", reversedUri)

        val sourceFirst = avgColor(src, 0L)
        val sourceLast = avgColor(src, dur - 120L)
        val reversedFirst = avgColor(reversedUri!!, 0L)

        val toLast = dist(reversedFirst, sourceLast)
        val toFirst = dist(reversedFirst, sourceFirst)
        assertTrue(
            "Reversed first frame ($reversedFirst) should resemble source LAST ($sourceLast, d=$toLast) " +
                "more than source FIRST ($sourceFirst, d=$toFirst) — the clip isn't actually reversed.",
            toLast < toFirst,
        )
    }
}
