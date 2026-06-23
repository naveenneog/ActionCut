package com.actioncut.core.domain.usecase

import com.actioncut.core.domain.repository.ProjectRepository
import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.MediaItem
import com.actioncut.core.model.MediaType
import com.actioncut.core.model.Project
import com.actioncut.core.model.ProjectSummary
import com.actioncut.core.model.TrackType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateProjectFromMediaUseCaseTest {

    private class FakeProjectRepository : ProjectRepository {
        var saved: Project? = null
        override fun observeProjects(): Flow<List<ProjectSummary>> = emptyFlow()
        override suspend fun getProject(id: String): Project? = saved
        override suspend fun upsertProject(project: Project) { saved = project }
        override suspend fun deleteProject(id: String) {}
    }

    private fun video(id: String, durationMs: Long) = MediaItem(
        id = id, uri = "uri://$id", type = MediaType.VIDEO, displayName = id, durationMs = durationMs,
    )

    private fun audio(id: String, durationMs: Long) = MediaItem(
        id = id, uri = "uri://$id", type = MediaType.AUDIO, displayName = id, durationMs = durationMs,
    )

    @Test
    fun build_laysVideoClipsContiguouslyOnVideoLane() = runTest {
        val repo = FakeProjectRepository()
        val useCase = CreateProjectFromMediaUseCase(repo)

        val project = useCase(
            name = "My Reel",
            aspectRatio = AspectRatio.RATIO_9_16,
            media = listOf(video("a", 3000), video("b", 2000)),
        )

        val videoTrack = project.timeline.tracks.first { it.type == TrackType.VIDEO }
        assertEquals(2, videoTrack.clips.size)
        assertEquals(0L, videoTrack.clips[0].timelineStartMs)
        assertEquals(3000L, videoTrack.clips[0].timelineEndMs)
        assertEquals(3000L, videoTrack.clips[1].timelineStartMs)
        assertEquals(5000L, videoTrack.clips[1].timelineEndMs)
        assertEquals(5000L, project.durationMs)
    }

    @Test
    fun build_putsAudioOnSeparateLane_andPersists() = runTest {
        val repo = FakeProjectRepository()
        val useCase = CreateProjectFromMediaUseCase(repo)

        val project = useCase(
            name = "Mixed",
            aspectRatio = AspectRatio.RATIO_1_1,
            media = listOf(video("v", 4000), audio("m", 8000)),
        )

        assertTrue(project.timeline.tracks.any { it.type == TrackType.AUDIO })
        assertEquals(project.id, repo.saved?.id)
        assertNotNull(repo.saved)
    }

    @Test
    fun build_blankName_fallsBackToUntitled() = runTest {
        val useCase = CreateProjectFromMediaUseCase(FakeProjectRepository())
        val project = useCase("", AspectRatio.DEFAULT, listOf(video("a", 1000)))
        assertEquals("Untitled", project.name)
    }
}
