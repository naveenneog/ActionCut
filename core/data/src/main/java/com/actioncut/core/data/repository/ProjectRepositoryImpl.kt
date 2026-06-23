package com.actioncut.core.data.repository

import com.actioncut.core.common.coroutine.DispatcherProvider
import com.actioncut.core.data.local.dao.ProjectDao
import com.actioncut.core.data.repository.ProjectMappers.toDomain
import com.actioncut.core.data.repository.ProjectMappers.toEntity
import com.actioncut.core.data.repository.ProjectMappers.toSummary
import com.actioncut.core.domain.repository.ProjectRepository
import com.actioncut.core.model.Project
import com.actioncut.core.model.ProjectSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Room-backed [ProjectRepository]. */
class ProjectRepositoryImpl @Inject constructor(
    private val dao: ProjectDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        dao.observeAll().map { list -> list.map { it.toSummary() } }

    override suspend fun getProject(id: String): Project? = withContext(dispatchers.io) {
        dao.getById(id)?.toDomain(json)
    }

    override suspend fun upsertProject(project: Project) = withContext(dispatchers.io) {
        dao.upsert(project.toEntity(json))
    }

    override suspend fun deleteProject(id: String) = withContext(dispatchers.io) {
        dao.deleteById(id)
    }
}
