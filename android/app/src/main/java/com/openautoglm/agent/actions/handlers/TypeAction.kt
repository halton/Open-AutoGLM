package com.openautoglm.agent.actions.handlers

import com.openautoglm.agent.accessibility.AutoGLMAccessibilityService
import com.openautoglm.agent.actions.BaseActionHandler
import com.openautoglm.agent.actions.ExecutionResult
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionType
import kotlinx.coroutines.delay

/**
 * Handler for text input actions.
 *
 * Executes text input into the currently focused input field using the
 * AccessibilityService. Automatically clears existing text before typing
 * new content.
 *
 * Important notes:
 * - The input field must be focused first (usually via a TAP action)
 * - Text is entered character by character
 * - Existing text is cleared before new input
 * - Works with ADB keyboard or system keyboard
 */
class TypeActionHandler : BaseActionHandler() {

    companion object {
        private const val POST_TYPE_DELAY_MS = 500L
        private const val CHAR_DELAY_MS = 50L
    }

    override fun canHandle(action: Action): Boolean {
        return action.type == ActionType.TYPE
    }

    override fun getDescription(): String {
        return "Types text into the currently focused input field"
    }

    override suspend fun executeInternal(action: Action): ExecutionResult {
        // Get AccessibilityService instance
        val service = AutoGLMAccessibilityService.getInstance()
            ?: return ExecutionResult.Failure(
                error = "AccessibilityService not available",
                errorCode = "SERVICE_UNAVAILABLE",
                isRetryable = false
            )

        // Extract text to type
        val text = extractText(action)
            ?: return ExecutionResult.Failure(
                error = "No text specified in action parameters",
                errorCode = "INVALID_PARAMETERS",
                isRetryable = false
            )

        if (text.isBlank()) {
            return ExecutionResult.Failure(
                error = "Text cannot be empty or blank",
                errorCode = "INVALID_TEXT",
                isRetryable = false
            )
        }

        // Type the text
        val success = service.performTextInput(text)

        if (!success) {
            return ExecutionResult.Failure(
                error = "Failed to type text. Ensure an input field is focused.",
                errorCode = "EXECUTION_FAILED",
                isRetryable = true
            )
        }

        // Wait for text input to complete and UI to update
        delay(POST_TYPE_DELAY_MS)

        return ExecutionResult.Success(
            message = "Typed text: \"${truncateForDisplay(text)}\"",
            data = mapOf(
                "text" to text,
                "length" to text.length.toString()
            )
        )
    }

    /**
     * Extracts text from action parameters.
     *
     * Supports parameter names: "text", "content", "input"
     */
    private fun extractText(action: Action): String? {
        val params = action.parameters

        // Try "text" parameter (most common)
        (params["text"] as? String)?.let { return it }

        // Try "content" parameter
        (params["content"] as? String)?.let { return it }

        // Try "input" parameter
        (params["input"] as? String)?.let { return it }

        return null
    }

    /**
     * Truncates text for display in logs/messages.
     */
    private fun truncateForDisplay(text: String, maxLength: Int = 50): String {
        return if (text.length > maxLength) {
            "${text.take(maxLength)}..."
        } else {
            text
        }
    }
}
