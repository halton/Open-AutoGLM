package com.openautoglm.agent.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openautoglm.agent.R
import com.openautoglm.agent.agent.AgentState
import com.openautoglm.agent.ui.components.TaskProgressComponent
import com.openautoglm.agent.ui.components.VoiceInputIconButton
import com.openautoglm.agent.ui.viewmodels.TaskViewModel
import com.openautoglm.agent.voice.AudioPermissionState
import com.openautoglm.agent.voice.PermissionHandler
import com.openautoglm.agent.voice.PermissionRationaleDialog
import com.openautoglm.agent.voice.VoiceInputState

/**
 * Main screen for task creation and execution.
 *
 * Features:
 * - Text input for task description
 * - Start/stop/pause controls
 * - Real-time progress display
 * - Task result display
 * - Support for pre-filled task descriptions (e.g., from redo)
 *
 * @param viewModel The TaskViewModel instance
 * @param initialTaskDescription Optional pre-filled task description (e.g., from redo action)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel = viewModel(),
    initialTaskDescription: String? = null
) {
    val context = LocalContext.current
    val agentState by viewModel.agentState.collectAsState()
    var taskInput by remember { mutableStateOf(initialTaskDescription ?: "") }
    val scrollState = rememberScrollState()

    // Update taskInput if initialTaskDescription changes (e.g., from redo)
    LaunchedEffect(initialTaskDescription) {
        if (!initialTaskDescription.isNullOrBlank()) {
            taskInput = initialTaskDescription
        }
    }

    // Voice input state
    val voiceInputState by viewModel.voiceInputState.collectAsState()
    val audioPermissionState by viewModel.audioPermissionState.collectAsState()
    val isVoiceAvailable by viewModel.isVoiceInputAvailable.collectAsState()

    // Refresh voice availability on screen start
    LaunchedEffect(Unit) {
        viewModel.refreshVoiceInputAvailability()
    }

    // Permission rationale dialog state
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val newState = if (isGranted) {
            AudioPermissionState.GRANTED
        } else {
            PermissionHandler.checkAudioPermissionState(context)
        }
        viewModel.updateAudioPermissionState(newState)

        // Start voice input if permission was granted
        if (isGranted) {
            viewModel.startVoiceInput()
        }
    }

    // Handle voice input result - update task input when transcription is ready
    LaunchedEffect(voiceInputState) {
        when (val state = voiceInputState) {
            is VoiceInputState.Result -> {
                if (state.transcription.isNotBlank()) {
                    taskInput = state.transcription
                }
                viewModel.resetVoiceInput()
            }
            else -> { /* Other states handled by UI */ }
        }
    }

    // Voice button click handler
    val onVoiceButtonClick: () -> Unit = {
        when (voiceInputState) {
            is VoiceInputState.Listening -> {
                // Stop listening and process result
                viewModel.stopVoiceInput()
            }
            is VoiceInputState.Processing -> {
                // Cancel processing - user wants to abort
                viewModel.cancelVoiceInput()
            }
            else -> {
                // Check permission and start listening
                when (audioPermissionState) {
                    AudioPermissionState.GRANTED -> {
                        viewModel.startVoiceInput()
                    }
                    AudioPermissionState.NOT_REQUESTED,
                    AudioPermissionState.DENIED_SHOW_RATIONALE,
                    AudioPermissionState.UNKNOWN -> {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    AudioPermissionState.PERMANENTLY_DENIED -> {
                        showPermissionRationale = true
                    }
                }
            }
        }
    }

    // Permission rationale dialog
    if (showPermissionRationale) {
        PermissionRationaleDialog(
            onDismiss = { showPermissionRationale = false },
            onOpenSettings = {
                showPermissionRationale = false
                PermissionHandler.openAppSettings(context)
            }
        )
    }

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

        // Task input field with voice input button
        OutlinedTextField(
            value = taskInput,
            onValueChange = { taskInput = it },
            label = { Text("What would you like me to do?") },
            placeholder = { Text("e.g., Find the best price for iPhone 15 across shopping apps") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            enabled = agentState is AgentState.Idle,
            trailingIcon = {
                // Show voice button when:
                // 1. Voice is available AND agent is idle (normal case)
                // 2. Voice input is currently active (listening or processing)
                val isVoiceActive = voiceInputState is VoiceInputState.Listening ||
                                    voiceInputState is VoiceInputState.Processing
                if (isVoiceAvailable && (agentState is AgentState.Idle || isVoiceActive)) {
                    VoiceInputIconButton(
                        voiceState = voiceInputState,
                        permissionState = audioPermissionState,
                        onClick = onVoiceButtonClick,
                        enabled = agentState is AgentState.Idle || isVoiceActive
                    )
                }
            },
            supportingText = {
                // Show voice input status
                when (val state = voiceInputState) {
                    is VoiceInputState.Listening -> {
                        Text(
                            text = if (state.partialText.isNotEmpty()) {
                                state.partialText
                            } else {
                                stringResource(R.string.voice_listening)
                            },
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is VoiceInputState.Processing -> {
                        Text(
                            text = stringResource(R.string.voice_processing),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    is VoiceInputState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> { /* No supporting text for Idle or Result */ }
                }
            }
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
                            android.util.Log.i("TaskScreen", "===== START TASK BUTTON CLICKED =====")
                            android.util.Log.i("TaskScreen", "Task input: '$taskInput'")
                            if (taskInput.isNotBlank()) {
                                android.util.Log.i("TaskScreen", "Input valid, calling viewModel.startTask()")
                                viewModel.startTask(taskInput)
                            } else {
                                android.util.Log.w("TaskScreen", "Task input is blank!")
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓ Task Completed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(
                                onClick = {
                                    viewModel.reset()
                                    taskInput = ""
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            IconButton(
                                onClick = {
                                    viewModel.reset()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
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
