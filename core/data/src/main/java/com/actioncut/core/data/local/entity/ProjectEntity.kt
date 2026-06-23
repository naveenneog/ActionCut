package com.actioncut.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room representation of a project. Metadata lives in columns (cheap to query for the
 * home list) while the full editable [com.actioncut.core.model.Timeline] is stored as a
 * serialized JSON blob, decoded lazily when the editor opens.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aspectRatio: String,
    val thumbnailUri: String?,
    val durationMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val timelineJson: String,
)
