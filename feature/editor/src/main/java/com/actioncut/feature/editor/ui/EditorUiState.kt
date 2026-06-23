package com.actioncut.feature.editor.ui

import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.Clip
import com.actioncut.core.model.Project
import com.actioncut.core.model.Timeline

/** Complete UI state for the editor screen. */
data class EditorUiState(
    val isLoading: Boolean = true,
    val projectName: String = "",
    val timeline: Timeline = Timeline.empty(),
    val aspectRatio: AspectRatio = AspectRatio.DEFAULT,
    val selectedClipId: String? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val pxPerSecond: Float = DEFAULT_PX_PER_SECOND,
    val activeTool: EditorTool? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isExporting: Boolean = false,
) {
    val durationMs: Long get() = timeline.durationMs

    val selectedClip: Clip?
        get() = selectedClipId?.let { id -> timeline.allClips.firstOrNull { it.id == id } }

    val hasContent: Boolean get() = timeline.allClips.isNotEmpty()

    companion object {
        const val DEFAULT_PX_PER_SECOND = 80f
        const val MIN_PX_PER_SECOND = 24f
        const val MAX_PX_PER_SECOND = 320f
    }
}
