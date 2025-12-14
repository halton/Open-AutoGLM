package com.openautoglm.agent.actions.handlers

import com.openautoglm.agent.accessibility.AutoGLMAccessibilityService
import com.openautoglm.agent.actions.BaseActionHandler
import com.openautoglm.agent.actions.ExecutionResult
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionType
import kotlinx.coroutines.delay

/**
 * Handler for system navigation actions.
 *
 * Executes Android system navigation:
 * - BACK: Navigate to previous screen or close dialog
 * - HOME: Return to launcher/home screen
 *
 * These actions are useful for:
 * - Backing out of wrong screens
 * - Closing dialogs or popups
 * - Starting tasks from a known state (home screen)
 */
class NavigationActionHandler : BaseActionHandler() {

    companion object {
        private const val POST_NAVIGATION_DELAY_MS = 500L
    }

    override fun canHandle(action: Action): Boolean {
        return action.type in setOf(ActionType.BACK, ActionType.HOME)
    }

    override fun getDescription(): String {
        return "Executes system navigation actions (Back, Home)"
    }

    override suspend fun executeInternal(action: Action): ExecutionResult {
        // Get AccessibilityService instance
        val service = AutoGLMAccessibilityService.getInstance()
            ?: return ExecutionResult.Failure(
                error = "AccessibilityService not available",
                errorCode = "SERVICE_UNAVAILABLE",
                isRetryable = false
            )

        // Execute the appropriate navigation action
        val (success, actionName) = when (action.type) {
            ActionType.BACK -> {
                service.performBack() to "Back"
            }
            ActionType.HOME -> {
                service.performHome() to "Home"
            }
            else -> {
                return ExecutionResult.Failure(
                    error = "Unsupported navigation action: ${action.type}",
                    errorCode = "UNSUPPORTED_ACTION",
                    isRetryable = false
                )
            }
        }

        if (!success) {
            return ExecutionResult.Failure(
                error = "Failed to execute $actionName action",
                errorCode = "EXECUTION_FAILED",
                isRetryable = true
            )
        }

        // Wait for navigation to complete
        delay(POST_NAVIGATION_DELAY_MS)

        return ExecutionResult.Success(
            message = "$actionName action executed",
            data = mapOf("action" to actionName)
        )
    }
}
