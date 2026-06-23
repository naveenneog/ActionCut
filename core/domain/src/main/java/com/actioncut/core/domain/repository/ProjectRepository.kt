package com.actioncut.core.domain.repository

import com.actioncut.core.model.Project
import com.actioncut.core.model.ProjectSummary
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for editing projects. Metadata + serialized timeline live in Room
 * (see `:core:data`). Summaries are observed for the home/list screen; the full
 * [Project] is loaded on demand when opening the editor.
 */
interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>

    suspend fun getProject(id: String): Project?

    suspend fun upsertProject(project: Project)

    suspend fun deleteProject(id: String)
}
