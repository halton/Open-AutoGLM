package com.openautoglm.agent.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.openautoglm.agent.AutoGLMApplication
import com.openautoglm.agent.R
import com.openautoglm.agent.accessibility.ScreenCaptureManager
import com.openautoglm.agent.core.ActionExecutor
import com.openautoglm.agent.core.AgentLoop
import com.openautoglm.agent.core.ScreenStateManager
import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.AppDatabase
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.TaskStatus
import com.openautoglm.agent.model.InferenceRouter
import com.openautoglm.agent.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Foreground service that hosts the agent execution loop.
 *
 * This service manages the lifecycle of agent tasks, running them in the background
 * while providing user visibility through persistent notifications. It supports:
 * - Starting new tasks from descriptions
 * - Pausing and resuming active tasks
 * - Cancelling tasks
 * - Broadcasting status updates via StateFlow
 *
 * The service runs as a foreground service to ensure Android does not kill it
 * during long-running task executions.
 */
class AgentService : Service() {

    // Binder for local clients to interact with the service
    private val binder = AgentBinder()

    // Coroutine scope tied to service lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current task execution job
    private var currentJob: Job? = null

    // Repository for task persistence
    private lateinit var repository: AgentRepository

    // Agent loop for executing tasks
    private lateinit var agentLoop: AgentLoop

    // Current task state
    private var currentTask: Task? = null

    // Mutable state flow for task status updates
    private val _taskState = MutableStateFlow<TaskState>(TaskState.Idle)

    /**
     * Public StateFlow for observing task status changes.
     * Clients can collect this flow to receive real-time updates.
     */
    val taskState: StateFlow<TaskState> = _taskState.asStateFlow()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AgentService"

        // Intent actions
        const val ACTION_START_TASK = "com.openautoglm.agent.START_TASK"
        const val ACTION_PAUSE_TASK = "com.openautoglm.agent.PAUSE_TASK"
        const val ACTION_RESUME_TASK = "com.openautoglm.agent.RESUME_TASK"
        const val ACTION_CANCEL_TASK = "com.openautoglm.agent.CANCEL_TASK"

        // Intent extras
        const val EXTRA_TASK_DESCRIPTION = "task_description"
        const val EXTRA_TASK_ID = "task_id"

        /**
         * Starts the AgentService as a foreground service.
         *
         * @param context Android context
         */
        fun start(context: Context) {
            val intent = Intent(context, AgentService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Stops the AgentService.
         *
         * @param context Android context
         */
        fun stop(context: Context) {
            val intent = Intent(context, AgentService::class.java)
            context.stopService(intent)
        }

        /**
         * Starts a task via intent.
         *
         * @param context Android context
         * @param taskDescription Description of the task to execute
         */
        fun startTask(context: Context, taskDescription: String) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_START_TASK
                putExtra(EXTRA_TASK_DESCRIPTION, taskDescription)
            }
            context.startForegroundService(intent)
        }

        /**
         * Sends a pause task command to the service.
         *
         * @param context Android context
         */
        fun pauseTask(context: Context) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_PAUSE_TASK
            }
            context.startService(intent)
        }

        /**
         * Sends a resume task command to the service.
         *
         * @param context Android context
         */
        fun resumeTask(context: Context) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_RESUME_TASK
            }
            context.startService(intent)
        }

        /**
         * Sends a cancel task command to the service.
         *
         * @param context Android context
         */
        fun cancelTask(context: Context) {
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_CANCEL_TASK
            }
            context.startService(intent)
        }
    }

    // Screen capture manager (needs to be initialized with media projection)
    private var screenCaptureManager: ScreenCaptureManager? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize repository
        val database = AppDatabase.getInstance(applicationContext)
        repository = AgentRepository.getInstance(database)

        // Note: AgentLoop will be initialized lazily when screen capture is available
        // since it requires ScreenCaptureManager which needs MediaProjection permission

        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, createNotification("Agent ready", "Waiting for tasks"))
    }

    /**
     * Initializes the agent loop with the given screen capture manager.
     * Must be called after media projection permission is granted.
     *
     * @param captureManager The ScreenCaptureManager instance
     */
    fun initializeWithScreenCapture(captureManager: ScreenCaptureManager) {
        screenCaptureManager = captureManager

        // Now we can create all dependencies for AgentLoop
        val inferenceRouter = InferenceRouter(repository)
        val actionExecutor = ActionExecutor(applicationContext)
        val screenStateManager = ScreenStateManager(captureManager)

        agentLoop = AgentLoop(
            repository = repository,
            inferenceRouter = inferenceRouter,
            actionExecutor = actionExecutor,
            screenStateManager = screenStateManager
        )

        // Set up progress callback to receive updates
        agentLoop.setProgressCallback(createProgressCallback())
    }

    /**
     * Creates a progress callback to handle agent loop events.
     */
    private fun createProgressCallback(): AgentLoop.ProgressCallback {
        return object : AgentLoop.ProgressCallback {
            override fun onStepStarted(step: Int, task: Task) {
                serviceScope.launch(Dispatchers.Main) {
                    _taskState.value = TaskState.Running(task, step, "Step $step starting...")
                    updateNotification("Task running", "Step $step: Starting...")
                }
            }

            override fun onScreenCaptured(step: Int, screenStateId: String) {
                // Screen captured, no UI update needed
            }

            override fun onInferenceCompleted(step: Int, response: String) {
                serviceScope.launch(Dispatchers.Main) {
                    currentTask?.let { task ->
                        _taskState.value = TaskState.Running(task, step, "Analyzing...")
                        updateNotification("Task running", "Step $step: Analyzing...")
                    }
                }
            }

            override fun onActionExecuting(step: Int, action: Action) {
                serviceScope.launch(Dispatchers.Main) {
                    currentTask?.let { task ->
                        val description = "Executing ${action.type.name.lowercase()}"
                        _taskState.value = TaskState.Running(task, step, description)
                        updateNotification("Task running", "Step $step: $description")
                    }
                }
            }

            override fun onActionCompleted(step: Int, action: Action, success: Boolean) {
                // Action completed, will be followed by next step or completion
            }

            override fun onTaskCompleted(task: Task) {
                serviceScope.launch(Dispatchers.Main) {
                    currentTask = task
                    val result = task.result ?: "Task completed successfully"
                    _taskState.value = TaskState.Completed(task, result)
                    updateNotification("Task completed", result.take(50))
                    showCompletionNotification(result)
                }
            }

            override fun onTaskFailed(task: Task, error: String) {
                serviceScope.launch(Dispatchers.Main) {
                    currentTask = task
                    _taskState.value = TaskState.Failed(task, error)
                    updateNotification("Task failed", error.take(50))
                }
            }

            override fun onTaskCancelled(task: Task) {
                serviceScope.launch(Dispatchers.Main) {
                    currentTask = null
                    _taskState.value = TaskState.Cancelled(task)
                    updateNotification("Task cancelled", "Ready for new tasks")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TASK -> {
                val description = intent.getStringExtra(EXTRA_TASK_DESCRIPTION)
                if (!description.isNullOrBlank()) {
                    startTask(description)
                }
            }
            ACTION_PAUSE_TASK -> pauseTask()
            ACTION_RESUME_TASK -> resumeTask()
            ACTION_CANCEL_TASK -> cancelTask()
        }

        // If killed by the system, restart with the last intent
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any running task
        currentJob?.cancel()
        // Cancel the service scope
        serviceScope.cancel()
    }

    /**
     * Creates and starts a new task.
     *
     * @param taskDescription Description of the task to execute
     */
    fun startTask(taskDescription: String) {
        // Check if agent loop is initialized
        if (!::agentLoop.isInitialized) {
            _taskState.value = TaskState.Error("Agent not initialized. Please grant screen capture permission.")
            updateNotification("Error", "Screen capture permission required")
            return
        }

        // Cancel any existing task
        currentJob?.cancel()

        serviceScope.launch {
            try {
                // Create new task entity
                val task = Task(
                    id = UUID.randomUUID(),
                    description = taskDescription,
                    status = TaskStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )

                // Persist task
                repository.insertTask(task)
                currentTask = task

                // Update state
                _taskState.value = TaskState.Starting(task)
                updateNotification("Starting task", taskDescription.take(50))

                // Execute the agent loop (it handles task status updates internally)
                currentJob = serviceScope.launch(Dispatchers.Default) {
                    executeAgentLoop(task)
                }
            } catch (e: Exception) {
                handleTaskError(e)
            }
        }
    }

    /**
     * Pauses the currently running task.
     */
    fun pauseTask() {
        val task = currentTask ?: return
        if (task.status != TaskStatus.RUNNING) return

        serviceScope.launch {
            try {
                // Pause the execution job
                currentJob?.cancel()

                // Update task status
                val pausedTask = task.copy(status = TaskStatus.PAUSED)
                repository.updateTask(pausedTask)
                currentTask = pausedTask

                _taskState.value = TaskState.Paused(pausedTask)
                updateNotification("Task paused", pausedTask.description.take(50))
            } catch (e: Exception) {
                handleTaskError(e)
            }
        }
    }

    /**
     * Resumes a paused task.
     */
    fun resumeTask() {
        val task = currentTask ?: return
        if (task.status != TaskStatus.PAUSED) return

        serviceScope.launch {
            try {
                // Update task status
                val runningTask = task.copy(status = TaskStatus.RUNNING)
                repository.updateTask(runningTask)
                currentTask = runningTask

                _taskState.value = TaskState.Running(runningTask, runningTask.stepCount, "Resuming...")
                updateNotification("Task resumed", "Step ${runningTask.stepCount}: Resuming...")

                // Resume execution
                currentJob = serviceScope.launch(Dispatchers.Default) {
                    executeAgentLoop(runningTask)
                }
            } catch (e: Exception) {
                handleTaskError(e)
            }
        }
    }

    /**
     * Cancels the current task.
     */
    fun cancelTask() {
        val task = currentTask ?: return

        serviceScope.launch {
            try {
                // Cancel the execution job
                currentJob?.cancel()

                // Update task status
                val cancelledTask = task.copy(
                    status = TaskStatus.CANCELLED,
                    completedAt = System.currentTimeMillis()
                )
                repository.updateTask(cancelledTask)
                currentTask = null

                _taskState.value = TaskState.Cancelled(cancelledTask)
                updateNotification("Task cancelled", "Ready for new tasks")

                // Reset to idle after a brief delay
                serviceScope.launch {
                    kotlinx.coroutines.delay(2000)
                    if (_taskState.value is TaskState.Cancelled) {
                        _taskState.value = TaskState.Idle
                    }
                }
            } catch (e: Exception) {
                handleTaskError(e)
            }
        }
    }

    /**
     * Executes the agent loop for the given task.
     * This runs on a background dispatcher.
     */
    private suspend fun executeAgentLoop(task: Task) {
        try {
            // The AgentLoop uses a ProgressCallback for status updates
            // which we set up in initializeWithScreenCapture()
            val completedTask = agentLoop.executeTask(task)
            currentTask = completedTask
            // Status updates are handled by the ProgressCallback
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Task was cancelled, state already updated by callback
            throw e
        } catch (e: Exception) {
            handleTaskError(e)
        }
    }

    /**
     * Handles task errors by updating state and notification.
     */
    private fun handleTaskError(error: Exception) {
        val task = currentTask

        if (task != null) {
            serviceScope.launch {
                val failedTask = task.copy(
                    status = TaskStatus.FAILED,
                    completedAt = System.currentTimeMillis(),
                    errorMessage = error.message
                )
                repository.updateTask(failedTask)
                currentTask = failedTask

                _taskState.value = TaskState.Failed(failedTask, error.message ?: "Unknown error")
                updateNotification("Task failed", error.message?.take(50) ?: "Unknown error")
            }
        } else {
            _taskState.value = TaskState.Error(error.message ?: "Unknown error")
            updateNotification("Error", error.message?.take(50) ?: "Unknown error")
        }
    }

    /**
     * Creates the foreground notification.
     */
    private fun createNotification(title: String, content: String): Notification {
        // Intent to open the main activity when notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause action
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentService::class.java).apply {
                action = ACTION_PAUSE_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel action
        val cancelIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AgentService::class.java).apply {
                action = ACTION_CANCEL_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AutoGLMApplication.CHANNEL_TASK_EXECUTION)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_pause, "Pause", pauseIntent)
            .addAction(R.drawable.ic_cancel, "Cancel", cancelIntent)
            .build()
    }

    /**
     * Updates the foreground notification with new content.
     */
    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Shows a high-priority notification when task is completed.
     */
    private fun showCompletionNotification(result: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AutoGLMApplication.CHANNEL_TASK_COMPLETION)
            .setContentTitle("Task Completed")
            .setContentText(result.take(100))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    /**
     * Shows a high-priority notification when user confirmation is required.
     */
    private fun showConfirmationNotification(message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AutoGLMApplication.CHANNEL_CONFIRMATION_REQUIRED)
            .setContentTitle("Confirmation Required")
            .setContentText(message.take(100))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    /**
     * Binder class for local service binding.
     */
    inner class AgentBinder : Binder() {
        /**
         * Returns the AgentService instance.
         */
        fun getService(): AgentService = this@AgentService
    }
}

/**
 * Sealed class representing the various states of a task.
 */
sealed class TaskState {
    /**
     * No task is active.
     */
    data object Idle : TaskState()

    /**
     * Task is being initialized.
     */
    data class Starting(val task: Task) : TaskState()

    /**
     * Task is actively running.
     */
    data class Running(
        val task: Task,
        val currentStep: Int,
        val stepDescription: String
    ) : TaskState()

    /**
     * Task is paused by user.
     */
    data class Paused(val task: Task) : TaskState()

    /**
     * Task completed successfully.
     */
    data class Completed(val task: Task, val result: String) : TaskState()

    /**
     * Task failed with an error.
     */
    data class Failed(val task: Task, val error: String) : TaskState()

    /**
     * Task was cancelled by user.
     */
    data class Cancelled(val task: Task) : TaskState()

    /**
     * Task requires user confirmation to proceed.
     */
    data class AwaitingConfirmation(val task: Task, val message: String) : TaskState()

    /**
     * General error state (no associated task).
     */
    data class Error(val message: String) : TaskState()
}
