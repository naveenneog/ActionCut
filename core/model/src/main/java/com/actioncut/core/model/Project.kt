package com.actioncut.core.model

import kotlinx.serialization.Serializable

/**
 * Canvas aspect ratio for a project. Drives the preview surface and export geometry.
 */
@Serializable
enum class AspectRatio(val width: Int, val height: Int, val label: String) {
    RATIO_9_16(9, 16, "9:16"),   // vertical / TikTok / Reels
    RATIO_16_9(16, 9, "16:9"),   // landscape / YouTube
    RATIO_1_1(1, 1, "1:1"),      // square
    RATIO_4_5(4, 5, "4:5"),      // portrait / Instagram
    RATIO_4_3(4, 3, "4:3"),      // classic
    RATIO_3_4(3, 4, "3:4");      // portrait classic

    val value: Float get() = width.toFloat() / height.toFloat()

    companion object {
        val DEFAULT = RATIO_9_16
    }
}

/**
 * Lightweight summary of a project for list/grid screens (no timeline payload).
 */
@Serializable
data class ProjectSummary(
    val id: String,
    val name: String,
    val thumbnailUri: String?,
    val durationMs: Long,
    val aspectRatio: AspectRatio,
    val updatedAtEpochMs: Long,
)

/**
 * A full editing project: metadata + the editable [Timeline].
 *
 * Persisted to Room (metadata) with the timeline serialized; see `:core:data`.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val aspectRatio: AspectRatio = AspectRatio.DEFAULT,
    val timeline: Timeline = Timeline.empty(),
    val thumbnailUri: String? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    /** Total project duration derived from the timeline. */
    val durationMs: Long get() = timeline.durationMs

    fun toSummary(): ProjectSummary = ProjectSummary(
        id = id,
        name = name,
        thumbnailUri = thumbnailUri,
        durationMs = durationMs,
        aspectRatio = aspectRatio,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
