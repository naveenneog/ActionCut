package com.actioncut.core.media.export

import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.Project
import kotlin.math.max

/**
 * Builds an FFmpeg argument list that renders a [Project]'s main video lane — trimming,
 * scaling, applying per-clip speed/reverse, and concatenating clips.
 *
 * This is the *FFmpeg path* the brief asks for. The popular `com.arthenica:ffmpeg-kit-*`
 * artifacts were retired from Maven Central (Jan 2025), so we don't hard-depend on them;
 * instead this builder produces the exact command a self-hosted FFmpegKit / mobile-ffmpeg
 * build would execute. Wire it up in [FFmpegVideoEngine] by handing these args to
 * `FFmpegKit.executeWithArguments(...)`.
 */
object FFmpegCommandBuilder {

    /**
     * @param targetWidth even output width
     * @param targetHeight even output height
     */
    fun buildExportCommand(
        project: Project,
        settings: ExportSettings,
        outputPath: String,
        targetWidth: Int,
        targetHeight: Int,
    ): List<String> {
        val clips = project.timeline.videoTracks.firstOrNull()?.clips.orEmpty()
            .filter { it.mediaUri != null }

        val args = mutableListOf("-y") // overwrite output

        // One -i input per clip, pre-trimmed at the demuxer for speed.
        clips.forEach { clip ->
            if (clip.type != ClipType.IMAGE) {
                args += listOf("-ss", msToTimecode(clip.sourceInMs))
                if (clip.sourceOutMs > clip.sourceInMs) {
                    args += listOf("-to", msToTimecode(clip.sourceOutMs))
                }
            }
            args += listOf("-i", clip.mediaUri!!)
        }

        args += listOf("-filter_complex", buildFilterGraph(clips, targetWidth, targetHeight))
        args += listOf("-map", "[outv]")
        if (clips.any { it.hasAudioTrack }) {
            args += listOf("-map", "[outa]")
        }

        args += listOf(
            "-r", settings.frameRate.fps.toString(),
            "-b:v", settings.effectiveBitrate().toString(),
            "-c:v", videoCodec(settings),
            "-pix_fmt", "yuv420p",
            outputPath,
        )
        return args
    }

    private fun buildFilterGraph(clips: List<Clip>, w: Int, h: Int): String {
        val sb = StringBuilder()
        val videoLabels = mutableListOf<String>()
        val audioLabels = mutableListOf<String>()

        clips.forEachIndexed { index, clip ->
            val scale = "scale=$w:$h:force_original_aspect_ratio=increase," +
                "crop=$w:$h,setsar=1"
            val speedPts = if (clip.speed != 1f) ",setpts=${1f / clip.speed}*PTS" else ""
            val reverse = if (clip.isReversed) ",reverse" else ""
            sb.append("[$index:v]$scale$speedPts$reverse[v$index];")
            videoLabels += "[v$index]"

            if (clip.hasAudioTrack) {
                val atempo = atempoChain(clip.speed)
                val areverse = if (clip.isReversed) ",areverse" else ""
                val volume = if (clip.volume != 1f) ",volume=${clip.volume}" else ""
                sb.append("[$index:a]aformat=sample_rates=48000:channel_layouts=stereo$atempo$areverse$volume[a$index];")
                audioLabels += "[a$index]"
            }
        }

        sb.append(videoLabels.joinToString(""))
        sb.append("concat=n=${videoLabels.size}:v=1:a=0[outv]")
        if (audioLabels.isNotEmpty()) {
            sb.append(";")
            sb.append(audioLabels.joinToString(""))
            sb.append("concat=n=${audioLabels.size}:v=0:a=1[outa]")
        }
        return sb.toString()
    }

    /** FFmpeg's `atempo` only accepts 0.5..2.0, so larger factors are chained. */
    private fun atempoChain(speed: Float): String {
        if (speed == 1f) return ""
        var remaining = speed
        val parts = mutableListOf<String>()
        while (remaining > 2f) {
            parts += "atempo=2.0"
            remaining /= 2f
        }
        while (remaining < 0.5f) {
            parts += "atempo=0.5"
            remaining /= 0.5f
        }
        parts += "atempo=${max(0.5f, remaining)}"
        return "," + parts.joinToString(",")
    }

    private fun videoCodec(settings: ExportSettings): String = when (settings.format) {
        com.actioncut.core.model.VideoFormat.MP4_H265 -> "libx265"
        com.actioncut.core.model.VideoFormat.WEBM_VP9 -> "libvpx-vp9"
        else -> "libx264"
    }

    private fun msToTimecode(ms: Long): String {
        val totalSeconds = ms / 1000.0
        return String.format(java.util.Locale.US, "%.3f", totalSeconds)
    }

    private val Clip.hasAudioTrack: Boolean
        get() = type == ClipType.VIDEO || type == ClipType.AUDIO
}
