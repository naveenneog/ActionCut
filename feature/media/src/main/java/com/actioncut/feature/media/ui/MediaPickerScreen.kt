package com.actioncut.feature.media.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.actioncut.core.common.time.TimeFormatter
import com.actioncut.core.designsystem.component.EmptyState
import com.actioncut.core.designsystem.component.PrimaryButton
import com.actioncut.core.designsystem.component.SelectableChip
import com.actioncut.core.designsystem.component.clickableNoRipple
import com.actioncut.core.domain.repository.MediaFilter
import com.actioncut.core.model.MediaItem
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MediaPickerScreen(
    onProjectCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MediaPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(permissionState.allPermissionsGranted) {
        viewModel.onPermissionResult(permissionState.allPermissionsGranted)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New Project", fontWeight = FontWeight.SemiBold) },
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
        bottomBar = {
            if (uiState.selected.isNotEmpty()) {
                SelectionBar(
                    count = uiState.selected.size,
                    totalDurationMs = uiState.selected.sumOf { it.durationMs },
                    isCreating = uiState.isCreating,
                    name = uiState.projectName,
                    onNameChange = viewModel::setProjectName,
                    onCreate = { viewModel.createProject(onCreated = onProjectCreated) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            FilterRow(selected = uiState.filter, onSelect = viewModel::setFilter)

            when {
                !uiState.permissionGranted -> EmptyState(
                    icon = Icons.Filled.PermMedia,
                    title = "Access your media",
                    description = "Grant access to import videos and photos for editing.",
                    actionLabel = "Grant access",
                    onAction = { permissionState.launchMultiplePermissionRequest() },
                )

                uiState.isEmpty -> EmptyState(
                    icon = Icons.Filled.PermMedia,
                    title = "No media found",
                    description = "Capture a video or photo, then come back to start editing.",
                )

                else -> MediaGrid(
                    media = uiState.media,
                    selectionIndexOf = uiState::selectionIndexOf,
                    onToggle = viewModel::toggleSelection,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(selected: MediaFilter, onSelect: (MediaFilter) -> Unit) {
    val filters = listOf(
        MediaFilter.ALL to "All",
        MediaFilter.VIDEO to "Video",
        MediaFilter.IMAGE to "Photo",
        MediaFilter.AUDIO to "Audio",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters.size) { index ->
            val (filter, label) = filters[index]
            SelectableChip(label = label, selected = selected == filter, onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun MediaGrid(
    media: List<MediaItem>,
    selectionIndexOf: (MediaItem) -> Int,
    onToggle: (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(media, key = { it.id }) { item ->
            MediaCell(
                item = item,
                selectionIndex = selectionIndexOf(item),
                onClick = { onToggle(item) },
            )
        }
    }
}

@Composable
private fun MediaCell(item: MediaItem, selectionIndex: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val selected = selectionIndex >= 0
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .apply {
                if (item.isVideo) {
                    videoFrameMillis(1_000)
                    decoderFactory(VideoFrameDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableNoRipple { onClick() },
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (item.durationMs > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            ) {
                Text(
                    text = TimeFormatter.clock(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
        }
        SelectionBadge(
            selectionIndex = selectionIndex,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

@Composable
private fun SelectionBadge(selectionIndex: Int, modifier: Modifier = Modifier) {
    val selected = selectionIndex >= 0
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = (selectionIndex + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    totalDurationMs: Long,
    isCreating: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text("Project name") },
                placeholder = { Text("Name your project") },
                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$count selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (totalDurationMs > 0) {
                        Text(
                            TimeFormatter.short(totalDurationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PrimaryButton(
                    text = if (isCreating) "Creating…" else "Create",
                    onClick = onCreate,
                    enabled = !isCreating,
                )
            }
        }
    }
}
