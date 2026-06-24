package com.actioncut.feature.editor.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actioncut.core.designsystem.component.AdjustmentSlider
import com.actioncut.core.designsystem.component.PrimaryButton
import com.actioncut.core.designsystem.component.SecondaryButton
import com.actioncut.core.designsystem.component.SectionHeader
import com.actioncut.core.designsystem.component.SelectableChip
import com.actioncut.core.model.CanvasColors
import com.actioncut.core.model.CanvasSettings
import com.actioncut.core.model.Clip
import com.actioncut.core.model.ColorAdjustments
import com.actioncut.core.model.Filter
import com.actioncut.core.model.Filters
import com.actioncut.core.model.FitMode
import com.actioncut.core.model.SpeedPresets
import com.actioncut.core.model.SpeedRamp
import com.actioncut.core.model.Transition
import com.actioncut.core.model.TransitionType
import com.actioncut.core.model.VisualEffectType
import com.actioncut.feature.editor.ui.EditorTool
import kotlin.math.roundToInt

/** Bottom parameter panel that swaps content based on the active [EditorTool]. */
@Composable
fun ToolPanel(
    tool: EditorTool,
    selectedClip: Clip?,
    onClose: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSpeedRamp: (SpeedRamp) -> Unit,
    onVolume: (Float) -> Unit,
    onFilter: (Filter?) -> Unit,
    onAdjust: (ColorAdjustments) -> Unit,
    onAddText: (String) -> Unit,
    onAddSticker: (String) -> Unit,
    canvas: CanvasSettings,
    onFitMode: (FitMode) -> Unit,
    onBackgroundColor: (Int) -> Unit,
    onCrop: (com.actioncut.core.model.CropRect) -> Unit,
    onTransition: (Transition?) -> Unit,
    onToggleEffect: (VisualEffectType) -> Unit,
    onAddKeyframe: () -> Unit,
    onClearKeyframes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 120.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("Done") }
            }

            if (selectedClip == null && tool != EditorTool.TEXT &&
                tool != EditorTool.STICKER && tool != EditorTool.CANVAS
            ) {
                Text(
                    "Select a clip on the timeline first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            when (tool) {
                EditorTool.SPEED -> SpeedPanel(
                    selectedClip?.speed ?: 1f,
                    selectedClip?.speedRamp ?: SpeedRamp.NONE,
                    onSpeed,
                    onSpeedRamp,
                )
                EditorTool.VOLUME -> VolumePanel(selectedClip?.volume ?: 1f, onVolume)
                EditorTool.FILTERS -> FilterPanel(selectedClip?.filter, onFilter)
                EditorTool.ADJUST -> AdjustPanel(
                    key = selectedClip?.id ?: "",
                    current = selectedClip?.adjustments ?: ColorAdjustments.NONE,
                    onAdjust = onAdjust,
                )
                EditorTool.TEXT -> TextPanel(selectedClip?.text?.text ?: "", onAddText)
                EditorTool.STICKER -> StickerPanel(onAddSticker)
                EditorTool.CANVAS -> CanvasPanel(canvas, onFitMode, onBackgroundColor)
                EditorTool.CROP -> CropPanel(selectedClip, onCrop)
                EditorTool.TRANSITIONS -> TransitionPanel(selectedClip?.transitionToNext, onTransition)
                EditorTool.EFFECTS -> EffectsPanel(
                    selectedClip?.effects?.map { it.type }?.toSet() ?: emptySet(),
                    onToggleEffect,
                )
                EditorTool.KEYFRAME -> KeyframePanel(selectedClip, onAddKeyframe, onClearKeyframes)
                else -> Unit
            }
        }
    }
}

@Composable
private fun SpeedPanel(
    current: Float,
    currentRamp: SpeedRamp,
    onSpeed: (Float) -> Unit,
    onSpeedRamp: (SpeedRamp) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(SpeedPresets.values) { speed ->
                SelectableChip(
                    label = "${formatSpeed(speed)}x",
                    selected = current == speed && currentRamp == SpeedRamp.NONE,
                    onClick = { onSpeed(speed) },
                )
            }
        }
        SectionHeader("Speed ramp")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(SpeedRamp.values()) { ramp ->
                SelectableChip(
                    label = ramp.label,
                    selected = currentRamp == ramp,
                    onClick = { onSpeedRamp(ramp) },
                )
            }
        }
    }
}

@Composable
private fun KeyframePanel(clip: Clip?, onAddKeyframe: () -> Unit, onClearKeyframes: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "${clip?.keyframes?.size ?: 0} keyframe(s). Move/scale an overlay or PiP, " +
                "scrub the playhead, then add a keyframe to animate position, scale & opacity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = "Add keyframe", onClick = onAddKeyframe)
            SecondaryButton(text = "Clear", onClick = onClearKeyframes)
        }
    }
}

@Composable
private fun VolumePanel(current: Float, onVolume: (Float) -> Unit) {
    AdjustmentSlider(
        label = "Volume",
        value = current,
        onValueChange = onVolume,
        valueRange = 0f..2f,
        valueFormatter = { "${(it * 100).roundToInt()}%" },
    )
}

@Composable
private fun FilterPanel(current: Filter?, onFilter: (Filter?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(Filters.catalogue) { filter ->
            val isNone = filter.id == Filters.None.id
            val selected = if (isNone) current == null else current?.id == filter.id
            SelectableChip(
                label = filter.name,
                selected = selected,
                onClick = { onFilter(if (isNone) null else filter) },
            )
        }
    }
}

@Composable
private fun AdjustPanel(key: String, current: ColorAdjustments, onAdjust: (ColorAdjustments) -> Unit) {
    var state by remember(key) { mutableStateOf(current) }
    fun push(updated: ColorAdjustments) {
        state = updated
        onAdjust(updated)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AdjustmentSlider("Brightness", state.brightness, { push(state.copy(brightness = it)) }, valueRange = -1f..1f, valueFormatter = ::signed)
        AdjustmentSlider("Contrast", state.contrast, { push(state.copy(contrast = it)) }, valueRange = -1f..1f, valueFormatter = ::signed)
        AdjustmentSlider("Saturation", state.saturation, { push(state.copy(saturation = it)) }, valueRange = -1f..1f, valueFormatter = ::signed)
        AdjustmentSlider("Warmth", state.warmth, { push(state.copy(warmth = it)) }, valueRange = -1f..1f, valueFormatter = ::signed)
    }
}

@Composable
private fun TextPanel(initial: String, onAddText: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Caption text") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        PrimaryButton(
            text = "Add text",
            onClick = { if (text.isNotBlank()) onAddText(text) },
        )
    }
}

@Composable
private fun TransitionPanel(current: Transition?, onTransition: (Transition?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(TransitionType.entries.toList()) { type ->
                val selected = (current?.type ?: TransitionType.NONE) == type
                SelectableChip(
                    label = type.displayName,
                    selected = selected,
                    onClick = {
                        onTransition(if (type == TransitionType.NONE) null else Transition(type))
                    },
                )
            }
        }
        Text(
            "Applies to the cut into the next clip. Renders on export; Slide/Wipe fall back to a fade.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EffectsPanel(applied: Set<VisualEffectType>, onToggleEffect: (VisualEffectType) -> Unit) {
    val haptic = com.actioncut.core.designsystem.component.rememberHaptic()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(VisualEffectType.entries.toList()) { type ->
                SelectableChip(
                    label = type.displayName,
                    selected = type in applied,
                    onClick = { haptic.tick(); onToggleEffect(type) },
                )
            }
        }
        Text(
            "Tap to add or remove. Effects render on export.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StickerPanel(onAddSticker: (String) -> Unit) {
    val haptic = com.actioncut.core.designsystem.component.rememberHaptic()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(46.dp),
        modifier = Modifier.heightIn(max = 160.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        gridItems(com.actioncut.core.model.Emojis.popular) { emoji ->
            Text(
                text = emoji,
                fontSize = 30.sp,
                modifier = Modifier
                    .padding(6.dp)
                    .clickable { haptic.tick(); onAddSticker(emoji) },
            )
        }
    }
}

@Composable
private fun CanvasPanel(
    canvas: CanvasSettings,
    onFitMode: (FitMode) -> Unit,
    onBackgroundColor: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Fit")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FitMode.entries.toList()) { mode ->
                SelectableChip(mode.displayName, canvas.fitMode == mode, onClick = { onFitMode(mode) })
            }
        }
        SectionHeader("Background")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(CanvasColors.swatches) { argb ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            2.dp,
                            if (canvas.backgroundColorArgb == argb) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            CircleShape,
                        )
                        .clickable { onBackgroundColor(argb) },
                )
            }
        }
    }
}

@Composable
private fun CropPanel(clip: Clip?, onCrop: (com.actioncut.core.model.CropRect) -> Unit) {
    val crop = clip?.crop ?: com.actioncut.core.model.CropRect()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AdjustmentSlider("Left", crop.left, { onCrop(crop.copy(left = it.coerceIn(0f, crop.right - 0.05f))) }, valueRange = 0f..1f, valueFormatter = ::pct)
        AdjustmentSlider("Top", crop.top, { onCrop(crop.copy(top = it.coerceIn(0f, crop.bottom - 0.05f))) }, valueRange = 0f..1f, valueFormatter = ::pct)
        AdjustmentSlider("Right", crop.right, { onCrop(crop.copy(right = it.coerceIn(crop.left + 0.05f, 1f))) }, valueRange = 0f..1f, valueFormatter = ::pct)
        AdjustmentSlider("Bottom", crop.bottom, { onCrop(crop.copy(bottom = it.coerceIn(crop.top + 0.05f, 1f))) }, valueRange = 0f..1f, valueFormatter = ::pct)
        SecondaryButton(text = "Reset crop", onClick = { onCrop(com.actioncut.core.model.CropRect()) }, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun pct(value: Float): String = "${(value * 100).roundToInt()}%"

private fun signed(value: Float): String {
    val pct = (value * 100).roundToInt()
    return if (pct > 0) "+$pct" else "$pct"
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
