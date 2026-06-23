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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio.value)
                .clickableNoRipple { onTogglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        this.player = player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )

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
