package com.openautoglm.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.TaskStatus
import com.openautoglm.agent.ui.viewmodels.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for viewing task history.
 *
 * Displays all completed, failed, and cancelled tasks from the database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Task History",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (tasks.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear All")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Completed tasks will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Task list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskHistoryItem(
                        task = task,
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }

    // Clear all confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to delete all task history? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllTasks()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TaskHistoryItem(
    task: Task,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (task.status) {
                TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                TaskStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                TaskStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Text(
                text = when (task.status) {
                    TaskStatus.COMPLETED -> "✓"
                    TaskStatus.FAILED -> "✗"
                    TaskStatus.CANCELLED -> "○"
                    TaskStatus.RUNNING -> "▶"
                    TaskStatus.PAUSED -> "⏸"
                    TaskStatus.PENDING -> "…"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp)
            )

            // Task details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status text
                    Text(
                        text = task.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (task.status) {
                            TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    // Date
                    Text(
                        text = dateFormatter.format(Date(task.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Steps if applicable
                    if (task.stepCount > 0) {
                        Text(
                            text = "${task.stepCount} steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Result message if present
                task.result?.let { result ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Error message if present
                task.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
