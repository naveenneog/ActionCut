package com.actioncut.core.domain.usecase

import com.actioncut.core.domain.repository.MediaFilter
import com.actioncut.core.domain.repository.MediaRepository
import com.actioncut.core.model.MediaItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes device media (optionally filtered) for the media picker. */
class GetDeviceMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    operator fun invoke(filter: MediaFilter = MediaFilter.ALL): Flow<List<MediaItem>> =
        mediaRepository.observeMedia(filter)
}
