package com.actioncut.core.media.export

import com.actioncut.core.domain.port.VideoExporter
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * FFmpeg-based [VideoExporter] adapter (swappable alternative to [Media3VideoExporter]).
 *
 * The actual FFmpeg invocation is intentionally not bundled: the `ffmpeg-kit` Maven
 * artifacts were retired (Jan 2025), and shipping a ~30MB native binary is a deployment
 * decision for the integrator. [FFmpegCommandBuilder] already produces the exact argument
 * list; to enable this engine:
 *
 * 1. Add a self-hosted FFmpegKit (or mobile-ffmpeg) dependency.
 * 2. Replace the body below with:
 *    `FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { session -> ... }`
 *    mapping the session state + statistics to [ExportState].
 * 3. Re-point the Hilt binding in `MediaModule` from [Media3VideoExporter] to this class.
 */
class FFmpegVideoEngine : VideoExporter {

    override fun export(
        project: Project,
        settings: ExportSettings,
        outputFilePath: String,
    ): Flow<ExportState> = flow {
        // The command that *would* be executed by a bundled FFmpeg binary:
        val args = FFmpegCommandBuilder.buildExportCommand(
            project = project,
            settings = settings,
            outputPath = outputFilePath,
            targetWidth = settings.resolution.width,
            targetHeight = settings.resolution.height,
        )
        emit(
            ExportState.Failed(
                "FFmpeg binary not bundled. Prepared ${args.size}-arg command; " +
                    "wire FFmpegKit into FFmpegVideoEngine to enable. Use Media3VideoExporter (default).",
            ),
        )
    }
}
