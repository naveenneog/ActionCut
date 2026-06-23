package com.actioncut.feature.export.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.actioncut.core.domain.usecase.ExportProjectUseCase
import com.actioncut.core.domain.usecase.GetProjectUseCase
import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.FrameRate
import com.actioncut.core.model.Resolution
import com.actioncut.core.model.VideoFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs a project export off the UI thread (Phase 6 — background export). Reports progress
 * via [setProgress] so the UI can render a live progress bar even if the user leaves the
 * export screen, and returns the output URI on success.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getProject: GetProjectUseCase,
    private val exportProject: ExportProjectUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID) ?: return Result.failure()
        val outputPath = inputData.getString(KEY_OUTPUT_PATH) ?: return Result.failure()
        val settings = ExportSettings(
            resolution = enumOrDefault(inputData.getString(KEY_RESOLUTION), Resolution.DEFAULT),
            frameRate = enumOrDefault(inputData.getString(KEY_FPS), FrameRate.DEFAULT),
            format = enumOrDefault(inputData.getString(KEY_FORMAT), VideoFormat.MP4_H264),
            aspectRatio = inputData.getString(KEY_ASPECT)
                ?.let { runCatching { AspectRatio.valueOf(it) }.getOrNull() },
        )

        val project = getProject(projectId) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Project not found"),
        )

        var result: Result = Result.failure(workDataOf(KEY_ERROR to "Export did not complete"))
        exportProject(project, settings, outputPath).collect { state ->
            when (state) {
                is ExportState.InProgress ->
                    setProgress(workDataOf(KEY_PROGRESS to state.progress))

                is ExportState.Completed ->
                    result = Result.success(workDataOf(KEY_OUTPUT_URI to state.outputUri))

                is ExportState.Failed ->
                    result = Result.failure(workDataOf(KEY_ERROR to state.message))

                else -> Unit
            }
        }
        return result
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_RESOLUTION = "resolution"
        const val KEY_FPS = "fps"
        const val KEY_FORMAT = "format"
        const val KEY_ASPECT = "aspect"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_ERROR = "error"
    }
}
