package com.actioncut.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import com.actioncut.core.domain.usecase.GetProjectUseCase
import com.actioncut.core.media.player.PlaybackState
import com.actioncut.core.media.player.PlayerController
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.Project
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM ViewModel tests (no emulator, no Robolectric) that exercise the real editing
 * logic behind the reported bugs: a clip is auto-selected on load, **delete** actually
 * removes the selected clip, and **duplicate** adds a copy. The player is mocked so no
 * media stack is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun videoClip(id: String, start: Long, end: Long) = Clip(
        id = id,
        type = ClipType.VIDEO,
        mediaUri = "file:///m/$id.mp4",
        timelineStartMs = start,
        timelineEndMs = end,
        sourceOutMs = end - start,
    )

    private fun sampleProject() = Project(
        id = "p1",
        name = "Test",
        timeline = Timeline(
            tracks = listOf(
                Track(
                    id = "t1",
                    type = TrackType.VIDEO,
                    clips = listOf(videoClip("c1", 0L, 3_000L), videoClip("c2", 3_000L, 6_000L)),
                ),
            ),
        ),
    )

    private fun buildViewModel(): EditorViewModel {
        val player = mockk<PlayerController>(relaxed = true)
        every { player.state } returns MutableStateFlow(PlaybackState())
        every { player.positionFlow(any()) } returns emptyFlow()
        val getProject = mockk<GetProjectUseCase>()
        coEvery { getProject(any()) } returns sampleProject()
        return EditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("projectId" to "p1")),
            getProject = getProject,
            saveProject = mockk(relaxed = true),
            resolveMedia = mockk(relaxed = true),
            resolveLibraryAudio = mockk(relaxed = true),
            waveformExtractor = mockk(relaxed = true),
            voiceRecorder = mockk(relaxed = true),
            playerController = player,
        )
    }

    @Test
    fun loadsProjectAndAutoSelectsFirstClip() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.timeline.allClips.size)
        assertEquals("c1", vm.uiState.value.selectedClipId)
    }

    @Test
    fun deleteSelectedRemovesTheClip() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deleteSelected()
        advanceUntilIdle()

        val clips = vm.uiState.value.timeline.allClips
        assertEquals(1, clips.size)
        assertEquals("c2", clips.first().id)
        assertNull(vm.uiState.value.selectedClipId)
    }

    @Test
    fun duplicateSelectedAddsACopy() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.selectClip("c1")
        vm.duplicateSelected()
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.timeline.allClips.size)
    }
}
