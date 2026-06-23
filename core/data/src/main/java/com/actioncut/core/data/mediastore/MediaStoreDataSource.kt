package com.actioncut.core.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Build
import android.provider.MediaStore
import com.actioncut.core.domain.repository.MediaFilter
import com.actioncut.core.model.MediaItem
import com.actioncut.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads device media through [MediaStore] (scoped-storage friendly — no direct file
 * paths). Exposes a cold [Flow] that re-queries whenever the underlying collections
 * change, so the picker stays live as the user shoots new footage.
 *
 * The caller is responsible for holding the relevant `READ_MEDIA_*` runtime permission;
 * without it the query simply yields an empty list rather than crashing.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun observeMedia(filter: MediaFilter): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(query(filter))
            }
        }
        // Watch every collection we might read.
        listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        ).forEach { resolver.registerContentObserver(it, true, observer) }

        trySend(query(filter)) // initial load

        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    fun query(filter: MediaFilter): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        if (filter.matches(MediaType.VIDEO)) results += queryVideos()
        if (filter.matches(MediaType.IMAGE)) results += queryImages()
        if (filter.matches(MediaType.AUDIO)) results += queryAudio()
        return results.sortedByDescending { it.dateAddedEpochSec }
    }

    private fun queryVideos(): List<MediaItem> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        return queryCollection(uri, projection, MediaType.VIDEO)
    }

    private fun queryImages(): List<MediaItem> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        return queryCollection(uri, projection, MediaType.IMAGE)
    }

    private fun queryAudio(): List<MediaItem> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM,
        )
        return queryCollection(uri, projection, MediaType.AUDIO)
    }

    private fun queryCollection(
        collection: android.net.Uri,
        projection: Array<String>,
        type: MediaType,
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        runCatching {
            resolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    items += MediaItem(
                        id = id.toString(),
                        uri = contentUri.toString(),
                        type = type,
                        displayName = nameCol.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: "",
                        durationMs = durationCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                        width = widthCol.takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0,
                        height = heightCol.takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0,
                        sizeBytes = sizeCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                        dateAddedEpochSec = dateCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                        folderName = bucketCol.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: "",
                    )
                }
            }
        }
        return items
    }

    fun getByUri(uri: String): MediaItem? = query(MediaFilter.ALL).firstOrNull { it.uri == uri }

    @Suppress("unused")
    private val sdkInt = Build.VERSION.SDK_INT
}
