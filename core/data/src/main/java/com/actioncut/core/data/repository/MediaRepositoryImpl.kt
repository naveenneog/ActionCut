package com.actioncut.core.data.repository

import com.actioncut.core.common.coroutine.DispatcherProvider
import com.actioncut.core.data.mediastore.MediaStoreDataSource
import com.actioncut.core.domain.repository.MediaFilter
import com.actioncut.core.domain.repository.MediaRepository
import com.actioncut.core.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** MediaStore-backed [MediaRepository]. */
class MediaRepositoryImpl @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val dispatchers: DispatcherProvider,
) : MediaRepository {

    override fun observeMedia(filter: MediaFilter): Flow<List<MediaItem>> =
        dataSource.observeMedia(filter).flowOn(dispatchers.io)

    override suspend fun getMedia(uri: String): MediaItem? = withContext(dispatchers.io) {
        dataSource.getByUri(uri)
    }

    override suspend fun resolveMedia(uri: String): MediaItem? = withContext(dispatchers.io) {
        dataSource.resolveMedia(uri)
    }

    override suspend fun resolveLibraryTrack(rawResName: String): String? =
        withContext(dispatchers.io) { dataSource.resolveLibraryTrack(rawResName) }
}
