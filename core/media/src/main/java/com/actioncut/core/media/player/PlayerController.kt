package com.actioncut.core.media.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.Timeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Thin wrapper around an [ExoPlayer] for the editor preview. Builds a sequential preview
 * playlist from the main video lane (each clip trimmed to its source window) and exposes
 * playback as observable [PlaybackState].
 *
 * Not a singleton: one instance per editor screen, released in the ViewModel's onCleared.
 *
 * Preview fidelity note: ExoPlayer applies playback speed globally, so per-clip speed and
 * GPU effects are approximated in preview; the authoritative render happens at export time
 * via [com.actioncut.core.media.export.Media3VideoExporter].
 */
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = false
        repeatMode = Player.REPEAT_MODE_OFF
    }

    /**
     * Secondary player for the AUDIO lane (music / extracted audio), mixed alongside the
     * video preview. Kept time-aligned to [player] (best-effort: assumes audio clips run
     * from the start of the timeline; arbitrary mid-timeline offsets are approximate).
     */
    private val audioPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = false
        repeatMode = Player.REPEAT_MODE_OFF
    }

    /** Player for picture-in-picture overlay video clips; its surface is rendered by the
     *  preview. Muted in preview (PiP audio is mixed at export). */
    val pipPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = false
        repeatMode = Player.REPEAT_MODE_OFF
        volume = 0f
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Per-playlist-item volume (0..1 for ExoPlayer), kept in sync with the timeline. */
    private var clipVolumes: List<Float> = emptyList()

    // Cumulative *timeline* start offset (ms) of each preview item per lane, so we can map
    // ExoPlayer's per-item position to an absolute timeline position (and back). Without
    // this the playhead snaps to 0 at every clip boundary (the "jumps back to first" bug).
    private var videoStartsMs: List<Long> = emptyList()
    private var audioStartsMs: List<Long> = emptyList()
    private var pipStartsMs: List<Long> = emptyList()
    private var totalPreviewMs: Long = 0L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mirrorSecondaryPlayers(isPlaying)
            pushState()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                audioPlayer.pause()
                pipPlayer.pause()
            }
            pushState()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyCurrentVolume()
            pushState()
        }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = pushState()
    }

    init {
        player.addListener(listener)
    }

    /** Rebuilds the preview playlist from [timeline]'s main video lane. */
    fun setTimeline(timeline: Timeline) {
        val clips = previewClips(timeline)
        clipVolumes = clips.map { it.volume }
        videoStartsMs = cumulativeStarts(clips.map { previewDurationMs(it) })
        totalPreviewMs = (videoStartsMs.lastOrNull() ?: 0L) + (clips.lastOrNull()?.let { previewDurationMs(it) } ?: 0L)
        val items = clips.map { clip ->
            val builder = MediaItem.Builder().setUri(clip.mediaUri)
            if (clip.type == ClipType.IMAGE) {
                builder.setImageDurationMs(clip.timelineDurationMs)
            } else {
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceInMs)
                        .setEndPositionMs(
                            if (clip.sourceOutMs > clip.sourceInMs) clip.sourceOutMs
                            else C.TIME_END_OF_SOURCE,
                        )
                        .build(),
                )
            }
            builder.build()
        }
        player.setMediaItems(items)
        player.prepare()
        applyCurrentVolume()

        // Build the audio-lane playlist for the secondary player.
        val audioClips = audioClipsOf(timeline)
        audioStartsMs = cumulativeStarts(audioClips.map { previewDurationMs(it) })
        audioPlayer.setMediaItems(audioClips.map { it.toClippedMediaItem() })
        audioPlayer.prepare()

        // Build the PiP playlist (overlay video clips).
        val pipClips = pipClipsOf(timeline)
        pipStartsMs = cumulativeStarts(pipClips.map { previewDurationMs(it) })
        pipPlayer.setMediaItems(pipClips.map { it.toClippedMediaItem() })
        pipPlayer.prepare()

        pushState()
    }

    /** Preview footprint (ms) of a clip on the timeline — used to map per-item positions. */
    private fun previewDurationMs(clip: Clip): Long = clip.timelineDurationMs.coerceAtLeast(1L)

    private fun cumulativeStarts(durations: List<Long>): List<Long> {
        var acc = 0L
        return durations.map { val start = acc; acc += it; start }
    }

    /** Absolute timeline position (ms) of the master video player across the whole playlist. */
    fun masterPositionMs(): Long {
        if (player.mediaItemCount == 0) return 0L
        val base = videoStartsMs.getOrElse(player.currentMediaItemIndex) { 0L }
        return (base + player.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /** Seeks [p] to an absolute timeline position, resolving the right playlist item. */
    private fun seekAbsolute(p: ExoPlayer, starts: List<Long>, absMs: Long) {
        if (p.mediaItemCount == 0) return
        var idx = 0
        for (i in 0 until p.mediaItemCount) {
            if (absMs >= (starts.getOrElse(i) { 0L })) idx = i else break
        }
        val within = (absMs - starts.getOrElse(idx) { 0L }).coerceAtLeast(0L)
        p.seekTo(idx, within)
    }

    private fun Clip.toClippedMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(mediaUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sourceInMs)
                    .setEndPositionMs(if (sourceOutMs > sourceInMs) sourceOutMs else C.TIME_END_OF_SOURCE)
                    .build(),
            )
            .build()

    private fun audioClipsOf(timeline: Timeline): List<Clip> =
        timeline.audioTracks
            .flatMap { it.clips }
            .filter { it.mediaUri != null && it.volume > 0f }
            .sortedBy { it.timelineStartMs }

    private fun pipClipsOf(timeline: Timeline): List<Clip> =
        timeline.tracks
            .filter { it.type == com.actioncut.core.model.TrackType.OVERLAY }
            .flatMap { it.clips }
            .filter { it.mediaUri != null && it.type == ClipType.VIDEO }
            .sortedBy { it.timelineStartMs }

    private fun mirrorSecondaryPlayers(isPlaying: Boolean) {
        val master = masterPositionMs()
        if (audioPlayer.mediaItemCount > 0) {
            if (isPlaying) { seekAbsolute(audioPlayer, audioStartsMs, master); audioPlayer.play() } else audioPlayer.pause()
        }
        if (pipPlayer.mediaItemCount > 0) {
            if (isPlaying) { seekAbsolute(pipPlayer, pipStartsMs, master); pipPlayer.play() } else pipPlayer.pause()
        }
    }

    /**
     * Updates preview volume/mute without rebuilding the playlist (used for non-structural
     * volume edits), so muting a clip is reflected live in the preview.
     */
    fun updateVolumes(timeline: Timeline) {
        clipVolumes = previewClips(timeline).map { it.volume }
        applyCurrentVolume()
    }

    private fun previewClips(timeline: Timeline): List<Clip> =
        timeline.videoTracks.firstOrNull()?.clips.orEmpty()
            .filter { it.mediaUri != null && it.type != ClipType.TEXT }

    private fun applyCurrentVolume() {
        val index = player.currentMediaItemIndex
        // ExoPlayer volume is 0..1; clamp (the model allows boost up to 2 for export only).
        player.volume = clipVolumes.getOrElse(index) { 1f }.coerceIn(0f, 1f)
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        val pos = positionMs.coerceAtLeast(0)
        seekAbsolute(player, videoStartsMs, pos)
        seekAbsolute(audioPlayer, audioStartsMs, pos)
        seekAbsolute(pipPlayer, pipStartsMs, pos)
        pushState()
    }

    fun setSpeed(speed: Float) {
        val params = PlaybackParameters(speed.coerceIn(0.1f, 10f))
        player.playbackParameters = params
        audioPlayer.playbackParameters = params
        pipPlayer.playbackParameters = params
    }

    /** Emits the live *absolute* playhead position while playing (collected on the main thread). */
    fun positionFlow(intervalMs: Long = 33L): Flow<Long> = flow {
        while (true) {
            val master = masterPositionMs()
            // Keep the secondary lanes time-aligned with the master, comparing *absolute*
            // positions so a video clip boundary doesn't yank the audio back to its start.
            if (audioPlayer.isPlaying) {
                val audioAbs = audioStartsMs.getOrElse(audioPlayer.currentMediaItemIndex) { 0L } + audioPlayer.currentPosition
                if (kotlin.math.abs(audioAbs - master) > 300) seekAbsolute(audioPlayer, audioStartsMs, master)
            }
            if (pipPlayer.isPlaying) {
                val pipAbs = pipStartsMs.getOrElse(pipPlayer.currentMediaItemIndex) { 0L } + pipPlayer.currentPosition
                if (kotlin.math.abs(pipAbs - master) > 300) seekAbsolute(pipPlayer, pipStartsMs, master)
            }
            emit(master)
            delay(intervalMs)
        }
    }

    fun release() {
        player.removeListener(listener)
        player.release()
        audioPlayer.release()
        pipPlayer.release()
    }

    private fun pushState() {
        _state.value = PlaybackState(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = masterPositionMs(),
            durationMs = if (totalPreviewMs > 0L) totalPreviewMs else {
                player.duration.let { if (it == C.TIME_UNSET) 0L else it.coerceAtLeast(0) }
            },
            isEnded = player.playbackState == Player.STATE_ENDED,
        )
    }
}
