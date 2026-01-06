package com.openautoglm.agent.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openautoglm.agent.inference.DownloadMirror
import com.openautoglm.agent.inference.ModelFormat
import com.openautoglm.agent.inference.ModelInfo
import com.openautoglm.agent.inference.NetworkTestResult
import com.openautoglm.agent.ui.viewmodels.DownloadProgressInfo
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

        // Mirror Selection Card
        MirrorSelectionCard(
            selectedMirror = uiState.selectedMirror,
            networkTestResults = uiState.networkTestResults,
            isTestingNetwork = uiState.isTestingNetwork,
            onMirrorSelected = { viewModel.setMirror(it) },
            onTestNetwork = { viewModel.testNetworkConnectivity() },
            onAutoSelect = { viewModel.autoSelectBestMirror() }
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

        // Info banner about offline models
        if (uiState.downloadedModels.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "For Offline Use / 离线使用",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Download a Gemma model (MediaPipe) for offline inference. These models work immediately without additional setup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "下载 Gemma 模型（MediaPipe）即可离线使用，无需额外配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredModels, key = { it.id }) { model ->
                // Check if this gated model needs HuggingFace token
                val needsHfToken = model.format == ModelFormat.MEDIAPIPE && !uiState.hasHuggingFaceToken
                ModelCard(
                    model = model,
                    isDownloaded = uiState.downloadedModels.contains(model.id),
                    isRecommended = model.id == uiState.recommendedModelId,
                    downloadProgress = uiState.downloadProgress[model.id],
                    progressInfo = uiState.downloadProgressInfo[model.id],
                    error = uiState.downloadErrors[model.id],
                    needsHfToken = needsHfToken,
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
private fun MirrorSelectionCard(
    selectedMirror: DownloadMirror,
    networkTestResults: Map<DownloadMirror, NetworkTestResult>,
    isTestingNetwork: Boolean,
    onMirrorSelected: (DownloadMirror) -> Unit,
    onTestNetwork: () -> Unit,
    onAutoSelect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download Source / 下载源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (isTestingNetwork) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = onTestNetwork,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Test Network",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mirror options
            DownloadMirror.entries.forEach { mirror ->
                val testResult = networkTestResults[mirror]
                val isSelected = selectedMirror == mirror

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    onClick = { onMirrorSelected(mirror) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mirror.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = mirror.displayNameZh,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Status indicator
                        when (testResult) {
                            is NetworkTestResult.Testing -> {
                                Text(
                                    text = "Testing...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is NetworkTestResult.Success -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Connected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${testResult.latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            is NetworkTestResult.Failed -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Failed",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Failed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "Not tested",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-select button
            OutlinedButton(
                onClick = onAutoSelect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTestingNetwork
            ) {
                Text("Auto-select Best / 自动选择最佳")
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
    progressInfo: DownloadProgressInfo?,
    error: String?,
    needsHfToken: Boolean = false,
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

                    // Format badge with offline status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (model.format) {
                                ModelFormat.GGUF -> "llama.cpp (GGUF)"
                                ModelFormat.MEDIAPIPE -> "MediaPipe"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Offline status indicator
                        if (model.format == ModelFormat.MEDIAPIPE) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        "Offline Ready",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                    labelColor = MaterialTheme.colorScheme.tertiary,
                                    leadingIconContentColor = MaterialTheme.colorScheme.tertiary
                                )
                            )
                        }
                    }
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

            // HuggingFace token warning for gated models (Gemma)
            if (needsHfToken && !isDownloaded) {
                val context = LocalContext.current
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HuggingFace Token Required / 需要令牌",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Step 1: Accept License
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/litert-community/Gemma3-1B-IT"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1. Accept License / 接受许可证", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Step 2: Create Token
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("2. Create Token / 创建令牌", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "3. Enter token in Settings / 在设置中输入令牌",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            // GGUF warning - native library required
            if (model.format == ModelFormat.GGUF) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Requires native library setup",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "For offline use, download a Gemma model (MediaPipe) instead",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "离线使用请下载 Gemma 模型（MediaPipe）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Error message with network hint
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
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Download Failed",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onDismissError) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onDownloadClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Retry")
                                }
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
                            // Show size progress if available
                            if (progressInfo != null && progressInfo.totalBytes > 0) {
                                Text(
                                    text = "${formatBytes(progressInfo.downloadedBytes)} / ${formatBytes(progressInfo.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "Downloading...",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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

/**
 * Formats bytes to human-readable string (e.g., "1.5 GB", "256 MB").
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000L -> String.format("%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
