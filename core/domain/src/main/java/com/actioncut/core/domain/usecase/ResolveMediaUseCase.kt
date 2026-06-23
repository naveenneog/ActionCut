package com.actioncut.core.domain.usecase

import com.actioncut.core.domain.repository.MediaRepository
import com.actioncut.core.model.MediaItem
import javax.inject.Inject

/**
 * Resolves a picked content URI (audio or video) into a [MediaItem] with duration/type,
 * so the editor can add it to the timeline. Backed by MediaMetadataRetriever in data.
 */
class ResolveMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(uri: String): MediaItem? = mediaRepository.resolveMedia(uri)
}
