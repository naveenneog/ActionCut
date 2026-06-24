package com.actioncut.feature.editor.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.actioncut.core.designsystem.component.ToolItem
import com.actioncut.feature.editor.ui.EditorTool

/** Default ordering of tools in the bottom bar. */
val MainTools: List<EditorTool> = listOf(
    EditorTool.SPLIT,
    EditorTool.SPEED,
    EditorTool.VOLUME,
    EditorTool.AUDIO,
    EditorTool.MUSIC,
    EditorTool.PIP,
    EditorTool.EXTRACT_AUDIO,
    EditorTool.MUTE,
    EditorTool.FILTERS,
    EditorTool.ADJUST,
    EditorTool.CROP,
    EditorTool.CANVAS,
    EditorTool.TEXT,
    EditorTool.STICKER,
    EditorTool.TRANSITIONS,
    EditorTool.EFFECTS,
    EditorTool.KEYFRAME,
    EditorTool.REVERSE,
    EditorTool.ROTATE,
    EditorTool.DELETE,
)

@Composable
fun EditorToolbar(
    activeTool: EditorTool?,
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(MainTools) { tool ->
            ToolItem(
                label = tool.label,
                icon = tool.icon(),
                selected = tool == activeTool,
                onClick = { onToolSelected(tool) },
            )
        }
    }
}

fun EditorTool.icon(): ImageVector = when (this) {
    EditorTool.SPLIT -> Icons.Filled.ContentCut
    EditorTool.TRIM -> Icons.Filled.ContentCut
    EditorTool.DELETE -> Icons.Filled.Delete
    EditorTool.SPEED -> Icons.Filled.Speed
    EditorTool.VOLUME -> Icons.Filled.VolumeUp
    EditorTool.MUTE -> Icons.Filled.VolumeOff
    EditorTool.AUDIO -> Icons.Filled.LibraryMusic
    EditorTool.MUSIC -> Icons.Filled.MusicNote
    EditorTool.PIP -> Icons.Filled.PictureInPictureAlt
    EditorTool.EXTRACT_AUDIO -> Icons.Filled.CallSplit
    EditorTool.REVERSE -> Icons.Filled.FastRewind
    EditorTool.ROTATE -> Icons.Filled.RotateRight
    EditorTool.FILTERS -> Icons.Filled.FilterVintage
    EditorTool.ADJUST -> Icons.Filled.Tune
    EditorTool.CROP -> Icons.Filled.Crop
    EditorTool.CANVAS -> Icons.Filled.AspectRatio
    EditorTool.TEXT -> Icons.Filled.TextFields
    EditorTool.STICKER -> Icons.Filled.EmojiEmotions
    EditorTool.TRANSITIONS -> Icons.Filled.SwapHoriz
    EditorTool.EFFECTS -> Icons.Filled.AutoAwesome
    EditorTool.KEYFRAME -> Icons.Filled.Animation
}
