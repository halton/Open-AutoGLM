package com.openautoglm.agent.actions

import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionResult

/**
 * Interface for handling specific action types in the VLM Android Agent.
 *
 * Each action type (Tap, Swipe, Type, etc.) should have a corresponding
 * implementation of this interface that knows how to execute that action
 * using Android's AccessibilityService or other system APIs.
 *
 * Implementations should be stateless and thread-safe where possible.
 */
interface ActionHandler {
    /**
     * Executes the given action and returns the result.
     *
     * This method is responsible for:
     * 1. Validating the action parameters
     * 2. Executing the action using appropriate Android APIs
     * 3. Waiting for the action to complete
     * 4. Capturing any errors or failures
     * 5. Returning a structured result
     *
     * @param action The action to execute
     * @return ActionResult indicating success/failure and any relevant data
     */
    suspend fun execute(action: Action): ActionResult

    /**
     * Validates whether this handler can execute the given action.
     *
     * @param action The action to validate
     * @return true if the action is valid and can be executed, false otherwise
     */
    fun canHandle(action: Action): Boolean

    /**
     * Returns a human-readable description of what this handler does.
     *
     * @return Description string
     */
    fun getDescription(): String
}

/**
 * Result of an action execution attempt.
 *
 * This sealed class represents the possible outcomes of executing an action,
 * providing detailed information about success, failure, or partial completion.
 */
sealed class ExecutionResult {
    /**
     * Action executed successfully.
     *
     * @property message Optional success message
     * @property data Optional result data (e.g., extracted text, screenshot path)
     */
    data class Success(
        val message: String? = null,
        val data: Map<String, String>? = null
    ) : ExecutionResult()

    /**
     * Action failed to execute.
     *
     * @property error Error message describing what went wrong
     * @property errorCode Optional error code for categorization
     * @property isRetryable Whether this action can be retried
     */
    data class Failure(
        val error: String,
        val errorCode: String? = null,
        val isRetryable: Boolean = true
    ) : ExecutionResult()

    /**
     * Action partially completed but requires user intervention.
     *
     * @property message Message explaining what's needed from the user
     * @property reason Reason for requiring intervention (e.g., CAPTCHA, 2FA)
     */
    data class RequiresIntervention(
        val message: String,
        val reason: InterventionReason
    ) : ExecutionResult()
}

/**
 * Reasons why an action might require user intervention.
 */
enum class InterventionReason {
    /** CAPTCHA or verification challenge detected */
    CAPTCHA,

    /** Two-factor authentication required */
    TWO_FACTOR_AUTH,

    /** Login credentials needed */
    LOGIN_REQUIRED,

    /** Permission grant required */
    PERMISSION_REQUIRED,

    /** App not installed */
    APP_NOT_INSTALLED,

    /** Network connectivity issue */
    NETWORK_ERROR,

    /** Unexpected UI state */
    UNEXPECTED_UI,

    /** Other reason */
    OTHER
}

/**
 * Extension function to convert ExecutionResult to ActionResult.
 */
fun ExecutionResult.toActionResult(actionId: String): ActionResult {
    return when (this) {
        is ExecutionResult.Success -> ActionResult(
            success = true,
            message = message
        )
        is ExecutionResult.Failure -> ActionResult(
            success = false,
            message = error
        )
        is ExecutionResult.RequiresIntervention -> ActionResult(
            success = false,
            message = "User intervention required: ${reason.name} - $message",
            requiresConfirmation = true
        )
    }
}

/**
 * Base abstract class providing common functionality for action handlers.
 *
 * Implementations can extend this to inherit common validation and error handling logic.
 */
abstract class BaseActionHandler : ActionHandler {
    /**
     * Validates action parameters before execution.
     *
     * @param action The action to validate
     * @throws IllegalArgumentException if validation fails
     */
    protected open fun validateAction(action: Action) {
        // Base validation - can be overridden by subclasses
    }

    /**
     * Handles errors that occur during action execution.
     *
     * @param error The exception that occurred
     * @return ExecutionResult.Failure with appropriate error information
     */
    protected open fun handleError(error: Exception): ExecutionResult {
        return ExecutionResult.Failure(
            error = error.message ?: "Unknown error occurred",
            errorCode = error::class.simpleName,
            isRetryable = error !is IllegalArgumentException
        )
    }

    /**
     * Template method for executing actions with error handling.
     */
    override suspend fun execute(action: Action): ActionResult {
        return try {
            validateAction(action)
            val result = executeInternal(action)
            result.toActionResult(action.id.toString())
        } catch (e: Exception) {
            handleError(e).toActionResult(action.id.toString())
        }
    }

    /**
     * Internal execution method to be implemented by subclasses.
     *
     * @param action The validated action to execute
     * @return ExecutionResult indicating the outcome
     */
    protected abstract suspend fun executeInternal(action: Action): ExecutionResult
}
