package com.actioncut.core.domain.usecase

import com.actioncut.core.domain.repository.ProjectRepository
import com.actioncut.core.model.Project
import com.actioncut.core.model.ProjectSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams project summaries for the home screen. */
class ObserveProjectsUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
) {
    operator fun invoke(): Flow<List<ProjectSummary>> = projectRepository.observeProjects()
}

/** Loads the full project (with timeline) for the editor. */
class GetProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(id: String): Project? = projectRepository.getProject(id)
}

/** Persists a project (create or update). */
class SaveProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(project: Project) {
        projectRepository.upsertProject(project.copy(updatedAtEpochMs = System.currentTimeMillis()))
    }
}

/** Deletes a project by id. */
class DeleteProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(id: String) = projectRepository.deleteProject(id)
}
