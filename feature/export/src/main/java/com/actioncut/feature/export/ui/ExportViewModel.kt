package com.actioncut.feature.export.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actioncut.core.domain.usecase.GetProjectUseCase
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.FrameRate
import com.actioncut.core.model.Resolution
import com.actioncut.core.model.VideoFormat
import com.actioncut.feature.export.work.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProject: GetProjectUseCase,
    private val exportManager: ExportManager,
) : ViewModel() {

    private val projectId: String = savedStateHandle[ARG_PROJECT_ID] ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            val project = getProject(projectId)
            if (project != null) {
                _uiState.update {
                    it.copy(
                        projectName = project.name,
                        settings = it.settings.copy(resolution = recommendedResolution()),
                    )
                }
            }
        }
    }

    fun setResolution(resolution: Resolution) =
        _uiState.update { it.copy(settings = it.settings.copy(resolution = resolution)) }

    fun setFrameRate(frameRate: FrameRate) =
        _uiState.update { it.copy(settings = it.settings.copy(frameRate = frameRate)) }

    fun setFormat(format: VideoFormat) =
        _uiState.update { it.copy(settings = it.settings.copy(format = format)) }

    fun startExport() {
        if (_uiState.value.isExporting) return
        val handle = exportManager.export(projectId, _uiState.value.settings)
        _uiState.update { it.copy(outputPath = handle.outputPath, exportState = ExportState.InProgress(0f)) }
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            handle.state.collect { state ->
                _uiState.update { it.copy(exportState = state) }
            }
        }
    }

    fun cancelExport() {
        exportManager.cancel(projectId)
        exportJob?.cancel()
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun reset() = _uiState.update { it.copy(exportState = ExportState.Idle, outputPath = null) }

    private fun recommendedResolution(): Resolution = Resolution.P1080

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
