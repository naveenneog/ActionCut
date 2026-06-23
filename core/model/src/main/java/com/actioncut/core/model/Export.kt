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
    /** Optional aspect override (from an [ExportPreset]); null = use the project's aspect. */
    val aspectRatio: AspectRatio? = null,
) {
    /** Heuristic target bitrate when one is not explicitly provided. */
    fun effectiveBitrate(): Int = bitrateBps ?: run {
        val pixels = resolution.width.toLong() * resolution.height.toLong()
        // ~0.1 bits per pixel per frame, a reasonable default for H.264.
        (pixels * frameRate.fps * 0.1).toInt().coerceAtLeast(2_000_000)
    }
}

/**
 * One-tap export targets sized for popular platforms. A preset pins the output aspect
 * ratio and resolution; [ExportPreset.ORIGINAL] keeps the project's own aspect.
 */
@Serializable
enum class ExportPreset(
    val displayName: String,
    val platform: String,
    val aspectRatio: AspectRatio?,
    val resolution: Resolution,
) {
    ORIGINAL("Original", "Project", null, Resolution.P1080),
    INSTAGRAM_REEL("Instagram Reel", "Instagram", AspectRatio.RATIO_9_16, Resolution.P1080),
    INSTAGRAM_POST("Instagram Post", "Instagram", AspectRatio.RATIO_1_1, Resolution.P1080),
    INSTAGRAM_PORTRAIT("Instagram 4:5", "Instagram", AspectRatio.RATIO_4_5, Resolution.P1080),
    TIKTOK("TikTok", "TikTok", AspectRatio.RATIO_9_16, Resolution.P1080),
    YOUTUBE("YouTube", "YouTube", AspectRatio.RATIO_16_9, Resolution.P1080),
    YOUTUBE_4K("YouTube 4K", "YouTube", AspectRatio.RATIO_16_9, Resolution.P2160),
    YOUTUBE_SHORTS("YT Shorts", "YouTube", AspectRatio.RATIO_9_16, Resolution.P1080),
    MOBILE_HD("Mobile HD", "Web", AspectRatio.RATIO_16_9, Resolution.P720);

    fun applyTo(settings: ExportSettings): ExportSettings =
        settings.copy(resolution = resolution, aspectRatio = aspectRatio)
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
