package com.actioncut.feature.editor.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.actioncut.core.designsystem.component.clickableNoRipple
import com.actioncut.core.designsystem.theme.LaneAudio
import com.actioncut.core.designsystem.theme.LaneOverlay
import com.actioncut.core.designsystem.theme.LaneText
import com.actioncut.core.designsystem.theme.LaneVideo
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ClipType
import com.actioncut.core.model.Timeline
import com.actioncut.core.model.Track
import com.actioncut.core.model.TrackType
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TRACK_HEIGHT = 52
private const val RULER_HEIGHT = 22
private const val HANDLE_WIDTH = 14

/**
 * CapCut-style timeline: a fixed center playhead with content scrolling beneath it. The
 * horizontal scroll offset *is* the current time, giving smooth, low-latency scrubbing.
 * Supports multi-track lanes, clip selection and drag-to-trim on the selected clip.
 */
@Composable
fun EditorTimeline(
    timeline: Timeline,
    playheadMs: Long,
    durationMs: Long,
    pxPerSecond: Float,
    isPlaying: Boolean,
    selectedClipId: String?,
    onScrub: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onMoveClip: (String, Long) -> Unit = { _, _ -> },
    onMoveCommit: () -> Unit = {},
    onLoadWaveform: (suspend (String) -> FloatArray)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val layoutDurationMs = durationMs.coerceAtLeast(2_000)

    fun msToPxF(ms: Long): Float = ms / 1000f * pxPerSecond
    fun pxToMs(px: Int): Long = (px / pxPerSecond * 1000f).toLong()

    // User scroll -> seek.
    LaunchedEffect(scrollState, pxPerSecond, durationMs) {
        snapshotFlow { scrollState.value to scrollState.isScrollInProgress }
            .collect { (value, inProgress) ->
                if (inProgress) onScrub(pxToMs(value).coerceIn(0, durationMs))
            }
    }
    // Playhead (playback / edits) -> follow scroll, unless the user is actively scrolling.
    LaunchedEffect(playheadMs, isPlaying) {
        if (!scrollState.isScrollInProgress) {
            val target = msToPxF(playheadMs).roundToInt()
            if (abs(scrollState.value - target) > 1) scrollState.scrollTo(target)
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        val viewportPx = constraints.maxWidth
        val halfViewportDp = with(density) { (viewportPx / 2).toDp() }
        val contentWidthDp = with(density) { msToPxF(layoutDurationMs).toDp() }

        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Spacer(Modifier.width(halfViewportDp))
            Column {
                TimeRuler(layoutDurationMs, pxPerSecond, contentWidthDp)
                val orderedTracks = timeline.tracks.sortedBy { it.index }
                if (orderedTracks.isEmpty()) {
                    Spacer(Modifier.width(contentWidthDp).height(TRACK_HEIGHT.dp))
                }
                orderedTracks.forEach { track ->
                    TrackLane(
                        track = track,
                        pxPerSecond = pxPerSecond,
                        contentWidthDp = contentWidthDp,
                        selectedClipId = selectedClipId,
                        onSelectClip = onSelectClip,
                        onTrimStart = onTrimStart,
                        onTrimEnd = onTrimEnd,
                        onMoveClip = onMoveClip,
                        onMoveCommit = onMoveCommit,
                        onLoadWaveform = onLoadWaveform,
                    )
                }
            }
            Spacer(Modifier.width(halfViewportDp))
        }

        // Fixed center playhead.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun TimeRuler(durationMs: Long, pxPerSecond: Float, widthDp: androidx.compose.ui.unit.Dp) {
    val totalSeconds = (durationMs / 1000).toInt()
    // Adaptive tick spacing so labels never crowd at low zoom.
    val step = when {
        pxPerSecond >= 120 -> 1
        pxPerSecond >= 60 -> 2
        else -> 5
    }
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(RULER_HEIGHT.dp),
    ) {
        val density = LocalDensity.current
        for (second in 0..totalSeconds step step) {
            val xDp = with(density) { (second * pxPerSecond).toDp() }
            Text(
                text = "${second}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = xDp).padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun TrackLane(
    track: Track,
    pxPerSecond: Float,
    contentWidthDp: androidx.compose.ui.unit.Dp,
    selectedClipId: String?,
    onSelectClip: (String?) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onMoveCommit: () -> Unit,
    onLoadWaveform: (suspend (String) -> FloatArray)?,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(contentWidthDp)
            .height(TRACK_HEIGHT.dp)
            .padding(vertical = 3.dp),
    ) {
        track.clips.forEach { clip ->
            val xDp = with(density) { (clip.timelineStartMs / 1000f * pxPerSecond).toDp() }
            Box(modifier = Modifier.offset(x = xDp).fillMaxHeight()) {
                ClipBlock(
                    clip = clip,
                    pxPerSecond = pxPerSecond,
                    selected = clip.id == selectedClipId,
                    onSelect = { onSelectClip(clip.id) },
                    onTrimStart = onTrimStart,
                    onTrimEnd = onTrimEnd,
                    onMoveClip = onMoveClip,
                    onMoveCommit = onMoveCommit,
                    onLoadWaveform = onLoadWaveform,
                )
            }
        }
    }
}

@Composable
private fun ClipBlock(
    clip: Clip,
    pxPerSecond: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onMoveClip: (String, Long) -> Unit = { _, _ -> },
    onMoveCommit: () -> Unit = {},
    onLoadWaveform: (suspend (String) -> FloatArray)? = null,
) {
    val density = LocalDensity.current
    val widthDp = with(density) { (clip.timelineDurationMs / 1000f * pxPerSecond).toDp() }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    var moveAnchor by remember(clip.id) { mutableLongStateOf(clip.timelineStartMs) }
    var moveAcc by remember(clip.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(laneColor(clip.type))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .clickableNoRipple { onSelect() }
            .pointerInput(clip.id, pxPerSecond) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onSelect()
                        moveAnchor = clip.timelineStartMs
                        moveAcc = 0f
                    },
                    onDrag = { _, drag ->
                        moveAcc += drag.x
                        onMoveClip(clip.id, moveAnchor + (moveAcc / pxPerSecond * 1000f).toLong())
                    },
                    onDragEnd = { onMoveCommit() },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (clip.type == ClipType.AUDIO && clip.mediaUri != null && onLoadWaveform != null) {
            AudioWaveform(
                uri = clip.mediaUri!!,
                loader = onLoadWaveform,
                modifier = Modifier.matchParentSize(),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = clipIcon(clip.type),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = clipLabel(clip),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selected) {
            ClipTrimHandles(
                clip = clip,
                pxPerSecond = pxPerSecond,
                onTrimStart = onTrimStart,
                onTrimEnd = onTrimEnd,
            )
        }
    }
}

@Composable
private fun AudioWaveform(
    uri: String,
    loader: suspend (String) -> FloatArray,
    modifier: Modifier = Modifier,
) {
    var amplitudes by remember(uri) { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(uri) {
        amplitudes = runCatching { loader(uri) }.getOrNull() ?: FloatArray(0)
    }
    val waveColor = Color.White.copy(alpha = 0.38f)
    Canvas(modifier = modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
        val amps = amplitudes
        if (amps.isEmpty()) return@Canvas
        val barWidth = size.width / amps.size
        val midY = size.height / 2f
        for (i in amps.indices) {
            val barHeight = (amps[i] * size.height).coerceAtLeast(2f)
            val x = i * barWidth + barWidth / 2f
            drawLine(
                color = waveColor,
                start = Offset(x, midY - barHeight / 2f),
                end = Offset(x, midY + barHeight / 2f),
                strokeWidth = (barWidth * 0.55f).coerceAtLeast(1f),
            )
        }
    }
}

@Composable
private fun ClipTrimHandles(
    clip: Clip,
    pxPerSecond: Float,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
) {
    Box(Modifier.fillMaxWidth().fillMaxHeight()) {
        // Left handle -> trim start.
        DragHandle(
            modifier = Modifier.align(Alignment.CenterStart),
            initialValue = clip.timelineStartMs,
            pxPerSecond = pxPerSecond,
            onValue = { onTrimStart(clip.id, it) },
        )
        // Right handle -> trim end.
        DragHandle(
            modifier = Modifier.align(Alignment.CenterEnd),
            initialValue = clip.timelineEndMs,
            pxPerSecond = pxPerSecond,
            onValue = { onTrimEnd(clip.id, it) },
        )
    }
}

@Composable
private fun DragHandle(
    modifier: Modifier,
    initialValue: Long,
    pxPerSecond: Float,
    onValue: (Long) -> Unit,
) {
    var anchor by remember { mutableLongStateOf(initialValue) }
    var acc by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .width(HANDLE_WIDTH.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(initialValue, pxPerSecond) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        anchor = initialValue
                        acc = 0f
                    },
                    onHorizontalDrag = { _, delta ->
                        acc += delta
                        onValue(anchor + (acc / pxPerSecond * 1000f).toLong())
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 18.dp)
                .background(Color.White, RoundedCornerShape(2.dp)),
        )
    }
}

/** Placeholder overload removed. */

private fun laneColor(type: ClipType): Color = when (type) {
    ClipType.VIDEO -> LaneVideo
    ClipType.IMAGE -> LaneVideo
    ClipType.AUDIO -> LaneAudio
    ClipType.TEXT -> LaneText
    ClipType.STICKER -> LaneOverlay
}

private fun clipIcon(type: ClipType) = when (type) {
    ClipType.VIDEO -> Icons.Filled.Movie
    ClipType.IMAGE -> Icons.Filled.Image
    ClipType.AUDIO -> Icons.Filled.Audiotrack
    ClipType.TEXT -> Icons.Filled.TextFields
    ClipType.STICKER -> Icons.Filled.Image
}

private fun clipLabel(clip: Clip): String = when (clip.type) {
    ClipType.TEXT -> clip.text?.text ?: "Text"
    ClipType.AUDIO -> "Audio"
    else -> clip.mediaUri?.substringAfterLast('/')?.take(14) ?: clip.type.name
}
