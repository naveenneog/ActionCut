package com.actioncut.feature.export.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actioncut.core.designsystem.component.LabeledProgressBar
import com.actioncut.core.designsystem.component.PrimaryButton
import com.actioncut.core.designsystem.component.SecondaryButton
import com.actioncut.core.designsystem.component.SectionHeader
import com.actioncut.core.designsystem.component.SelectableChip
import com.actioncut.core.model.ExportPreset
import com.actioncut.core.model.ExportSettings
import com.actioncut.core.model.ExportState
import com.actioncut.core.model.FrameRate
import com.actioncut.core.model.Resolution
import com.actioncut.core.model.VideoFormat
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Export", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val state = uiState.exportState) {
                is ExportState.Completed -> CompletedContent(
                    onShare = { shareVideo(context, state.outputUri) },
                    onDone = onBack,
                )

                is ExportState.InProgress -> ProgressContent(
                    progress = state.progress,
                    onCancel = viewModel::cancelExport,
                )

                is ExportState.Failed -> FailedContent(
                    message = state.message,
                    onRetry = { viewModel.reset(); viewModel.startExport() },
                )

                else -> SettingsContent(
                    settings = uiState.settings,
                    selectedPreset = uiState.selectedPreset,
                    onPreset = viewModel::setPreset,
                    onResolution = viewModel::setResolution,
                    onFrameRate = viewModel::setFrameRate,
                    onFormat = viewModel::setFormat,
                    onExport = viewModel::startExport,
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    settings: ExportSettings,
    selectedPreset: ExportPreset?,
    onPreset: (ExportPreset) -> Unit,
    onResolution: (Resolution) -> Unit,
    onFrameRate: (FrameRate) -> Unit,
    onFormat: (VideoFormat) -> Unit,
    onExport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("Platform")
        ChipRow(ExportPreset.entries.toList(), selectedPreset, { it.displayName }, onPreset)

        SectionHeader("Resolution")
        ChipRow(Resolution.entries.toList(), settings.resolution, { it.shortLabel }, onResolution)

        SectionHeader("Frame rate")
        ChipRow(FrameRate.entries.toList(), settings.frameRate, { "${it.fps} fps" }, onFrameRate)

        SectionHeader("Format")
        ChipRow(VideoFormat.entries.toList(), settings.format, ::formatLabel, onFormat)

        Text(
            text = "${settings.resolution.width}×${settings.resolution.height} · " +
                "${(settings.effectiveBitrate() / 1_000_000f).roundToInt()} Mbps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        PrimaryButton(
            text = "Export video",
            onClick = onExport,
            leadingIcon = Icons.Filled.Movie,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            SelectableChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun ProgressContent(progress: Float, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 40.dp)) {
        LabeledProgressBar(
            progress = progress,
            label = "Exporting… ${(progress * 100).roundToInt()}%",
        )
        SecondaryButton(text = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun CompletedContent(onShare: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(72.dp),
        )
        Text(
            "Saved to your Gallery",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Movies/ActionCut · ready to share",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Done", onClick = onDone)
            PrimaryButton(text = "Share", onClick = onShare)
        }
    }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
        )
        Text(
            "Export failed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
        )
        PrimaryButton(text = "Retry", onClick = onRetry, modifier = Modifier.padding(top = 24.dp))
    }
}

private fun formatLabel(format: VideoFormat): String = when (format) {
    VideoFormat.MP4_H264 -> "MP4 · H.264"
    VideoFormat.MP4_H265 -> "MP4 · H.265"
    VideoFormat.WEBM_VP9 -> "WebM · VP9"
}

private fun shareVideo(context: android.content.Context, uriString: String) {
    runCatching {
        val uri = if (uriString.startsWith("content://")) {
            android.net.Uri.parse(uriString)
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uriString))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share video"))
    }
}
