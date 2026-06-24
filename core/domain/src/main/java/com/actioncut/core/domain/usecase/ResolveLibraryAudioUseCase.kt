package com.actioncut.core.domain.usecase

import com.actioncut.core.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Resolves a bundled music/SFX library track name into a playable `file://` URI (copying
 * the asset into the cache on first use), so it can be added to the timeline like any other
 * audio clip.
 */
class ResolveLibraryAudioUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(rawResName: String): String? =
        mediaRepository.resolveLibraryTrack(rawResName)
}
