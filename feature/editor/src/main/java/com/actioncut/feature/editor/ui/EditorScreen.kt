package com.actioncut.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actioncut.core.common.time.TimeFormatter
import com.actioncut.core.designsystem.component.PrimaryButton
import com.actioncut.core.designsystem.component.rememberHaptic
import com.actioncut.feature.editor.ui.preview.PreviewPlayer
import com.actioncut.feature.editor.ui.timeline.EditorTimeline
import com.actioncut.feature.editor.ui.tools.EditorToolbar
import com.actioncut.feature.editor.ui.tools.ToolPanel

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.addAudioAtPlayhead(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        EditorTopBar(
            title = uiState.projectName,
            canUndo = uiState.canUndo,
            canRedo = uiState.canRedo,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onBack = onBack,
            onExport = onExport,
        )

        PreviewPlayer(
            player = viewModel.playerController.player,
            aspectRatio = uiState.aspectRatio,
            isPlaying = uiState.isPlaying,
            onTogglePlay = viewModel::togglePlayPause,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        PlaybackBar(
            playheadMs = uiState.playheadMs,
            durationMs = uiState.durationMs,
            isPlaying = uiState.isPlaying,
            onTogglePlay = viewModel::togglePlayPause,
            onZoomIn = { viewModel.setZoom(uiState.pxPerSecond * 1.4f) },
            onZoomOut = { viewModel.setZoom(uiState.pxPerSecond / 1.4f) },
        )

        EditorTimeline(
            timeline = uiState.timeline,
            playheadMs = uiState.playheadMs,
            durationMs = uiState.durationMs,
            pxPerSecond = uiState.pxPerSecond,
            isPlaying = uiState.isPlaying,
            selectedClipId = uiState.selectedClipId,
            onScrub = viewModel::seekTo,
            onSelectClip = viewModel::selectClip,
            onTrimStart = viewModel::trimStart,
            onTrimEnd = viewModel::trimEnd,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )

        Box(modifier = Modifier.navigationBarsPadding()) {
            val tool = uiState.activeTool
            if (tool != null && tool.opensPanel) {
                ToolPanel(
                    tool = tool,
                    selectedClip = uiState.selectedClip,
                    onClose = { viewModel.setActiveTool(null) },
                    onSpeed = viewModel::setSpeed,
                    onVolume = viewModel::setVolume,
                    onFilter = viewModel::setFilter,
                    onAdjust = viewModel::setAdjustments,
                    onAddText = viewModel::addTextAtPlayhead,
                    onTransition = viewModel::setTransition,
                    onAddEffect = viewModel::addEffect,
                )
            } else {
                EditorToolbar(
                    activeTool = uiState.activeTool,
                    onToolSelected = { tool ->
                        if (tool == EditorTool.AUDIO) {
                            audioPicker.launch(arrayOf("audio/*"))
                        } else {
                            viewModel.setActiveTool(tool)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title.ifBlank { "Editor" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
        }
        PrimaryButton(text = "Export", onClick = onExport, leadingIcon = Icons.Filled.Add)
    }
}

@Composable
private fun PlaybackBar(
    playheadMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    val haptic = rememberHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { haptic.click(); onTogglePlay() }) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = "${TimeFormatter.precise(playheadMs)} / ${TimeFormatter.clock(durationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onZoomOut) {
            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onZoomIn) {
            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in", modifier = Modifier.size(20.dp))
        }
    }
}
