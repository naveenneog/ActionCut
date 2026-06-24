package com.actioncut.core.domain.repository

import com.actioncut.core.model.MediaItem
import com.actioncut.core.model.MediaType
import kotlinx.coroutines.flow.Flow

/** Query filter for browsing device media. */
enum class MediaFilter {
    ALL,
    VIDEO,
    IMAGE,
    AUDIO;

    fun matches(type: MediaType): Boolean = when (this) {
        ALL -> true
        VIDEO -> type == MediaType.VIDEO
        IMAGE -> type == MediaType.IMAGE
        AUDIO -> type == MediaType.AUDIO
    }
}

/**
 * Read access to on-device media via scoped storage. Implemented in `:core:data`
 * using MediaStore. Returns a cold [Flow] so the picker updates as content changes.
 */
interface MediaRepository {
    fun observeMedia(filter: MediaFilter = MediaFilter.ALL): Flow<List<MediaItem>>

    suspend fun getMedia(uri: String): MediaItem?

    /**
     * Resolves an arbitrary content URI (e.g. one returned by the system audio picker /
     * Storage Access Framework) into a [MediaItem], probing its duration and type with
     * [android.media.MediaMetadataRetriever]. Used when adding music in the editor.
     */
    suspend fun resolveMedia(uri: String): MediaItem?

    /**
     * Materialises a bundled music/SFX library track (`res/raw/<rawResName>`) into a
     * playable `file://` URI, copying it into the cache on first use. Returns null if the
     * named asset isn't bundled.
     */
    suspend fun resolveLibraryTrack(rawResName: String): String?
}
