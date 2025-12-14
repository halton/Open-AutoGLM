package com.openautoglm.agent.agent

import android.content.Context
import android.util.Log
import com.openautoglm.agent.accessibility.ScreenCaptureManager
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

    companion object {
        private const val TAG = "PhoneAgent"
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
            language = config.language,
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
                    language = config.language,
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

            // Capture current screen state
            val screenshot = screenCapture.captureScreen(config.screenshotQuality)
                ?: return StepResult(
                    success = false,
                    finished = true,
                    action = null,
                    thinking = "",
                    message = "Failed to capture screenshot"
                )

            // Build conversation messages
            if (isFirst) {
                // Add system prompt
                val systemPrompt = PromptTemplates.getSystemPrompt(
                    language = config.language,
                    appContext = null  // TODO: Add current app context
                )
                conversationContext.add(ChatMessage.system(systemPrompt))

                // Add user message with task and screenshot
                val userMessage = ChatMessage.user(
                    listOf(
                        ContentPart.text("Task: $userPrompt\n\nCurrent screen:"),
                        ContentPart.imageBase64(screenshot.base64Data, "image/png")
                    )
                )
                conversationContext.add(userMessage)
            } else {
                // Add screenshot with screen info
                val userMessage = ChatMessage.user(
                    listOf(
                        ContentPart.text("** Screen Info **\n\nCurrent screen:"),
                        ContentPart.imageBase64(screenshot.base64Data, "image/png")
                    )
                )
                conversationContext.add(userMessage)
            }

            // Get model response
            val modelResponse = try {
                modelClient.generateChatCompletion(
                    messages = conversationContext,
                    temperature = 0.3f,
                    maxTokens = config.actionTimeout.toInt()
                )
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
     * Executes an action (placeholder - will be implemented by ActionExecutor).
     */
    private suspend fun executeAction(action: Action): ActionExecutionResult {
        // TODO: Implement via ActionExecutor in T049
        // For now, return a simple success result
        return ActionExecutionResult(
            success = true,
            shouldFinish = action.type == ActionType.FINISH,
            message = action.parameters["message"] as? String
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
