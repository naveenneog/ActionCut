package com.actioncut.feature.export.ui

import com.actioncut.core.model.ExportPreset
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState

data class ExportUiState(
    val projectName: String = "",
    val settings: ExportSettings = ExportSettings(),
    val selectedPreset: ExportPreset? = ExportPreset.ORIGINAL,
    val exportState: ExportState = ExportState.Idle,
    val outputPath: String? = null,
) {
    val isExporting: Boolean get() = exportState is ExportState.InProgress
    val isCompleted: Boolean get() = exportState is ExportState.Completed
    val canEditSettings: Boolean get() = exportState is ExportState.Idle || exportState is ExportState.Failed
}
