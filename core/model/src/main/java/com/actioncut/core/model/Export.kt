package com.actioncut.core.model

import kotlinx.serialization.Serializable

/** Output resolutions supported by the export engine. */
@Serializable
enum class Resolution(val shortLabel: String, val width: Int, val height: Int) {
    P480("480p", 854, 480),
    P720("720p", 1280, 720),
    P1080("1080p", 1920, 1080),
    P2160("4K", 3840, 2160);

    /** Long edge in pixels — used to scale to the project aspect ratio. */
    val longEdge: Int get() = maxOf(width, height)

    companion object {
        val DEFAULT = P1080
    }
}

/** Container/codec target for the exported file. */
@Serializable
enum class VideoFormat(val extension: String, val mimeType: String) {
    MP4_H264("mp4", "video/avc"),
    MP4_H265("mp4", "video/hevc"),
    WEBM_VP9("webm", "video/x-vnd.on2.vp9"),
}

/** Common frame rates. */
@Serializable
enum class FrameRate(val fps: Int) {
    FPS_24(24),
    FPS_30(30),
    FPS_60(60);

    companion object {
        val DEFAULT = FPS_30
    }
}

/**
 * Fully describes an export job. Bitrate is derived from resolution/fps when null.
 */
@Serializable
data class ExportSettings(
    val resolution: Resolution = Resolution.DEFAULT,
    val frameRate: FrameRate = FrameRate.DEFAULT,
    val format: VideoFormat = VideoFormat.MP4_H264,
    val bitrateBps: Int? = null,
) {
    /** Heuristic target bitrate when one is not explicitly provided. */
    fun effectiveBitrate(): Int = bitrateBps ?: run {
        val pixels = resolution.width.toLong() * resolution.height.toLong()
        // ~0.1 bits per pixel per frame, a reasonable default for H.264.
        (pixels * frameRate.fps * 0.1).toInt().coerceAtLeast(2_000_000)
    }
}

/**
 * Progress/state of an export job, surfaced to the UI and persisted by WorkManager.
 */
sealed interface ExportState {
    data object Idle : ExportState
    data class InProgress(val progress: Float, val etaMs: Long? = null) : ExportState
    data class Completed(val outputUri: String) : ExportState
    data class Failed(val message: String) : ExportState
    data object Cancelled : ExportState
}
