package com.openautoglm.agent.agent

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.openautoglm.agent.accessibility.ScreenCaptureManager
import com.openautoglm.agent.core.ActionExecutor
import com.openautoglm.agent.core.ResponseParser
import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionType
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.TaskStatus
import com.openautoglm.agent.knowledge.PromptTemplates
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ContentPart
import com.openautoglm.agent.model.ModelClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * AI-powered agent for automating Android phone interactions.
 *
 * The PhoneAgent uses a vision-language model to understand screen content
 * and decide on actions to complete user tasks. It follows a step-by-step
 * approach:
 * 1. Capture current screen state
 * 2. Send to VLM with task context
 * 3. Parse VLM response into action
 * 4. Execute action
 * 5. Repeat until task complete
 *
 * Ported from Python implementation in phone_agent/agent.py
 *
 * @param modelClient The VLM client for inference
 * @param screenCapture Manager for capturing screenshots
 * @param repository Data repository for storing task history
 * @param config Agent configuration
 * @param context Application context
 */
class PhoneAgent(
    private val modelClient: ModelClient,
    private val screenCapture: ScreenCaptureManager,
    private val repository: AgentRepository,
    private val config: AgentConfig = AgentConfig.DEFAULT,
    private val context: Context
) {
    // ActionExecutor for actually performing UI actions
    private val actionExecutor = ActionExecutor(context)

    companion object {
        private const val TAG = "PhoneAgent"

        /**
         * Converts a Bitmap to base64-encoded string.
         */
        private fun Bitmap.toBase64(quality: Int = 80): String {
            val outputStream = ByteArrayOutputStream()
            compress(Bitmap.CompressFormat.PNG, quality, outputStream)
            val byteArray = outputStream.toByteArray()
            return Base64.encodeToString(byteArray, Base64.NO_WRAP)
        }
    }

    // Internal state
    private val conversationContext = mutableListOf<ChatMessage>()
    private var stepCount = 0
    private var currentTaskId: UUID? = null

    // State flow for UI observation
    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    /**
     * Runs the agent to complete a task.
     *
     * This is the main entry point for task execution. The agent will
     * continue stepping until the task is complete or max steps reached.
     *
     * @param taskDescription Natural language description of the task
     * @return Final message from the agent
     */
    suspend fun run(taskDescription: String): String {
        Log.i(TAG, "Starting task: $taskDescription")

        // Create and save task
        val task = Task(
            description = taskDescription,
            status = TaskStatus.RUNNING,
            maxSteps = config.maxStepsPerTask
        )
        repository.insertTask(task)
        currentTaskId = task.id

        // Reset state
        conversationContext.clear()
        stepCount = 0
        _agentState.value = AgentState.Running(task.id, stepCount, taskDescription)

        // Execute first step with user prompt
        var result = executeStep(taskDescription, isFirst = true)

        if (result.finished) {
            finishTask(task.id, TaskStatus.COMPLETED, result.message)
            return result.message ?: "Task completed"
        }

        // Continue until finished or max steps reached
        while (stepCount < config.maxStepsPerTask) {
            result = executeStep(isFirst = false)

            if (result.finished) {
                finishTask(task.id, TaskStatus.COMPLETED, result.message)
                return result.message ?: "Task completed"
            }
        }

        // Max steps reached
        val message = "Maximum steps (${config.maxStepsPerTask}) reached"
        finishTask(task.id, TaskStatus.FAILED, message)
        return message
    }

    /**
     * Executes a single step of the agent.
     *
     * Useful for manual control, debugging, or UI that wants to show
     * step-by-step progress.
     *
     * @param taskDescription Task description (only needed for first step)
     * @return StepResult with details about the step execution
     */
    suspend fun step(taskDescription: String? = null): StepResult {
        val isFirst = conversationContext.isEmpty()

        if (isFirst) {
            require(!taskDescription.isNullOrBlank()) {
                "Task description is required for the first step"
            }

            // Create task if not exists
            if (currentTaskId == null) {
                val task = Task(
                    description = taskDescription,
                    status = TaskStatus.RUNNING,
                    maxSteps = config.maxStepsPerTask
                )
                repository.insertTask(task)
                currentTaskId = task.id
            }
        }

        return executeStep(taskDescription, isFirst)
    }

    /**
     * Resets the agent state for a new task.
     */
    fun reset() {
        conversationContext.clear()
        stepCount = 0
        currentTaskId = null
        _agentState.value = AgentState.Idle
    }

    /**
     * Pauses the current task execution.
     */
    suspend fun pause() {
        currentTaskId?.let { taskId ->
            repository.getTaskById(taskId)?.let { task ->
                repository.updateTask(task.copy(status = TaskStatus.PAUSED))
                _agentState.value = AgentState.Paused(taskId)
            }
        }
    }

    /**
     * Cancels the current task execution.
     */
    suspend fun cancel() {
        currentTaskId?.let { taskId ->
            finishTask(taskId, TaskStatus.CANCELLED, "Task cancelled by user")
        }
    }

    /**
     * Gets the current conversation context.
     *
     * @return Copy of the conversation messages
     */
    fun getContext(): List<ChatMessage> = conversationContext.toList()

    /**
     * Gets the current step count.
     */
    fun getStepCount(): Int = stepCount

    /**
     * Internal method to execute a single step of the agent loop.
     */
    private suspend fun executeStep(
        userPrompt: String? = null,
        isFirst: Boolean = false
    ): StepResult {
        stepCount++
        Log.d(TAG, "Executing step $stepCount")

        try {
            // Update state
            _agentState.value = AgentState.Running(
                currentTaskId ?: UUID.randomUUID(),
                stepCount,
                userPrompt ?: "Continuing task..."
            )

            // On first step, go to home screen first so the model doesn't see the app's own UI
            if (isFirst) {
                Log.i(TAG, "First step: Going to home screen before capturing screenshot")
                actionExecutor.executeHome()
                kotlinx.coroutines.delay(500) // Wait for home screen to appear
            }

            // Capture current screen state
            val screenshot = screenCapture.captureScreen()
                ?: return StepResult(
                    success = false,
                    finished = true,
                    action = null,
                    thinking = "",
                    message = "Failed to capture screenshot"
                )

            // Convert screenshot to base64
            val screenshotBase64 = screenshot.toBase64()

            // Build conversation messages
            if (isFirst) {
                // Add system prompt
                val systemPrompt = PromptTemplates.getSystemPrompt(
                    language = config.language,
                    appContext = null  // TODO: Add current app context
                )
                conversationContext.add(ChatMessage.system(systemPrompt))

                // Add user message with task and screenshot
                // Note: Image comes BEFORE text for BigModel API compatibility
                val userMessage = ChatMessage.user(
                    listOf(
                        ContentPart.imageBase64(screenshotBase64, "image/png"),
                        ContentPart.text("Task: $userPrompt")
                    )
                )
                conversationContext.add(userMessage)
            } else {
                // Add screenshot with screen info
                // Note: Image comes BEFORE text for BigModel API compatibility
                val userMessage = ChatMessage.user(
                    listOf(
                        ContentPart.imageBase64(screenshotBase64, "image/png"),
                        ContentPart.text("** Screen Info **")
                    )
                )
                conversationContext.add(userMessage)
            }

            // Get model response
            val modelResponse = try {
                modelClient.request(conversationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Model inference error", e)
                return StepResult(
                    success = false,
                    finished = true,
                    action = null,
                    thinking = "",
                    message = "Model error: ${e.message}"
                )
            }

            // Parse action from response
            val action = try {
                currentTaskId?.let { taskId ->
                    ResponseParser.parseAction(modelResponse, taskId)
                } ?: throw IllegalStateException("No current task ID")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse action, treating as finish", e)
                Action(
                    taskId = currentTaskId ?: UUID.randomUUID(),
                    type = ActionType.FINISH,
                    parameters = mapOf("message" to (modelResponse.action)),
                    thinking = modelResponse.thinking
                )
            }

            // Log thinking and action if verbose
            if (config.logVerbosity >= 1) {
                Log.i(TAG, "💭 Thinking: ${modelResponse.thinking}")
                Log.i(TAG, "🎯 Action: ${action.type} ${action.parameters}")
            }

            // Remove image from context to save memory
            conversationContext[conversationContext.lastIndex] = ChatMessage.user(
                "** Screen Info ** (screenshot removed)"
            )

            // Save action to database
            repository.insertAction(action)

            // Execute action (will be implemented by ActionExecutor)
            val actionResult = executeAction(action)

            // Add assistant response to context
            val assistantMessage = ChatMessage.assistant(
                "<think>${modelResponse.thinking}</think><answer>${modelResponse.action}</answer>"
            )
            conversationContext.add(assistantMessage)

            // Check if finished
            val finished = action.type == ActionType.FINISH || actionResult.shouldFinish

            if (finished && config.logVerbosity >= 1) {
                Log.i(TAG, "🎉 Task completed: ${actionResult.message ?: action.parameters["message"]}")
            }

            return StepResult(
                success = actionResult.success,
                finished = finished,
                action = action,
                thinking = modelResponse.thinking,
                message = actionResult.message ?: action.parameters["message"] as? String
            )

        } catch (e: Exception) {
            Log.e(TAG, "Step execution error", e)
            return StepResult(
                success = false,
                finished = true,
                action = null,
                thinking = "",
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Executes an action using the ActionExecutor.
     */
    private suspend fun executeAction(action: Action): ActionExecutionResult {
        val result = actionExecutor.execute(action)

        return ActionExecutionResult(
            success = result.success,
            shouldFinish = action.type == ActionType.FINISH || result.shouldFinish,
            message = result.message
        )
    }

    /**
     * Marks a task as finished and updates its status.
     */
    private suspend fun finishTask(taskId: UUID, status: TaskStatus, message: String?) {
        repository.getTaskById(taskId)?.let { task ->
            repository.updateTask(
                task.copy(
                    status = status,
                    result = message,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
        _agentState.value = AgentState.Finished(taskId, message)
    }
}

/**
 * Result of a single agent step.
 */
data class StepResult(
    val success: Boolean,
    val finished: Boolean,
    val action: Action?,
    val thinking: String,
    val message: String? = null
)

/**
 * Result of executing an action.
 */
data class ActionExecutionResult(
    val success: Boolean,
    val shouldFinish: Boolean,
    val message: String? = null
)

/**
 * Agent state for UI observation.
 */
sealed class AgentState {
    object Idle : AgentState()
    data class Running(val taskId: UUID, val step: Int, val description: String) : AgentState()
    data class Paused(val taskId: UUID) : AgentState()
    data class Finished(val taskId: UUID, val message: String?) : AgentState()
    data class Error(val message: String) : AgentState()
}
