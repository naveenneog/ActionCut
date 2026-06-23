package com.actioncut.feature.export.work

import android.content.Context
import com.actioncut.core.domain.port.MediaSaver
import com.actioncut.core.domain.usecase.ExportProjectUseCase
import com.actioncut.core.domain.usecase.GetProjectUseCase
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.VideoFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a project export and saves the result to the device Gallery.
 *
 * The export runs **directly** (the Media3 exporter already marshals onto the main
 * Looper), collected within the caller's coroutine scope — this removed the WorkManager
 * indirection that was a silent point of failure. On completion the file is copied into
 * `Movies/ActionCut` via [MediaSaver], and the emitted [ExportState.Completed] carries the
 * shareable gallery `content://` URI.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getProject: GetProjectUseCase,
    private val exportProject: ExportProjectUseCase,
    private val mediaSaver: MediaSaver,
) {
    data class ExportHandle(val outputPath: String, val state: Flow<ExportState>)

    fun export(projectId: String, settings: ExportSettings): ExportHandle {
        val fileName = "ActionCut_${System.currentTimeMillis()}.${settings.format.extension}"
        val outputFile = File(context.cacheDir, fileName)
        val outputPath = outputFile.absolutePath

        val state = channelFlow {
            val project = getProject(projectId)
            if (project == null) {
                trySend(ExportState.Failed("Project not found"))
                close()
                return@channelFlow
            }
            exportProject(project, settings, outputPath).collect { s ->
                if (s is ExportState.Completed) {
                    val mime = if (settings.format == VideoFormat.WEBM_VP9) "video/webm" else "video/mp4"
                    val galleryUri = runCatching {
                        mediaSaver.saveVideoToGallery(outputPath, fileName, mime)
                    }.getOrNull()
                    runCatching { outputFile.delete() }
                    trySend(ExportState.Completed(galleryUri ?: s.outputUri))
                } else {
                    trySend(s)
                }
            }
            close()
        }
        return ExportHandle(outputPath, state)
    }

    /** Cancellation is handled by cancelling the collecting coroutine (see ViewModel). */
    fun cancel(projectId: String) = Unit
}
