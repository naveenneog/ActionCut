package com.actioncut.feature.export.work

import android.content.Context
import android.os.Environment
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues [ExportWorker] jobs and exposes their progress as an [ExportState] flow by
 * observing WorkManager's [WorkInfo]. The output file is written to the app-specific
 * external Movies directory (scoped-storage friendly, no extra permissions).
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    data class ExportHandle(val outputPath: String, val state: Flow<ExportState>)

    fun export(projectId: String, settings: ExportSettings): ExportHandle {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val fileName = "ActionCut_${System.currentTimeMillis()}.${settings.format.extension}"
        val outputPath = File(dir, fileName).absolutePath

        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(
                workDataOf(
                    ExportWorker.KEY_PROJECT_ID to projectId,
                    ExportWorker.KEY_OUTPUT_PATH to outputPath,
                    ExportWorker.KEY_RESOLUTION to settings.resolution.name,
                    ExportWorker.KEY_FPS to settings.frameRate.name,
                    ExportWorker.KEY_FORMAT to settings.format.name,
                ),
            )
            .build()

        workManager.enqueueUniqueWork(uniqueName(projectId), ExistingWorkPolicy.REPLACE, request)

        val flow = workManager.getWorkInfoByIdFlow(request.id)
            .map { info -> info.toExportState(outputPath) }
        return ExportHandle(outputPath, flow)
    }

    fun cancel(projectId: String) {
        workManager.cancelUniqueWork(uniqueName(projectId))
    }

    private fun uniqueName(projectId: String) = "export_$projectId"

    private fun WorkInfo?.toExportState(outputPath: String): ExportState = when (this?.state) {
        null, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportState.InProgress(0f)
        WorkInfo.State.RUNNING -> ExportState.InProgress(progress.getFloat(ExportWorker.KEY_PROGRESS, 0f))
        WorkInfo.State.SUCCEEDED ->
            ExportState.Completed(outputData.getString(ExportWorker.KEY_OUTPUT_URI) ?: outputPath)
        WorkInfo.State.FAILED ->
            ExportState.Failed(outputData.getString(ExportWorker.KEY_ERROR) ?: "Export failed")
        WorkInfo.State.CANCELLED -> ExportState.Cancelled
    }
}
