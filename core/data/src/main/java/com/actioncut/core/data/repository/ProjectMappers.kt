package com.actioncut.core.data.repository

import com.actioncut.core.data.local.entity.ProjectEntity
import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.Project
import com.actioncut.core.model.ProjectSummary
import com.actioncut.core.model.Timeline
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Entity <-> domain mapping. The timeline is (de)serialized via kotlinx.serialization. */
internal object ProjectMappers {

    fun ProjectEntity.toSummary(): ProjectSummary = ProjectSummary(
        id = id,
        name = name,
        thumbnailUri = thumbnailUri,
        durationMs = durationMs,
        aspectRatio = aspectRatio.toAspectRatio(),
        updatedAtEpochMs = updatedAtEpochMs,
    )

    fun ProjectEntity.toDomain(json: Json): Project = Project(
        id = id,
        name = name,
        aspectRatio = aspectRatio.toAspectRatio(),
        timeline = runCatching { json.decodeFromString<Timeline>(timelineJson) }
            .getOrDefault(Timeline.empty()),
        thumbnailUri = thumbnailUri,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    fun Project.toEntity(json: Json): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        aspectRatio = aspectRatio.name,
        thumbnailUri = thumbnailUri,
        durationMs = durationMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        timelineJson = json.encodeToString(timeline),
    )

    private fun String.toAspectRatio(): AspectRatio =
        runCatching { AspectRatio.valueOf(this) }.getOrDefault(AspectRatio.DEFAULT)
}
