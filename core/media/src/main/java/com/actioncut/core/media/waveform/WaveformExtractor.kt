package com.actioncut.core.media.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import com.actioncut.core.common.coroutine.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces a normalized amplitude envelope for an audio/video URI so the timeline can draw
 * a waveform on audio clips. Decodes a subset of PCM via [MediaExtractor] + [MediaCodec]
 * (peak per time bucket), caches the result, and falls back to a deterministic synthesized
 * envelope if decoding isn't possible — so the UI always has something to render.
 */
@Singleton
class WaveformExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val cache = LruCache<String, FloatArray>(64)

    /** Returns [buckets] amplitudes in 0f..1f for [uri] (cached). */
    suspend fun amplitudes(uri: String, buckets: Int = 64): FloatArray =
        withContext(dispatchers.io) {
            cache.get(uri)?.let { return@withContext it }
            val result = runCatching { decode(uri, buckets) }.getOrNull()
                ?.takeIf { it.any { v -> v > 0f } }
                ?: synthesize(uri, buckets)
            cache.put(uri, result)
            result
        }

    private fun decode(uri: String, buckets: Int): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, Uri.parse(uri), null)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        if (trackIndex == null) {
            extractor.release()
            return FloatArray(0)
        }

        val format = extractor.getTrackFormat(trackIndex)
        extractor.selectTrack(trackIndex)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val peaks = FloatArray(buckets)
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        var maxPeak = 0f
        var iterations = 0

        while (!sawOutputEos && iterations++ < MAX_ITERATIONS) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                val outputBuffer = codec.getOutputBuffer(outIndex)
                if (outputBuffer != null && info.size > 0) {
                    val bucket = if (durationUs > 0) {
                        ((info.presentationTimeUs.toDouble() / durationUs) * (buckets - 1))
                            .toInt().coerceIn(0, buckets - 1)
                    } else {
                        0
                    }
                    outputBuffer.position(info.offset)
                    val shorts = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val count = shorts.remaining()
                    val step = max(1, count / 256) // subsample for speed
                    var localPeak = 0f
                    var i = 0
                    while (i < count) {
                        val v = abs(shorts.get(i).toInt()) / 32768f
                        if (v > localPeak) localPeak = v
                        i += step
                    }
                    if (localPeak > peaks[bucket]) peaks[bucket] = localPeak
                    if (localPeak > maxPeak) maxPeak = localPeak
                }
                codec.releaseOutputBuffer(outIndex, false)
            }
        }

        runCatching { codec.stop() }
        codec.release()
        extractor.release()

        val norm = if (maxPeak > 0f) maxPeak else 1f
        for (i in peaks.indices) peaks[i] = (peaks[i] / norm).coerceIn(0f, 1f)
        return peaks
    }

    /** Deterministic, natural-looking envelope used when real decoding is unavailable. */
    private fun synthesize(uri: String, buckets: Int): FloatArray {
        val seed = uri.hashCode()
        return FloatArray(buckets) { i ->
            val a = sin((i * 0.5f) + (seed % 7)) * 0.5f + 0.5f
            val b = sin((i * 0.13f) + (seed % 13)) * 0.5f + 0.5f
            (0.22f + 0.72f * (a * 0.6f + b * 0.4f)).coerceIn(0.08f, 1f)
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_ITERATIONS = 200_000
    }
}
