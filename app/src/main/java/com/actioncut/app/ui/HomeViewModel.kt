package com.actioncut.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actioncut.core.domain.usecase.DeleteProjectUseCase
import com.actioncut.core.domain.usecase.ObserveProjectsUseCase
import com.actioncut.core.model.ProjectSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeProjects: ObserveProjectsUseCase,
    private val deleteProject: DeleteProjectUseCase,
) : ViewModel() {

    val projects: StateFlow<List<ProjectSummary>> = observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(projectId: String) {
        viewModelScope.launch { deleteProject(projectId) }
    }
}
