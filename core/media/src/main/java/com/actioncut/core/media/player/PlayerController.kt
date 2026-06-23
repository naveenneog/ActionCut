package com.actioncut.core.media.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = pushState()
        override fun onPlaybackStateChanged(playbackState: Int) = pushState()
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
        val mainTrack = timeline.videoTracks.firstOrNull()
        val items = mainTrack?.clips.orEmpty()
            .filter { it.mediaUri != null && it.type != ClipType.TEXT }
            .map { clip ->
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
        pushState()
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
        player.seekTo(positionMs.coerceAtLeast(0))
        pushState()
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.1f, 10f))
    }

    /** Emits the live playhead position while playing (collected on the main thread). */
    fun positionFlow(intervalMs: Long = 33L): Flow<Long> = flow {
        while (true) {
            emit(player.currentPosition)
            delay(intervalMs)
        }
    }

    fun release() {
        player.removeListener(listener)
        player.release()
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
