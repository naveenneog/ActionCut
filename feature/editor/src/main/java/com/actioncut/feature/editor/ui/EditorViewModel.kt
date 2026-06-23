package com.actioncut.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actioncut.core.domain.editor.ClipFactory
import com.actioncut.core.domain.editor.TimelineEditor
import com.actioncut.core.domain.usecase.GetProjectUseCase
import com.actioncut.core.domain.usecase.ResolveMediaUseCase
import com.actioncut.core.domain.usecase.SaveProjectUseCase
import com.actioncut.core.media.player.PlayerController
import com.actioncut.core.media.waveform.WaveformExtractor
import com.actioncut.core.model.CanvasSettings
import com.actioncut.core.model.ColorAdjustments
import com.actioncut.core.model.Filter
import com.actioncut.core.model.Project
import com.actioncut.core.model.TextProperties
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Transition
import com.actioncut.core.model.VisualEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProject: GetProjectUseCase,
    private val saveProject: SaveProjectUseCase,
    private val resolveMedia: ResolveMediaUseCase,
    private val waveformExtractor: WaveformExtractor,
    val playerController: PlayerController,
) : ViewModel() {

    private val projectId: String = savedStateHandle[ARG_PROJECT_ID] ?: ""

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var loadedProject: Project? = null
    private val undoStack = ArrayDeque<Timeline>()
    private val redoStack = ArrayDeque<Timeline>()
    private var saveJob: Job? = null

    init {
        observePlayback()
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val project = getProject(projectId)
            if (project == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            loadedProject = project
            _uiState.update {
                it.copy(
                    isLoading = false,
                    projectName = project.name,
                    timeline = project.timeline,
                    aspectRatio = project.aspectRatio,
                    canvas = project.canvas,
                    selectedClipId = project.timeline.allClips.firstOrNull()?.id,
                )
            }
            playerController.setTimeline(project.timeline)
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            playerController.state.collect { playback ->
                _uiState.update { state ->
                    val following = if (playback.isPlaying) {
                        playback.positionMs.coerceIn(0, state.durationMs)
                    } else {
                        state.playheadMs
                    }
                    state.copy(isPlaying = playback.isPlaying, playheadMs = following)
                }
            }
        }
        viewModelScope.launch {
            playerController.positionFlow().collect { positionMs ->
                if (_uiState.value.isPlaying) {
                    _uiState.update { it.copy(playheadMs = positionMs.coerceIn(0, it.durationMs)) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ playback

    fun togglePlayPause() = playerController.togglePlayPause()

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0, _uiState.value.durationMs)
        playerController.seekTo(clamped)
        _uiState.update { it.copy(playheadMs = clamped) }
    }

    // ------------------------------------------------------------------ selection / tools

    fun selectClip(clipId: String?) = _uiState.update { it.copy(selectedClipId = clipId) }

    /** Loads a normalized amplitude envelope for an audio clip's waveform. */
    suspend fun loadWaveform(uri: String): FloatArray = waveformExtractor.amplitudes(uri)

    fun setActiveTool(tool: EditorTool?) {
        when (tool) {
            EditorTool.SPLIT -> { splitAtPlayhead(); clearTool() }
            EditorTool.DELETE -> { deleteSelected(); clearTool() }
            EditorTool.REVERSE -> { toggleReverse(); clearTool() }
            EditorTool.ROTATE -> { rotateSelected(); clearTool() }
            EditorTool.MUTE -> { toggleMuteSelected(); clearTool() }
            EditorTool.EXTRACT_AUDIO -> { detachAudioFromSelected(); clearTool() }
            EditorTool.AUDIO -> clearTool() // the screen launches the audio picker
            EditorTool.PIP -> clearTool() // the screen launches the video picker
            else -> _uiState.update { it.copy(activeTool = tool) }
        }
    }

    private fun clearTool() = _uiState.update { it.copy(activeTool = null) }

    fun setZoom(pxPerSecond: Float) = _uiState.update {
        it.copy(
            pxPerSecond = pxPerSecond.coerceIn(
                EditorUiState.MIN_PX_PER_SECOND,
                EditorUiState.MAX_PX_PER_SECOND,
            ),
        )
    }

    // ------------------------------------------------------------------ structural edits

    fun splitAtPlayhead() {
        val state = _uiState.value
        val clipId = state.selectedClipId ?: clipAtPlayhead()?.id ?: return
        mutate(structural = true) { TimelineEditor.splitClip(it, clipId, state.playheadMs) }
    }

    fun deleteSelected() {
        val clipId = _uiState.value.selectedClipId ?: return
        mutate(structural = true) { TimelineEditor.removeClip(it, clipId, ripple = true) }
        _uiState.update { it.copy(selectedClipId = null) }
    }

    fun trimStart(clipId: String, newStartMs: Long) =
        mutate(structural = true) { TimelineEditor.trimClipStart(it, clipId, newStartMs) }

    fun trimEnd(clipId: String, newEndMs: Long) =
        mutate(structural = true) { TimelineEditor.trimClipEnd(it, clipId, newEndMs) }

    /** Drag-to-move a clip along its lane (no player rebuild during the drag). */
    fun moveClip(clipId: String, newStartMs: Long) {
        val track = TimelineEditor.findClip(_uiState.value.timeline, clipId)?.first ?: return
        mutate(structural = false) {
            TimelineEditor.moveClip(it, clipId, track.id, newStartMs.coerceAtLeast(0))
        }
    }

    /** Rebuilds the preview once a drag-arrange finishes. */
    fun commitArrangement() {
        playerController.setTimeline(_uiState.value.timeline)
        playerController.seekTo(_uiState.value.playheadMs)
    }

    /** Extracts the selected video clip's audio onto a separate audio lane. */
    fun detachAudioFromSelected() = withSelected { id ->
        mutate(structural = true) { TimelineEditor.detachAudio(it, id) }
    }

    fun setSpeed(speed: Float) = withSelected { id ->
        mutate(structural = true) { TimelineEditor.setSpeed(it, id, speed) }
    }

    fun toggleReverse() = withSelected { id ->
        val current = currentClip(id)?.isReversed ?: false
        mutate(structural = true) { TimelineEditor.setReversed(it, id, !current) }
    }

    fun addTextAtPlayhead(text: String) {
        val state = _uiState.value
        val track = state.timeline.tracks.firstOrNull { it.type == com.actioncut.core.model.TrackType.TEXT }
        val clip = ClipFactory.text(text, state.playheadMs)
        mutate(structural = true) { timeline ->
            if (track != null) {
                TimelineEditor.insertClip(timeline, track.id, clip)
            } else {
                val (withTrack, trackId) = TimelineEditor.addTrack(timeline, com.actioncut.core.model.TrackType.TEXT)
                TimelineEditor.insertClip(withTrack, trackId, clip)
            }
        }
    }

    /**
     * Adds a picked audio file (music / voiceover) as an audio clip at the playhead,
     * creating an AUDIO lane if the project doesn't have one yet.
     */
    fun addAudioAtPlayhead(uri: String) {
        viewModelScope.launch {
            val media = resolveMedia(uri) ?: return@launch
            val audioMedia = media.copy(type = com.actioncut.core.model.MediaType.AUDIO)
            val clip = ClipFactory.fromMedia(audioMedia, _uiState.value.playheadMs)
            val track = _uiState.value.timeline.tracks
                .firstOrNull { it.type == com.actioncut.core.model.TrackType.AUDIO }
            mutate(structural = true) { timeline ->
                if (track != null) {
                    TimelineEditor.insertClip(timeline, track.id, clip)
                } else {
                    val (withTrack, trackId) =
                        TimelineEditor.addTrack(timeline, com.actioncut.core.model.TrackType.AUDIO)
                    TimelineEditor.insertClip(withTrack, trackId, clip)
                }
            }
        }
    }

    /** Toggles the selected clip's audio on/off (volume 0 = muted / audio removed on export). */
    fun toggleMuteSelected() = withSelected { id ->
        val muted = (currentClip(id)?.volume ?: 1f) == 0f
        mutate(structural = false) { TimelineEditor.setVolume(it, id, if (muted) 1f else 0f) }
    }

    /** Adds an emoji sticker overlay at the playhead on an OVERLAY lane. */
    fun addStickerAtPlayhead(emoji: String) {
        val clip = ClipFactory.emojiSticker(emoji, _uiState.value.playheadMs)
        val track = _uiState.value.timeline.tracks
            .firstOrNull { it.type == com.actioncut.core.model.TrackType.OVERLAY }
        mutate(structural = false) { timeline ->
            if (track != null) {
                TimelineEditor.addClip(timeline, track.id, clip)
            } else {
                val (withTrack, trackId) =
                    TimelineEditor.addTrack(timeline, com.actioncut.core.model.TrackType.OVERLAY)
                TimelineEditor.addClip(withTrack, trackId, clip)
            }
        }
        _uiState.update { it.copy(selectedClipId = clip.id) }
    }

    /** Repositions an overlay (sticker/text) on the preview canvas (normalized -1..1). */
    fun setOverlayPosition(clipId: String, offsetX: Float, offsetY: Float) {
        val clip = currentClip(clipId) ?: return
        mutate(structural = false) {
            TimelineEditor.setTransform(it, clipId, clip.transform.copy(offsetX = offsetX, offsetY = offsetY))
        }
    }

    /** Resizes an overlay / PiP (uniform scale). */
    fun setOverlayScale(clipId: String, scale: Float) {
        val clip = currentClip(clipId) ?: return
        mutate(structural = false) {
            TimelineEditor.setTransform(it, clipId, clip.transform.copy(scale = scale.coerceIn(0.1f, 1f)))
        }
    }

    // ------------------------------------------------------------------ canvas / crop

    fun setFitMode(mode: com.actioncut.core.model.FitMode) = updateCanvas { it.copy(fitMode = mode) }

    fun setBackgroundColor(argb: Int) = updateCanvas { it.copy(backgroundColorArgb = argb) }

    private fun updateCanvas(transform: (CanvasSettings) -> CanvasSettings) {
        val newCanvas = transform(_uiState.value.canvas)
        _uiState.update { it.copy(canvas = newCanvas) }
        loadedProject = loadedProject?.copy(canvas = newCanvas)
        scheduleSave()
    }

    /** Sets the crop region (normalized 0..1) of the selected clip. */
    fun setCrop(clipId: String, crop: com.actioncut.core.model.CropRect) {
        mutate(structural = false) {
            TimelineEditor.updateClip(it, clipId) { c -> c.copy(crop = crop) }
        }
    }

    /** Adds a picked video as a picture-in-picture overlay (scaled, in a corner). */
    fun addPipAtPlayhead(uri: String) {
        viewModelScope.launch {
            val media = resolveMedia(uri) ?: return@launch
            val base = ClipFactory.fromMedia(
                media.copy(type = com.actioncut.core.model.MediaType.VIDEO),
                _uiState.value.playheadMs,
            )
            val clip = base.copy(
                transform = com.actioncut.core.model.Transform(offsetX = 0.4f, offsetY = -0.4f, scale = 0.4f),
            )
            val track = _uiState.value.timeline.tracks
                .firstOrNull { it.type == com.actioncut.core.model.TrackType.OVERLAY }
            mutate(structural = true) { timeline ->
                if (track != null) {
                    TimelineEditor.addClip(timeline, track.id, clip)
                } else {
                    val (withTrack, trackId) =
                        TimelineEditor.addTrack(timeline, com.actioncut.core.model.TrackType.OVERLAY)
                    TimelineEditor.addClip(withTrack, trackId, clip)
                }
            }
            _uiState.update { it.copy(selectedClipId = clip.id) }
        }
    }

    // ------------------------------------------------------------------ property edits

    fun setVolume(volume: Float) = withSelected { id ->
        mutate(structural = false) { TimelineEditor.setVolume(it, id, volume) }
    }

    fun rotateSelected() = withSelected { id ->
        val current = currentClip(id)?.rotationDegrees ?: 0
        mutate(structural = false) { TimelineEditor.setRotation(it, id, current + 90) }
    }

    fun setFilter(filter: Filter?) = withSelected { id ->
        mutate(structural = false) { TimelineEditor.setFilter(it, id, filter) }
    }

    fun setAdjustments(adjustments: ColorAdjustments) = withSelected { id ->
        mutate(structural = false) { TimelineEditor.setAdjustments(it, id, adjustments) }
    }

    fun setTextProperties(text: TextProperties) = withSelected { id ->
        mutate(structural = false) { TimelineEditor.updateClip(it, id) { clip -> clip.copy(text = text) } }
    }

    fun setTransition(transition: Transition?) = withSelected { id ->
        mutate(structural = false) { TimelineEditor.setTransition(it, id, transition) }
    }

    fun addEffect(effect: VisualEffect) = withSelected { id ->
        mutate(structural = false) { TimelineEditor.addEffect(it, id, effect) }
    }

    // ------------------------------------------------------------------ undo / redo

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_uiState.value.timeline)
        val previous = undoStack.removeLast()
        applyTimeline(previous, structural = true)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_uiState.value.timeline)
        val next = redoStack.removeLast()
        applyTimeline(next, structural = true)
    }

    // ------------------------------------------------------------------ internals

    private inline fun withSelected(action: (String) -> Unit) {
        _uiState.value.selectedClipId?.let(action)
    }

    private fun currentClip(id: String) = _uiState.value.timeline.allClips.firstOrNull { it.id == id }

    private fun clipAtPlayhead(): com.actioncut.core.model.Clip? {
        val playhead = _uiState.value.playheadMs
        return _uiState.value.timeline.videoTracks.firstOrNull()?.clipAt(playhead)
    }

    private fun mutate(structural: Boolean, op: (Timeline) -> Timeline) {
        val current = _uiState.value.timeline
        val updated = op(current)
        if (updated == current) return
        undoStack.addLast(current)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        applyTimeline(updated, structural)
    }

    private fun applyTimeline(timeline: Timeline, structural: Boolean) {
        _uiState.update {
            it.copy(
                timeline = timeline,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                playheadMs = it.playheadMs.coerceIn(0, timeline.durationMs),
            )
        }
        if (structural) {
            playerController.setTimeline(timeline)
            playerController.seekTo(_uiState.value.playheadMs)
        } else {
            // Reflect volume/mute changes in the preview without a disruptive rebuild.
            playerController.updateVolumes(timeline)
        }
        scheduleSave()
    }

    private fun scheduleSave() {
        val project = loadedProject ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            val updated = project.copy(timeline = _uiState.value.timeline)
            loadedProject = updated
            saveProject(updated)
        }
    }

    fun saveNow() {
        val project = loadedProject ?: return
        viewModelScope.launch {
            saveProject(project.copy(timeline = _uiState.value.timeline))
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveNow()
        playerController.release()
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
        private const val MAX_UNDO = 50
    }
}
