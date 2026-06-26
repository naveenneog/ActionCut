package com.actioncut.feature.media.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actioncut.core.domain.repository.MediaFilter
import com.actioncut.core.domain.usecase.CreateProjectFromMediaUseCase
import com.actioncut.core.domain.usecase.GetDeviceMediaUseCase
import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getDeviceMedia: GetDeviceMediaUseCase,
    private val createProjectFromMedia: CreateProjectFromMediaUseCase,
) : ViewModel() {

    private val permissionGranted = MutableStateFlow(false)
    private val filter = MutableStateFlow(MediaFilter.ALL)
    private val selected = MutableStateFlow<List<MediaItem>>(emptyList())
    private val isCreating = MutableStateFlow(false)
    private val projectName = MutableStateFlow(defaultProjectName())

    private val media: StateFlow<List<MediaItem>> =
        combine(permissionGranted, filter) { granted, f -> granted to f }
            .flatMapLatest { (granted, f) ->
                if (granted) getDeviceMedia(f) else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<MediaPickerUiState> = combine(
        permissionGranted,
        filter,
        media,
        selected,
        isCreating,
    ) { granted, f, mediaList, selectedItems, creating ->
        MediaPickerUiState(
            isLoading = granted && mediaList.isEmpty() && !creating,
            permissionGranted = granted,
            filter = f,
            media = mediaList,
            selected = selectedItems,
            isCreating = creating,
        )
    }.combine(projectName) { state, name ->
        state.copy(projectName = name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaPickerUiState())

    fun onPermissionResult(granted: Boolean) {
        permissionGranted.value = granted
    }

    fun setFilter(newFilter: MediaFilter) {
        filter.value = newFilter
    }

    /** Toggles selection, preserving the order in which items were picked. */
    fun toggleSelection(item: MediaItem) {
        selected.value = selected.value.toMutableList().apply {
            val index = indexOfFirst { it.id == item.id }
            if (index >= 0) removeAt(index) else add(item)
        }
    }

    fun clearSelection() {
        selected.value = emptyList()
    }

    /** Updates the name the user is giving the new project. */
    fun setProjectName(name: String) {
        projectName.value = name
    }

    private fun defaultProjectName(): String {
        val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
        return "Video ${fmt.format(java.util.Date())}"
    }

    fun createProject(
        aspectRatio: AspectRatio = AspectRatio.DEFAULT,
        onCreated: (projectId: String) -> Unit,
    ) {
        if (selected.value.isEmpty() || isCreating.value) return
        isCreating.value = true
        val name = projectName.value.ifBlank { defaultProjectName() }
        viewModelScope.launch {
            val project = createProjectFromMedia(name, aspectRatio, selected.value)
            isCreating.value = false
            onCreated(project.id)
        }
    }
}
