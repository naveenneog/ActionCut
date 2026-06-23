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
}
