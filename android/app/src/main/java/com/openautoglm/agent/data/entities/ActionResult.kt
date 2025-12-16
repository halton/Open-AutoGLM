package com.openautoglm.agent.data.entities

import java.util.UUID

/**
 * Represents the result of an action execution.
 * This is a plain data class (not a Room entity) used to communicate
 * action execution outcomes throughout the application.
 *
 * @property actionId The unique identifier of the action that was executed, if applicable
 * @property success Whether the action executed successfully
 * @property shouldFinish Whether the agent should finish after this action
 * @property message Optional message describing the result or any errors
 * @property requiresConfirmation Whether the action requires user confirmation before proceeding
 */
data class ActionResult(
    val actionId: UUID? = null,
    val success: Boolean,
    val shouldFinish: Boolean = false,
    val message: String? = null,
    val requiresConfirmation: Boolean = false
) {
    companion object {
        /**
         * Creates a successful action result.
         */
        fun success(
            actionId: UUID? = null,
            message: String? = null,
            shouldFinish: Boolean = false
        ): ActionResult = ActionResult(
            actionId = actionId,
            success = true,
            shouldFinish = shouldFinish,
            message = message,
            requiresConfirmation = false
        )

        /**
         * Creates a failed action result.
         */
        fun failure(
            actionId: UUID? = null,
            message: String? = null
        ): ActionResult = ActionResult(
            actionId = actionId,
            success = false,
            shouldFinish = false,
            message = message,
            requiresConfirmation = false
        )

        /**
         * Creates an action result that requires user confirmation.
         */
        fun requiresConfirmation(
            actionId: UUID? = null,
            message: String? = null
        ): ActionResult = ActionResult(
            actionId = actionId,
            success = true,
            shouldFinish = false,
            message = message,
            requiresConfirmation = true
        )

        /**
         * Creates a result indicating the agent should finish.
         */
        fun finish(
            actionId: UUID? = null,
            message: String? = null,
            success: Boolean = true
        ): ActionResult = ActionResult(
            actionId = actionId,
            success = success,
            shouldFinish = true,
            message = message,
            requiresConfirmation = false
        )
    }
}
