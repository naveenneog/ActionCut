package com.actioncut.core.domain.usecase

import com.actioncut.core.common.id.Ids
import com.actioncut.core.domain.editor.ClipFactory
import com.actioncut.core.domain.repository.ProjectRepository
import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.Clip
import com.actioncut.core.model.MediaItem
import com.actioncut.core.model.MediaType
import com.actioncut.core.model.Project
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
import javax.inject.Inject

/**
 * Builds a brand-new [Project] from a set of selected media, laying video/image clips
 * contiguously on the main video lane and audio files on a dedicated audio lane, then
 * persists it. Returns the created project so the caller can open the editor.
 */
class CreateProjectFromMediaUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(
        name: String,
        aspectRatio: AspectRatio,
        media: List<MediaItem>,
    ): Project {
        val now = System.currentTimeMillis()

        // Main video lane: video + image clips placed back to back.
        var videoCursor = 0L
        val videoClips = mutableListOf<Clip>()
        media.filter { it.type == MediaType.VIDEO || it.type == MediaType.IMAGE }
            .forEach { item ->
                val clip = ClipFactory.fromMedia(item, videoCursor)
                videoClips += clip
                videoCursor = clip.timelineEndMs
            }

        // Audio lane: audio clips placed back to back.
        var audioCursor = 0L
        val audioClips = mutableListOf<Clip>()
        media.filter { it.type == MediaType.AUDIO }.forEach { item ->
            val clip = ClipFactory.fromMedia(item, audioCursor)
            audioClips += clip
            audioCursor = clip.timelineEndMs
        }

        val tracks = buildList {
            add(Track(id = Ids.track(), type = TrackType.VIDEO, clips = videoClips, index = 0))
            if (audioClips.isNotEmpty()) {
                add(Track(id = Ids.track(), type = TrackType.AUDIO, clips = audioClips, index = 1))
            }
        }

        val project = Project(
            id = Ids.project(),
            name = name.ifBlank { "Untitled" },
            aspectRatio = aspectRatio,
            timeline = Timeline(tracks = tracks),
            thumbnailUri = media.firstOrNull()?.uri,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        projectRepository.upsertProject(project)
        return project
    }
}
