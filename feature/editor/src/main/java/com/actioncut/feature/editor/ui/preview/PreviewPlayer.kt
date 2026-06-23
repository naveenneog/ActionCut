package com.actioncut.feature.editor.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.actioncut.core.designsystem.component.clickableNoRipple
import com.actioncut.core.model.AspectRatio
import com.actioncut.core.model.Clip

/**
 * Top-of-editor preview surface. Letterboxes the [ExoPlayer] output to the project's
 * [AspectRatio] on a black canvas, overlays active sticker/text clips (draggable to
 * reposition), and shows a tap-to-toggle play/pause control.
 */
@Composable
fun PreviewPlayer(
    player: ExoPlayer,
    aspectRatio: AspectRatio,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    overlays: List<Clip> = emptyList(),
    playheadMs: Long = 0L,
    selectedClipId: String? = null,
    onSelectOverlay: (String) -> Unit = {},
    onMoveOverlay: (String, Float, Float) -> Unit = { _, _, _ -> },
    pipPlayer: ExoPlayer? = null,
    pipClips: List<Clip> = emptyList(),
    onScaleOverlay: (String, Float) -> Unit = { _, _ -> },
    fitMode: com.actioncut.core.model.FitMode = com.actioncut.core.model.FitMode.FILL,
    backgroundColorArgb: Int = 0xFF000000.toInt(),
    modifier: Modifier = Modifier,
) {
    val resizeMode = when (fitMode) {
        com.actioncut.core.model.FitMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        com.actioncut.core.model.FitMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        com.actioncut.core.model.FitMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio.value)
                .background(Color(backgroundColorArgb))
                .clickableNoRipple { onTogglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        this.player = player
                        this.resizeMode = resizeMode
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = {
                    it.player = player
                    it.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (pipPlayer != null) {
                PipLayer(
                    pipPlayer = pipPlayer,
                    pipClips = pipClips,
                    playheadMs = playheadMs,
                    selectedClipId = selectedClipId,
                    onSelect = onSelectOverlay,
                    onMove = onMoveOverlay,
                    onScale = onScaleOverlay,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            OverlayLayer(
                overlays = overlays,
                playheadMs = playheadMs,
                selectedClipId = selectedClipId,
                onSelect = onSelectOverlay,
                onMove = onMoveOverlay,
                modifier = Modifier.fillMaxSize(),
            )

            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp).padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PipLayer(
    pipPlayer: ExoPlayer,
    pipClips: List<Clip>,
    playheadMs: Long,
    selectedClipId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onScale: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = pipClips.firstOrNull {
        playheadMs >= it.timelineStartMs && playheadMs < it.timelineEndMs
    } ?: return

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val t = active.transform
        val density = androidx.compose.ui.platform.LocalDensity.current
        val pipW = with(density) { (w * t.scale).toDp() }
        val pipH = with(density) { (h * t.scale).toDp() }
        val isSelected = active.id == selectedClipId

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(pipW, pipH)
                .graphicsLayer {
                    translationX = (t.offsetX / 2f) * w
                    translationY = (t.offsetY / 2f) * h
                    rotationZ = t.rotationDegrees
                }
                .border(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                )
                .pointerInput(active.id, w, h) {
                    var ox = t.offsetX
                    var oy = t.offsetY
                    detectDragGestures(
                        onDragStart = { onSelect(active.id); ox = active.transform.offsetX; oy = active.transform.offsetY },
                        onDrag = { change, drag ->
                            change.consume()
                            ox = (ox + drag.x / (w / 2f)).coerceIn(-1f, 1f)
                            oy = (oy + drag.y / (h / 2f)).coerceIn(-1f, 1f)
                            onMove(active.id, ox, oy)
                        },
                    )
                },
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        this.player = pipPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = pipPlayer },
                modifier = Modifier.fillMaxSize(),
            )
            // Resize handle (drag to scale).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(active.id, w) {
                        var s = t.scale
                        detectDragGestures(
                            onDragStart = { onSelect(active.id); s = active.transform.scale },
                            onDrag = { change, drag ->
                                change.consume()
                                s = (s + drag.x / w).coerceIn(0.1f, 1f)
                                onScale(active.id, s)
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun OverlayLayer(
    overlays: List<Clip>,
    playheadMs: Long,
    selectedClipId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        overlays
            .filter { playheadMs >= it.timelineStartMs && playheadMs < it.timelineEndMs }
            .forEach { clip ->
                val t = clip.transform
                val glyph = clip.text?.text ?: ""
                val baseSp = clip.text?.fontSizeSp ?: 24f
                val isSelected = clip.id == selectedClipId

                Text(
                    text = glyph,
                    fontSize = (baseSp * t.scale).sp,
                    color = Color(clip.text?.colorArgb ?: 0xFFFFFFFF.toInt()),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = (t.offsetX / 2f) * w
                            translationY = (t.offsetY / 2f) * h
                            rotationZ = t.rotationDegrees
                        }
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.6f))
                            } else {
                                Modifier
                            },
                        )
                        .pointerInput(clip.id, w, h) {
                            var ox = t.offsetX
                            var oy = t.offsetY
                            detectDragGestures(
                                onDragStart = {
                                    onSelect(clip.id)
                                    ox = clip.transform.offsetX
                                    oy = clip.transform.offsetY
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    ox = (ox + drag.x / (w / 2f)).coerceIn(-1f, 1f)
                                    oy = (oy + drag.y / (h / 2f)).coerceIn(-1f, 1f)
                                    onMove(clip.id, ox, oy)
                                },
                            )
                        }
                        .clickableNoRipple { onSelect(clip.id) },
                )
            }
    }
}
