package com.openautoglm.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openautoglm.agent.agent.AgentState
import com.openautoglm.agent.ui.components.TaskProgressComponent
import com.openautoglm.agent.ui.viewmodels.TaskViewModel

/**
 * Main screen for task creation and execution.
 *
 * Features:
 * - Text input for task description
 * - Start/stop/pause controls
 * - Real-time progress display
 * - Task result display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel = viewModel()
) {
    val context = LocalContext.current
    val agentState by viewModel.agentState.collectAsState()
    var taskInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Create Task",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Task input field
        OutlinedTextField(
            value = taskInput,
            onValueChange = { taskInput = it },
            label = { Text("What would you like me to do?") },
            placeholder = { Text("e.g., Find the best price for iPhone 15 across shopping apps") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            enabled = agentState is AgentState.Idle
        )

        // Quick suggestions
        if (agentState is AgentState.Idle) {
            Text(
                text = "Quick suggestions:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val suggestions = listOf(
                "Compare prices for [product] across Taobao, JD, and Amazon",
                "Order [food] from [restaurant] on Meituan",
                "Book a train from [city] to [city] on [date]",
                "Send a message to [contact] on WeChat"
            )

            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { taskInput = suggestion },
                    label = { Text(suggestion, maxLines = 1) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (agentState) {
                is AgentState.Idle -> {
                    Button(
                        onClick = {
                            if (taskInput.isNotBlank()) {
                                viewModel.startTask(taskInput)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = taskInput.isNotBlank()
                    ) {
                        Text("Start Task")
                    }
                }
                is AgentState.Running -> {
                    Button(
                        onClick = { viewModel.pauseTask() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Pause")
                    }
                    OutlinedButton(
                        onClick = { viewModel.cancelTask() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
                is AgentState.Paused -> {
                    Button(
                        onClick = { viewModel.resumeTask() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Resume")
                    }
                    OutlinedButton(
                        onClick = { viewModel.cancelTask() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
                is AgentState.Finished -> {
                    Button(
                        onClick = {
                            viewModel.reset()
                            taskInput = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("New Task")
                    }
                }
                is AgentState.Error -> {
                    Button(
                        onClick = {
                            viewModel.reset()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset")
                    }
                }
            }
        }

        Divider()

        // Progress and results
        when (val state = agentState) {
            is AgentState.Running -> {
                TaskProgressComponent(
                    taskDescription = state.description,
                    currentStep = state.step,
                    totalSteps = 50  // TODO: Get from config
                )
            }
            is AgentState.Paused -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Task Paused",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "The task has been paused. Click Resume to continue.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is AgentState.Finished -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✓ Task Completed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        state.message?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            is AgentState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            is AgentState.Idle -> {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🤖",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "Ready to help!",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Describe your task and I'll automate it for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
