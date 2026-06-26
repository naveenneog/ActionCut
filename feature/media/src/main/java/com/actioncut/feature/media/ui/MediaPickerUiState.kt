package com.actioncut.feature.media.ui

import com.actioncut.core.domain.repository.MediaFilter
import com.actioncut.core.model.MediaItem

/** UI state for the media picker screen. */
data class MediaPickerUiState(
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val filter: MediaFilter = MediaFilter.ALL,
    val media: List<MediaItem> = emptyList(),
    val selected: List<MediaItem> = emptyList(),
    val isCreating: Boolean = false,
    val projectName: String = "",
) {
    val canCreate: Boolean get() = selected.isNotEmpty() && !isCreating
    val isEmpty: Boolean get() = permissionGranted && !isLoading && media.isEmpty()

    fun selectionIndexOf(item: MediaItem): Int = selected.indexOfFirst { it.id == item.id }
}
