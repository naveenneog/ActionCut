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
        val audioItems = audioItems(timeline)
        audioPlayer.setMediaItems(audioItems)
        audioPlayer.prepare()

        // Build the PiP playlist (overlay video clips).
        val pipItems = pipItems(timeline)
        pipPlayer.setMediaItems(pipItems)
        pipPlayer.prepare()

        pushState()
    }

    private fun pipItems(timeline: Timeline): List<MediaItem> =
        timeline.tracks
            .filter { it.type == com.actioncut.core.model.TrackType.OVERLAY }
            .flatMap { it.clips }
            .filter { it.mediaUri != null && it.type == ClipType.VIDEO }
            .sortedBy { it.timelineStartMs }
            .map { clip ->
                MediaItem.Builder()
                    .setUri(clip.mediaUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.sourceInMs)
                            .setEndPositionMs(
                                if (clip.sourceOutMs > clip.sourceInMs) clip.sourceOutMs
                                else C.TIME_END_OF_SOURCE,
                            )
                            .build(),
                    )
                    .build()
            }

    private fun audioItems(timeline: Timeline): List<MediaItem> =
        timeline.audioTracks
            .flatMap { it.clips }
            .filter { it.mediaUri != null && it.volume > 0f }
            .sortedBy { it.timelineStartMs }
            .map { clip ->
                MediaItem.Builder()
                    .setUri(clip.mediaUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.sourceInMs)
                            .setEndPositionMs(
                                if (clip.sourceOutMs > clip.sourceInMs) clip.sourceOutMs
                                else C.TIME_END_OF_SOURCE,
                            )
                            .build(),
                    )
                    .build()
            }

    private fun mirrorSecondaryPlayers(isPlaying: Boolean) {
        listOf(audioPlayer, pipPlayer).forEach { p ->
            if (p.mediaItemCount == 0) return@forEach
            if (isPlaying) {
                p.seekTo(player.currentPosition.coerceAtLeast(0))
                p.play()
            } else {
                p.pause()
            }
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
        player.seekTo(pos)
        if (audioPlayer.mediaItemCount > 0) audioPlayer.seekTo(pos)
        if (pipPlayer.mediaItemCount > 0) pipPlayer.seekTo(pos)
        pushState()
    }

    fun setSpeed(speed: Float) {
        val params = PlaybackParameters(speed.coerceIn(0.1f, 10f))
        player.playbackParameters = params
        audioPlayer.playbackParameters = params
        pipPlayer.playbackParameters = params
    }

    /** Emits the live playhead position while playing (collected on the main thread). */
    fun positionFlow(intervalMs: Long = 33L): Flow<Long> = flow {
        while (true) {
            // Keep the secondary lanes time-aligned with the master video player.
            val master = player.currentPosition.coerceAtLeast(0)
            listOf(audioPlayer, pipPlayer).forEach { p ->
                if (p.isPlaying && kotlin.math.abs(p.currentPosition - master) > 300) {
                    p.seekTo(master)
                }
            }
            emit(player.currentPosition)
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
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.let { if (it == C.TIME_UNSET) 0L else it.coerceAtLeast(0) },
            isEnded = player.playbackState == Player.STATE_ENDED,
        )
    }
}
