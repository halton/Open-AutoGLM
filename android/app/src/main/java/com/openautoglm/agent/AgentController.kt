package com.openautoglm.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.AppDatabase
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.TaskStatus
import com.openautoglm.agent.service.AgentService
import com.openautoglm.agent.service.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * High-level API for controlling the VLM-powered Android agent.
 *
 * This controller provides a clean interface for the UI layer to interact with the agent,
 * managing task submission, lifecycle control, and status observation.
 *
 * Usage:
 * ```kotlin
 * val controller = AgentController.getInstance(context)
 *
 * // Submit a task
 * val taskId = controller.submitTask("Open WeChat and send a message to John")
 *
 * // Observe task status
 * controller.getTaskStatus(taskId).collect { task ->
 *     println("Task status: ${task?.status}")
 * }
 *
 * // Pause/resume agent
 * controller.pauseAgent()
 * controller.resumeAgent()
 * ```
 */
class AgentController private constructor(
    private val context: Context,
    private val repository: AgentRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Agent state management
    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    // Reference to the agent service (via binding)
    private var agentService: AgentService? = null
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    // Service connection for binding
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AgentService.AgentBinder
            agentService = binder?.getService()
            _isServiceConnected.value = true

            // Observe task state from service
            agentService?.let { svc ->
                scope.launch {
                    svc.taskState.collect { taskState ->
                        updateAgentStateFromTaskState(taskState)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            agentService = null
            _isServiceConnected.value = false
            _agentState.value = AgentState.IDLE
        }
    }

    /**
     * Updates the agent state based on the task state from the service.
     */
    private fun updateAgentStateFromTaskState(taskState: TaskState) {
        _agentState.value = when (taskState) {
            is TaskState.Idle -> AgentState.IDLE
            is TaskState.Starting -> AgentState.RUNNING
            is TaskState.Running -> AgentState.RUNNING
            is TaskState.Paused -> AgentState.PAUSED
            is TaskState.Completed -> AgentState.IDLE
            is TaskState.Failed -> AgentState.ERROR
            is TaskState.Cancelled -> AgentState.IDLE
            is TaskState.AwaitingConfirmation -> AgentState.PAUSED
            is TaskState.Error -> AgentState.ERROR
        }
    }

    /**
     * Submit a new task to the agent for execution.
     *
     * @param description The natural language description of the task to execute
     * @param maxSteps Maximum number of steps allowed for this task (default: 100)
     * @return The UUID of the created task
     * @throws IllegalArgumentException if description is empty or exceeds 1000 characters
     */
    fun submitTask(description: String, maxSteps: Int = 100): UUID {
        require(description.isNotBlank()) { "Task description cannot be blank" }
        require(description.length <= 1000) { "Task description cannot exceed 1000 characters" }
        require(maxSteps > 0) { "Max steps must be positive" }

        val task = Task(
            description = description.trim(),
            maxSteps = maxSteps,
            status = TaskStatus.PENDING
        )

        scope.launch {
            repository.insertTask(task)
            ensureServiceStarted()
            // Start task via intent
            AgentService.startTask(context, task.description)
        }

        return task.id
    }

    /**
     * Cancel a specific task.
     *
     * If the task is currently running, it will be stopped. If pending, it will be removed
     * from the queue. Completed or failed tasks cannot be cancelled.
     *
     * @param taskId The UUID of the task to cancel
     */
    fun cancelTask(taskId: UUID) {
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null && task.status in listOf(TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.PAUSED)) {
                repository.updateTask(
                    task.copy(
                        status = TaskStatus.CANCELLED,
                        completedAt = System.currentTimeMillis()
                    )
                )
                // Cancel via intent
                AgentService.cancelTask(context)
            }
        }
    }

    /**
     * Pause the agent's execution.
     *
     * The currently running task will be paused and can be resumed later.
     * Pending tasks remain in the queue.
     */
    fun pauseAgent() {
        _agentState.value = AgentState.PAUSED
        // Pause via intent
        AgentService.pauseTask(context)
    }

    /**
     * Resume the agent's execution after being paused.
     *
     * The previously paused task will continue from where it left off.
     */
    fun resumeAgent() {
        _agentState.value = AgentState.RUNNING
        // Resume via intent
        AgentService.resumeTask(context)
    }

    /**
     * Observe the status of a specific task.
     *
     * @param taskId The UUID of the task to observe
     * @return A Flow emitting the task whenever it changes, or null if not found
     */
    fun getTaskStatus(taskId: UUID): Flow<Task?> {
        return repository.getAllTasks().map { tasks ->
            tasks.find { it.id == taskId }
        }
    }

    /**
     * Observe all active tasks (PENDING, RUNNING, or PAUSED).
     *
     * @return A Flow emitting the list of active tasks
     */
    fun getActiveTasks(): Flow<List<Task>> = repository.getActiveTasks()

    /**
     * Get the history of completed, failed, and cancelled tasks.
     *
     * @return A Flow emitting the list of historical tasks
     */
    fun getTaskHistory(): Flow<List<Task>> {
        return repository.getAllTasks().map { tasks ->
            tasks.filter { task ->
                task.status in listOf(
                    TaskStatus.COMPLETED,
                    TaskStatus.FAILED,
                    TaskStatus.CANCELLED
                )
            }
        }
    }

    /**
     * Observe actions for a specific task.
     *
     * @param taskId The UUID of the task
     * @return A Flow emitting the list of actions, ordered by timestamp
     */
    fun getActionsForTask(taskId: UUID): Flow<List<Action>> {
        return repository.getActionsByTaskIdOrdered(taskId)
    }

    /**
     * Clear task history (completed, failed, and cancelled tasks).
     *
     * This removes historical tasks and their associated actions from the database.
     * Active tasks (PENDING, RUNNING, PAUSED) are not affected.
     */
    fun clearHistory() {
        scope.launch {
            repository.getAllTasks().first()
                .filter { task ->
                    task.status in listOf(
                        TaskStatus.COMPLETED,
                        TaskStatus.FAILED,
                        TaskStatus.CANCELLED
                    )
                }
                .forEach { task ->
                    repository.deleteTask(task)
                }
        }
    }

    /**
     * Retry a failed or cancelled task.
     *
     * Creates a new task with the same description and parameters.
     *
     * @param taskId The UUID of the task to retry
     * @return The UUID of the new task, or null if the original task was not found
     */
    fun retryTask(taskId: UUID): UUID? {
        var newTaskId: UUID? = null
        scope.launch {
            val originalTask = repository.getTaskById(taskId)
            if (originalTask != null && originalTask.status in listOf(TaskStatus.FAILED, TaskStatus.CANCELLED)) {
                newTaskId = submitTask(originalTask.description, originalTask.maxSteps)
            }
        }
        return newTaskId
    }

    /**
     * Stop all active tasks and shutdown the agent.
     *
     * This will cancel all pending and running tasks and stop the agent service.
     */
    fun shutdown() {
        scope.launch {
            _agentState.value = AgentState.IDLE

            // Cancel all active tasks
            repository.getActiveTasks().first().forEach { task ->
                repository.updateTask(
                    task.copy(
                        status = TaskStatus.CANCELLED,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = "Agent shutdown"
                    )
                )
            }

            // Unbind and stop the service
            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Service not bound, ignore
            }
            AgentService.stop(context)
            agentService = null
            _isServiceConnected.value = false
        }
    }

    /**
     * Ensure the agent service is started and bound.
     */
    private fun ensureServiceStarted() {
        if (!_isServiceConnected.value) {
            // Start the service
            AgentService.start(context)

            // Bind to it for status updates
            val intent = Intent(context, AgentService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Unbind from the agent service.
     *
     * Call this when the controller is no longer needed (e.g., in onDestroy).
     */
    fun unbind() {
        try {
            context.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            // Service not bound, ignore
        }
        agentService = null
        _isServiceConnected.value = false
    }

    companion object {
        @Volatile
        private var INSTANCE: AgentController? = null

        /**
         * Get the singleton AgentController instance.
         *
         * @param context Application or Activity context
         * @return The AgentController instance
         */
        fun getInstance(context: Context): AgentController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private fun createInstance(context: Context): AgentController {
            val database = AppDatabase.getInstance(context)
            val repository = AgentRepository.getInstance(database)
            return AgentController(context, repository)
        }

        /**
         * Clear the singleton instance.
         *
         * This should only be used in tests.
         */
        internal fun clearInstance() {
            synchronized(this) {
                INSTANCE?.shutdown()
                INSTANCE = null
            }
        }
    }
}

/**
 * Represents the current state of the agent.
 */
enum class AgentState {
    /** Agent is not running any tasks */
    IDLE,

    /** Agent is actively executing tasks */
    RUNNING,

    /** Agent execution is paused */
    PAUSED,

    /** Agent encountered an error */
    ERROR
}
