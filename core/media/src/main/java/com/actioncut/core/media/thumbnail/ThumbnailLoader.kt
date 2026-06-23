package com.actioncut.core.media.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.actioncut.core.common.coroutine.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts and caches still frames for the timeline filmstrip. Picker grid thumbnails are
 * handled by Coil; this exists for precise frame-at-time previews on the timeline.
 *
 * Performance: a memory [LruCache] (sized to ~1/8 of the heap) avoids re-decoding frames
 * while scrubbing, and extraction runs on the IO dispatcher with
 * [MediaMetadataRetriever.OPTION_CLOSEST_SYNC] for fast keyframe lookup.
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun frameAt(
        uri: String,
        timeMs: Long,
        targetHeightPx: Int = 120,
    ): Bitmap? = withContext(dispatchers.io) {
        val key = "$uri@$timeMs"
        cache.get(key)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        val bitmap = runCatching {
            retriever.setDataSource(context, Uri.parse(uri))
            val frame = retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
            frame?.let { scaleToHeight(it, targetHeightPx) }
        }.getOrNull()
        runCatching { retriever.release() }

        bitmap?.also { cache.put(key, it) }
    }

    fun clear() = cache.evictAll()

    private fun scaleToHeight(source: Bitmap, targetHeightPx: Int): Bitmap {
        if (source.height <= targetHeightPx) return source
        val ratio = targetHeightPx.toFloat() / source.height
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, targetHeightPx, true)
    }
}
