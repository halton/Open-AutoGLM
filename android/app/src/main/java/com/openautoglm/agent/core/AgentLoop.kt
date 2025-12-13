package com.openautoglm.agent.core

import android.util.Log
import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionResult
import com.openautoglm.agent.data.entities.ActionType
import com.openautoglm.agent.data.entities.ScreenState
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.TaskStatus
import com.openautoglm.agent.knowledge.PromptTemplates
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ContentPart
import com.openautoglm.agent.model.InferenceRouter
import com.openautoglm.agent.model.ModelResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Main agent execution loop that orchestrates the VLM-powered Android automation.
 *
 * The loop follows this pattern:
 * 1. Capture current screen state
 * 2. Build prompt with system instructions and screenshot
 * 3. Call VLM for action inference
 * 4. Parse the response to extract action
 * 5. Execute the action
 * 6. Repeat until task completion, failure, or cancellation
 */
class AgentLoop(
    private val repository: AgentRepository,
    private val inferenceRouter: InferenceRouter,
    private val actionExecutor: ActionExecutor,
    private val screenStateManager: ScreenStateManager
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val DEFAULT_STEP_DELAY_MS = 500L
    }

    /**
     * Represents the current state of the agent loop execution.
     */
    sealed class AgentLoopState {
        /** Agent is idle, no task running */
        data object Idle : AgentLoopState()

        /** Capturing screen state */
        data class CapturingScreen(val step: Int, val taskId: UUID) : AgentLoopState()

        /** Waiting for VLM inference */
        data class Inferring(val step: Int, val taskId: UUID) : AgentLoopState()

        /** Executing an action */
        data class ExecutingAction(val step: Int, val taskId: UUID, val action: Action) : AgentLoopState()

        /** Task completed successfully */
        data class Completed(val taskId: UUID, val result: String?) : AgentLoopState()

        /** Task failed with error */
        data class Failed(val taskId: UUID, val error: String) : AgentLoopState()

        /** Task was cancelled */
        data class Cancelled(val taskId: UUID) : AgentLoopState()
    }

    private val _state = MutableStateFlow<AgentLoopState>(AgentLoopState.Idle)
    /** Observable state of the agent loop */
    val state: StateFlow<AgentLoopState> = _state.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    /** Current step number in the execution */
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private var currentJob: Job? = null
    private var isCancelled = false

    /**
     * Callback interface for agent loop progress updates.
     */
    interface ProgressCallback {
        /** Called when a new step begins */
        fun onStepStarted(step: Int, task: Task)

        /** Called when screen capture is completed */
        fun onScreenCaptured(step: Int, screenStateId: String)

        /** Called when VLM inference is completed */
        fun onInferenceCompleted(step: Int, response: String)

        /** Called when an action is about to be executed */
        fun onActionExecuting(step: Int, action: Action)

        /** Called when an action execution is completed */
        fun onActionCompleted(step: Int, action: Action, success: Boolean)

        /** Called when the task is completed */
        fun onTaskCompleted(task: Task)

        /** Called when the task fails */
        fun onTaskFailed(task: Task, error: String)

        /** Called when the task is cancelled */
        fun onTaskCancelled(task: Task)
    }

    private var progressCallback: ProgressCallback? = null

    /**
     * Sets the progress callback for receiving execution updates.
     */
    fun setProgressCallback(callback: ProgressCallback?) {
        progressCallback = callback
    }

    /**
     * Main entry point for executing a task.
     *
     * @param task The task to execute
     * @return The updated task with final status
     */
    suspend fun executeTask(task: Task): Task = coroutineScope {
        Log.i(TAG, "Starting task execution: ${task.id}, description: ${task.description}")

        isCancelled = false
        _currentStep.value = 0

        // Update task to RUNNING status
        var currentTask = task.copy(
            status = TaskStatus.RUNNING,
            startedAt = System.currentTimeMillis()
        )
        repository.updateTask(currentTask)

        try {
            currentTask = runAgentLoop(currentTask)
        } catch (e: CancellationException) {
            Log.i(TAG, "Task cancelled: ${task.id}")
            currentTask = currentTask.copy(
                status = TaskStatus.CANCELLED,
                completedAt = System.currentTimeMillis()
            )
            repository.updateTask(currentTask)
            _state.value = AgentLoopState.Cancelled(currentTask.id)
            progressCallback?.onTaskCancelled(currentTask)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Task failed with exception: ${task.id}", e)
            val errorMessage = e.message ?: "Unknown error occurred"
            currentTask = currentTask.copy(
                status = TaskStatus.FAILED,
                completedAt = System.currentTimeMillis(),
                errorMessage = errorMessage
            )
            repository.updateTask(currentTask)
            _state.value = AgentLoopState.Failed(currentTask.id, errorMessage)
            progressCallback?.onTaskFailed(currentTask, errorMessage)
        }

        currentTask
    }

    /**
     * Runs the main agent loop until completion, failure, or cancellation.
     */
    private suspend fun runAgentLoop(initialTask: Task): Task = coroutineScope {
        var currentTask = initialTask

        while (currentTask.canContinue() && !isCancelled) {
            ensureActive() // Check for coroutine cancellation

            val stepNumber = currentTask.stepCount + 1
            _currentStep.value = stepNumber
            progressCallback?.onStepStarted(stepNumber, currentTask)

            Log.d(TAG, "Executing step $stepNumber for task ${currentTask.id}")

            // Step 1: Capture screen state
            _state.value = AgentLoopState.CapturingScreen(stepNumber, currentTask.id)
            val screenState = try {
                screenStateManager.captureCurrentState()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture screen state at step $stepNumber", e)
                throw AgentLoopException("Screen capture failed: ${e.message}", e)
            }
            progressCallback?.onScreenCaptured(stepNumber, screenState.id.toString())

            // Step 2: Build prompt with system instructions and screenshot
            val messages = buildPromptMessages(currentTask, screenState)

            // Step 3: Call VLM for inference
            _state.value = AgentLoopState.Inferring(stepNumber, currentTask.id)
            val vlmResponse = try {
                inferenceRouter.route(messages, currentTask.inferenceMode)
            } catch (e: Exception) {
                Log.e(TAG, "VLM inference failed at step $stepNumber", e)
                throw AgentLoopException("VLM inference failed: ${e.message}", e)
            }
            progressCallback?.onInferenceCompleted(stepNumber, vlmResponse.action)

            // Step 4: Parse VLM response to extract action
            val parsedAction = try {
                ResponseParser.parseAction(vlmResponse, currentTask.id)
                    ?: throw AgentLoopException("Failed to parse action from VLM response")
            } catch (e: AgentLoopException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Response parsing failed at step $stepNumber", e)
                throw AgentLoopException("Response parsing failed: ${e.message}", e)
            }

            // Step 5: Save the action to repository
            repository.insertAction(parsedAction)
            progressCallback?.onActionExecuting(stepNumber, parsedAction)

            // Step 6: Check for finish action
            if (parsedAction.type == ActionType.FINISH) {
                Log.i(TAG, "Task completed with FINISH action at step $stepNumber")
                val result = parsedAction.parameters["message"] as? String
                currentTask = currentTask.copy(
                    status = TaskStatus.COMPLETED,
                    completedAt = System.currentTimeMillis(),
                    stepCount = stepNumber,
                    result = result
                )
                repository.updateTask(currentTask)

                val updatedAction = parsedAction.copy(success = true)
                repository.updateAction(updatedAction)
                progressCallback?.onActionCompleted(stepNumber, updatedAction, true)

                _state.value = AgentLoopState.Completed(currentTask.id, result)
                progressCallback?.onTaskCompleted(currentTask)
                return@coroutineScope currentTask
            }

            // Step 7: Check for TAKE_OVER action (user intervention required)
            if (parsedAction.type == ActionType.TAKE_OVER) {
                Log.i(TAG, "Task requires user intervention at step $stepNumber")
                val reason = parsedAction.parameters["reason"] as? String ?: "User intervention required"
                currentTask = currentTask.copy(
                    status = TaskStatus.PAUSED,
                    stepCount = stepNumber,
                    result = reason
                )
                repository.updateTask(currentTask)

                val updatedAction = parsedAction.copy(success = true)
                repository.updateAction(updatedAction)
                progressCallback?.onActionCompleted(stepNumber, updatedAction, true)

                return@coroutineScope currentTask
            }

            // Step 8: Execute the action
            _state.value = AgentLoopState.ExecutingAction(stepNumber, currentTask.id, parsedAction)
            val actionResult = try {
                actionExecutor.execute(parsedAction)
            } catch (e: Exception) {
                Log.e(TAG, "Action execution failed at step $stepNumber", e)
                val failedAction = parsedAction.copy(success = false)
                repository.updateAction(failedAction)
                progressCallback?.onActionCompleted(stepNumber, failedAction, false)
                throw AgentLoopException("Action execution failed: ${e.message}", e)
            }

            // Step 9: Update action with result
            val updatedAction = parsedAction.copy(success = actionResult.success)
            repository.updateAction(updatedAction)
            progressCallback?.onActionCompleted(stepNumber, updatedAction, actionResult.success)

            if (!actionResult.success) {
                Log.w(TAG, "Action failed at step $stepNumber: ${actionResult.errorMessage}")
                // Continue the loop - VLM will see the current state and decide next action
            }

            // Step 10: Update task step count
            currentTask = currentTask.copy(stepCount = stepNumber)
            repository.updateTask(currentTask)

            // Brief delay between steps to allow UI to update
            kotlinx.coroutines.delay(DEFAULT_STEP_DELAY_MS)
        }

        // Check why we exited the loop
        if (isCancelled) {
            Log.i(TAG, "Task was cancelled: ${currentTask.id}")
            currentTask = currentTask.copy(
                status = TaskStatus.CANCELLED,
                completedAt = System.currentTimeMillis()
            )
            repository.updateTask(currentTask)
            _state.value = AgentLoopState.Cancelled(currentTask.id)
            progressCallback?.onTaskCancelled(currentTask)
        } else if (currentTask.hasReachedMaxSteps()) {
            Log.w(TAG, "Task reached max steps limit: ${currentTask.id}")
            val errorMessage = "Maximum steps (${currentTask.maxSteps}) reached without completion"
            currentTask = currentTask.copy(
                status = TaskStatus.FAILED,
                completedAt = System.currentTimeMillis(),
                errorMessage = errorMessage
            )
            repository.updateTask(currentTask)
            _state.value = AgentLoopState.Failed(currentTask.id, errorMessage)
            progressCallback?.onTaskFailed(currentTask, errorMessage)
        }

        currentTask
    }

    /**
     * Builds the list of chat messages for VLM inference.
     *
     * @param task The current task
     * @param screenState The current screen state
     * @return List of ChatMessage for the VLM
     */
    private fun buildPromptMessages(task: Task, screenState: ScreenState): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // System prompt with agent instructions
        val systemPrompt = PromptTemplates.getSystemPrompt(task.description)
        messages.add(ChatMessage.system(systemPrompt))

        // User message with task description and screenshot
        val userPromptText = PromptTemplates.getUserPrompt(
            taskDescription = task.description,
            currentStep = task.stepCount + 1,
            maxSteps = task.maxSteps
        )

        // Build multimodal content with text and image
        val contentParts = mutableListOf<ContentPart>()
        contentParts.add(ContentPart.text(userPromptText))

        // Add screenshot as base64 image
        screenState.screenshotBase64?.let { base64 ->
            contentParts.add(ContentPart.imageBase64(base64, "image/png"))
        }

        messages.add(ChatMessage.user(contentParts))

        return messages
    }

    /**
     * Cancels the currently running task.
     */
    fun cancelCurrentTask() {
        Log.i(TAG, "Cancel requested for current task")
        isCancelled = true
        currentJob?.cancel()
    }

    /**
     * Resets the agent loop state to idle.
     */
    fun reset() {
        isCancelled = false
        currentJob = null
        _currentStep.value = 0
        _state.value = AgentLoopState.Idle
    }
}

/**
 * Exception thrown by the agent loop for recoverable errors.
 */
class AgentLoopException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
