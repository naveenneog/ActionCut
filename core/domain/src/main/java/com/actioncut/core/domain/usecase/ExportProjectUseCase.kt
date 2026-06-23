package com.actioncut.core.domain.usecase

import com.actioncut.core.domain.port.VideoExporter
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Renders a [Project] to a file via the [VideoExporter] port, surfacing [ExportState]
 * progress. The concrete exporter (Media3 Transformer by default) is injected, keeping
 * this use case engine-agnostic.
 */
class ExportProjectUseCase @Inject constructor(
    private val videoExporter: VideoExporter,
) {
    operator fun invoke(
        project: Project,
        settings: ExportSettings,
        outputFilePath: String,
    ): Flow<ExportState> = videoExporter.export(project, settings, outputFilePath)
}
