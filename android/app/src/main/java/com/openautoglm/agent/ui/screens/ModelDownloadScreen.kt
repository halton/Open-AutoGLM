package com.openautoglm.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openautoglm.agent.inference.ModelFormat
import com.openautoglm.agent.inference.ModelInfo
import com.openautoglm.agent.ui.viewmodels.ModelDownloadViewModel
import com.openautoglm.agent.ui.viewmodels.ModelFilter

/**
 * Screen for downloading and managing on-device ML models.
 *
 * Displays available models for download, download progress,
 * and allows deletion of downloaded models.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    viewModel: ModelDownloadViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf(ModelFilter.ALL) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "On-Device Models",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Storage Info Card
        StorageInfoCard(
            totalModelSizeMB = uiState.totalModelSizeMB,
            availableSpaceMB = uiState.availableSpaceMB,
            downloadedCount = uiState.downloadedModels.size
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Filter Chips
        FilterChips(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Model List
        val filteredModels = when (selectedFilter) {
            ModelFilter.ALL -> uiState.availableModels
            ModelFilter.GGUF -> uiState.availableModels.filter { it.format == ModelFormat.GGUF }
            ModelFilter.MEDIAPIPE -> uiState.availableModels.filter { it.format == ModelFormat.MEDIAPIPE }
            ModelFilter.DOWNLOADED -> uiState.availableModels.filter { uiState.downloadedModels.contains(it.id) }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredModels, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    isDownloaded = uiState.downloadedModels.contains(model.id),
                    isRecommended = model.id == uiState.recommendedModelId,
                    downloadProgress = uiState.downloadProgress[model.id],
                    error = uiState.downloadErrors[model.id],
                    onDownloadClick = { viewModel.startDownload(model.id) },
                    onCancelClick = { viewModel.cancelDownload(model.id) },
                    onDeleteClick = { showDeleteDialog = model.id },
                    onDismissError = { viewModel.clearError(model.id) }
                )
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { modelId ->
        val model = uiState.availableModels.find { it.id == modelId }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model?") },
            text = {
                Text("Are you sure you want to delete ${model?.displayName}? You can download it again later.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(modelId)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StorageInfoCard(
    totalModelSizeMB: Long,
    availableSpaceMB: Long,
    downloadedCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$downloadedCount models",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Storage Used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSize(totalModelSizeMB),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSize(availableSpaceMB),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (availableSpaceMB < 2000) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(
    selectedFilter: ModelFilter,
    onFilterSelected: (ModelFilter) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == ModelFilter.ALL,
            onClick = { onFilterSelected(ModelFilter.ALL) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedFilter == ModelFilter.GGUF,
            onClick = { onFilterSelected(ModelFilter.GGUF) },
            label = { Text("AutoGLM") }
        )
        FilterChip(
            selected = selectedFilter == ModelFilter.MEDIAPIPE,
            onClick = { onFilterSelected(ModelFilter.MEDIAPIPE) },
            label = { Text("Gemma") }
        )
        FilterChip(
            selected = selectedFilter == ModelFilter.DOWNLOADED,
            onClick = { onFilterSelected(ModelFilter.DOWNLOADED) },
            label = { Text("Downloaded") }
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isDownloaded: Boolean,
    isRecommended: Boolean,
    downloadProgress: Float?,
    error: String?,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismissError: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended && !isDownloaded)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRecommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Recommended", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    labelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Format badge
                    Text(
                        text = when (model.format) {
                            ModelFormat.GGUF -> "llama.cpp (GGUF)"
                            ModelFormat.MEDIAPIPE -> "MediaPipe"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status/Size
                Column(horizontalAlignment = Alignment.End) {
                    if (isDownloaded) {
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = formatSize(model.sizeMB),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Chinese description
            Text(
                text = model.descriptionZh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Requirements
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "RAM: ${model.minRamMB / 1024}GB+",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (model.supportsVision) {
                    Text(
                        text = "Vision: Yes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Error message
            AnimatedVisibility(
                visible = error != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                error?.let {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissError) {
                                Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Download Progress
            AnimatedVisibility(
                visible = downloadProgress != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                downloadProgress?.let { progress ->
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading...",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    downloadProgress != null -> {
                        OutlinedButton(
                            onClick = onCancelClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                    isDownloaded -> {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        Button(onClick = onDownloadClick) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats size in MB to human-readable string.
 */
private fun formatSize(sizeMB: Long): String {
    return when {
        sizeMB >= 1024 -> String.format("%.1f GB", sizeMB / 1024.0)
        else -> "$sizeMB MB"
    }
}
