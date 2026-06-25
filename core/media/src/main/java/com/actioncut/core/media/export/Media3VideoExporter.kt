package com.actioncut.core.media.export

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.actioncut.core.domain.port.VideoExporter
import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.Project
import com.actioncut.core.model.Resolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Default [VideoExporter] backed by **AndroidX Media3 Transformer** — hardware-accelerated
 * and fully available on Maven Central (unlike the retired FFmpegKit artifacts).
 *
 * Builds a [Composition] from the project's video lane (each clip trimmed to its source
 * window and scaled to the target resolution via a [Presentation] effect), then renders to
 * an MP4/WebM file. Progress is polled on the main [Looper] and streamed as [ExportState].
 *
 * Transformer must be driven from a thread with a [Looper]; we marshal all calls onto the
 * main thread so the exporter works equally from a ViewModel or a background WorkManager
 * worker.
 */
class Media3VideoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reverser: com.actioncut.core.media.reverse.VideoReverser,
) : VideoExporter {

    override fun export(
        project: Project,
        settings: ExportSettings,
        outputFilePath: String,
    ): Flow<ExportState> = callbackFlow {
        val rawClips = project.timeline.videoTracks.firstOrNull()?.clips.orEmpty()
            .filter { it.mediaUri != null }

        if (rawClips.isEmpty()) {
            trySend(ExportState.Failed("Timeline has no exportable video clips"))
            close()
            return@callbackFlow
        }

        // Reverse is not a Media3 effect: pre-process reversed clips into a temp file off the
        // main thread, then treat them as ordinary forward clips below.
        val clips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            rawClips.map { clip ->
                if (clip.isReversed && clip.type == ClipType.VIDEO && clip.mediaUri != null) {
                    val reversedUri = reverser.reverse(clip.mediaUri!!, clip.sourceInMs, clip.sourceOutMs)
                    if (reversedUri != null) {
                        clip.copy(mediaUri = reversedUri, isReversed = false, sourceInMs = 0L, sourceOutMs = 0L)
                    } else {
                        clip
                    }
                } else {
                    clip
                }
            }
        }

        // Preset support: an explicit aspect override (Instagram/TikTok/…) wins over the
        // project's own aspect, letting one project export to many platform shapes.
        val aspect = settings.aspectRatio ?: project.aspectRatio
        val (targetWidth, targetHeight) = targetDimensions(aspect, settings.resolution)
        val handler = Handler(Looper.getMainLooper())
        var transformer: Transformer? = null
        var progressRunnable: Runnable? = null

        handler.post {
            runCatching {
                val overlayClips = project.timeline.allClips.filter {
                    it.type == ClipType.TEXT || it.type == ClipType.STICKER
                }
                val presentationLayout = when (project.canvas.fitMode) {
                    com.actioncut.core.model.FitMode.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
                    com.actioncut.core.model.FitMode.FILL -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    com.actioncut.core.model.FitMode.STRETCH -> Presentation.LAYOUT_STRETCH_TO_FIT
                }
                val editedItems = clips.mapIndexed { index, clip ->
                    val incoming = clips.getOrNull(index - 1)?.transitionToNext
                        ?.takeIf { it.type != com.actioncut.core.model.TransitionType.NONE }
                    val outgoing = clip.transitionToNext
                        ?.takeIf { it.type != com.actioncut.core.model.TransitionType.NONE }
                    clip.toEditedMediaItem(
                        targetWidth, targetHeight, settings, overlayClips, presentationLayout,
                        incoming, outgoing,
                    )
                }
                val videoSequence = EditedMediaItemSequence(editedItems)

                val sequences = mutableListOf(videoSequence)

                // Picture-in-picture: overlay video clips composited over the main video.
                val pipClips = project.timeline.tracks
                    .filter { it.type == com.actioncut.core.model.TrackType.OVERLAY }
                    .flatMap { it.clips }
                    .filter { it.mediaUri != null && it.type == ClipType.VIDEO }
                    .sortedBy { it.timelineStartMs }
                if (pipClips.isNotEmpty()) {
                    sequences += EditedMediaItemSequence(
                        pipClips.map {
                            it.toEditedMediaItem(
                                targetWidth, targetHeight, settings, emptyList(),
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                            )
                        },
                    )
                }

                // Mix the audio lane (background music / added audio) as another sequence.
                val audioClips = project.timeline.audioTracks
                    .flatMap { it.clips }
                    .filter { it.mediaUri != null && it.volume > 0f }
                    .sortedBy { it.timelineStartMs }
                if (audioClips.isNotEmpty()) {
                    sequences += EditedMediaItemSequence(audioClips.map { it.toAudioEditedMediaItem() })
                }

                val compositionBuilder = Composition.Builder(sequences)
                    // Force a (silent) audio track so a sequence that mixes clips without
                    // audio (images, muted clips) and clips with audio doesn't fail with
                    // "preceding MediaItem does not contain any track of type 1".
                    .experimentalSetForceAudioTrack(true)
                if (pipClips.isNotEmpty()) {
                    compositionBuilder.setVideoCompositorSettings(pipCompositorSettings(pipClips.first()))
                }
                val composition = compositionBuilder.build()

                val t = Transformer.Builder(context)
                    .setVideoMimeType(settings.format.mimeType)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            trySend(ExportState.Completed(outputFilePath))
                            close()
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            // Surface the *root* cause (e.g. the GL shader compile log),
                            // not just the opaque top-level "Video frame processing error".
                            val chain = generateSequence(exception as Throwable) { it.cause }
                                .mapNotNull { it.message?.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                                .toList()
                            trySend(ExportState.Failed(chain.lastOrNull() ?: chain.joinToString(" <- ").ifEmpty { "Export failed" }))
                            close()
                        }
                    })
                    .build()

                transformer = t
                t.start(composition, outputFilePath)
                trySend(ExportState.InProgress(0f))

                // Poll progress every 200ms.
                val holder = ProgressHolder()
                progressRunnable = object : Runnable {
                    override fun run() {
                        val current = transformer ?: return
                        val stateInt = current.getProgress(holder)
                        if (stateInt != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            trySend(ExportState.InProgress(holder.progress / 100f))
                        }
                        handler.postDelayed(this, 200L)
                    }
                }
                handler.postDelayed(progressRunnable!!, 200L)
            }.onFailure {
                trySend(ExportState.Failed(it.message ?: "Failed to start export"))
                close()
            }
        }

        awaitClose {
            handler.post {
                progressRunnable?.let { handler.removeCallbacks(it) }
                transformer?.cancel()
                transformer = null
            }
        }
    }

    private fun Clip.toEditedMediaItem(
        targetWidth: Int,
        targetHeight: Int,
        settings: ExportSettings,
        overlays: List<Clip>,
        presentationLayout: Int,
        incoming: com.actioncut.core.model.Transition? = null,
        outgoing: com.actioncut.core.model.Transition? = null,
    ): EditedMediaItem {
        val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)
        if (type != ClipType.IMAGE) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sourceInMs)
                    .setEndPositionMs(
                        if (sourceOutMs > sourceInMs) sourceOutMs else C.TIME_END_OF_SOURCE,
                    )
                    .build(),
            )
        }

        val videoEffects = EffectMapper.videoEffects(this, targetWidth, targetHeight, overlays, presentationLayout)
            .toMutableList()

        // Transitions: ramp this clip's leading/trailing edge (fade/zoom/blur). Applied
        // after the framing/overlays so the whole composited frame transitions.
        val clipDurUs = timelineDurationMs * 1_000L
        incoming?.let { tr ->
            val edgeUs = (tr.durationMs * 1_000L).coerceAtMost(clipDurUs / 2)
            if (edgeUs > 0L) {
                videoEffects += TransitionEffect(
                    TransitionEdge.IN, TransitionEffect.kindFor(tr.type), edgeUs, clipDurUs,
                )
            }
        }
        outgoing?.let { tr ->
            val edgeUs = (tr.durationMs * 1_000L).coerceAtMost(clipDurUs / 2)
            if (edgeUs > 0L) {
                videoEffects += TransitionEffect(
                    TransitionEdge.OUT, TransitionEffect.kindFor(tr.type), edgeUs, clipDurUs,
                )
            }
        }

        val audioProcessors = EffectMapper.audioProcessors(this)

        val builder = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setEffects(Effects(audioProcessors, videoEffects))

        // Mute / "remove audio from this video": strip the clip's own audio track.
        if (volume == 0f && type == ClipType.VIDEO) {
            builder.setRemoveAudio(true)
        }

        if (type == ClipType.IMAGE) {
            builder.setDurationUs(timelineDurationMs * 1_000)
            builder.setFrameRate(settings.frameRate.fps)
        }
        return builder.build()
    }

    /** Audio-only edited item for the background-audio sequence (music / voiceover). */
    private fun Clip.toAudioEditedMediaItem(): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sourceInMs)
                    .setEndPositionMs(if (sourceOutMs > sourceInMs) sourceOutMs else C.TIME_END_OF_SOURCE)
                    .build(),
            )
            .build()
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(EffectMapper.audioProcessors(this), emptyList()))
            .build()
    }

    /**
     * Compositor settings that scale + position the PiP input (id 1) over the main (id 0).
     * Reads keyframes per-frame so an animated PiP moves/zooms across the export, matching
     * the live preview. Assumes the PiP lane starts at timeline 0 (best-effort).
     */
    private fun pipCompositorSettings(pip: com.actioncut.core.model.Clip): VideoCompositorSettings =
        object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<Size>): Size = inputSizes.first()
            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
                if (inputId == 1) {
                    val props = com.actioncut.core.model.Keyframes.propsAt(pip, presentationTimeUs / 1000L)
                    val t = props.transform
                    OverlaySettings.Builder()
                        .setScale(t.scale, t.scale)
                        .setOverlayFrameAnchor(0f, 0f)
                        .setBackgroundFrameAnchor(
                            t.offsetX.coerceIn(-1f, 1f),
                            -t.offsetY.coerceIn(-1f, 1f),
                        )
                        .build()
                } else {
                    OverlaySettings.Builder().build()
                }
        }

    /** Computes even, codec-friendly output dimensions for the given aspect + resolution. */
    private fun targetDimensions(aspect: AspectRatio, resolution: Resolution): Pair<Int, Int> {
        val longEdge = resolution.longEdge
        return if (aspect.value >= 1f) {
            even(longEdge) to even((longEdge / aspect.value).roundToInt())
        } else {
            even((longEdge * aspect.value).roundToInt()) to even(longEdge)
        }
    }

    private fun even(v: Int): Int = if (v % 2 == 0) v else v + 1
}
