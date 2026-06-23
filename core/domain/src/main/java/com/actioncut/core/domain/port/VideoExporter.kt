package com.actioncut.core.domain.port

import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * Output port for rendering a [Project] to a video file. Defined in the domain layer
 * (dependency-inversion) and implemented by an adapter in `:core:media`.
 *
 * The default adapter uses **Media3 Transformer**; an FFmpeg-based adapter can be
 * swapped in without changing callers (see `FFmpegVideoEngine`).
 */
interface VideoExporter {
    /**
     * Renders [project] using [settings] to [outputFilePath], emitting [ExportState]
     * updates (progress, completion, failure). Collecting scope cancellation aborts
     * the export.
     */
    fun export(
        project: Project,
        settings: ExportSettings,
        outputFilePath: String,
    ): Flow<ExportState>
}
