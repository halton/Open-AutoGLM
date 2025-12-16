package com.openautoglm.agent.actions.handlers

import com.openautoglm.agent.accessibility.AutoGLMAccessibilityService
import com.openautoglm.agent.actions.ActionHandler
import com.openautoglm.agent.actions.BaseActionHandler
import com.openautoglm.agent.actions.ExecutionResult
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionType
import kotlinx.coroutines.delay

/**
 * Handler for tap actions (single tap, double tap, long press).
 *
 * Executes touch gestures at specific screen coordinates using the
 * AccessibilityService. Coordinates are normalized (0-999) and converted
 * to actual screen pixels during execution.
 *
 * Supported action types:
 * - TAP: Single tap at coordinates
 * - DOUBLE_TAP: Two quick taps at coordinates
 * - LONG_PRESS: Hold at coordinates for extended duration
 */
class TapActionHandler : BaseActionHandler() {

    companion object {
        private const val POST_TAP_DELAY_MS = 300L
        private const val DOUBLE_TAP_INTERVAL_MS = 100L
        private const val LONG_PRESS_DURATION_MS = 1000L
    }

    override fun canHandle(action: Action): Boolean {
        return action.type in setOf(
            ActionType.TAP,
            ActionType.DOUBLE_TAP,
            ActionType.LONG_PRESS
        )
    }

    override fun getDescription(): String {
        return "Executes tap gestures (single, double, long press) at screen coordinates"
    }

    override suspend fun executeInternal(action: Action): ExecutionResult {
        // Get AccessibilityService instance
        val service = AutoGLMAccessibilityService.instance
            ?: return ExecutionResult.Failure(
                error = "AccessibilityService not available",
                errorCode = "SERVICE_UNAVAILABLE",
                isRetryable = false
            )

        // Extract coordinates from parameters
        val (x, y) = extractCoordinates(action)
            ?: return ExecutionResult.Failure(
                error = "Invalid coordinates in action parameters",
                errorCode = "INVALID_PARAMETERS",
                isRetryable = false
            )

        // Validate coordinates are within normalized range (0-999)
        if (!isValidCoordinate(x) || !isValidCoordinate(y)) {
            return ExecutionResult.Failure(
                error = "Coordinates must be between 0 and 999, got: ($x, $y)",
                errorCode = "INVALID_COORDINATES",
                isRetryable = false
            )
        }

        // Execute the appropriate tap type
        val success = when (action.type) {
            ActionType.TAP -> {
                service.performTap(x, y)
            }
            ActionType.DOUBLE_TAP -> {
                service.performTap(x, y)
                delay(DOUBLE_TAP_INTERVAL_MS)
                service.performTap(x, y)
                true
            }
            ActionType.LONG_PRESS -> {
                service.performLongPress(x, y, LONG_PRESS_DURATION_MS)
            }
            else -> false
        }

        if (!success) {
            return ExecutionResult.Failure(
                error = "Failed to execute ${action.type} at ($x, $y)",
                errorCode = "EXECUTION_FAILED",
                isRetryable = true
            )
        }

        // Wait for UI to respond
        delay(POST_TAP_DELAY_MS)

        val actionName = when (action.type) {
            ActionType.TAP -> "Tap"
            ActionType.DOUBLE_TAP -> "Double tap"
            ActionType.LONG_PRESS -> "Long press"
            else -> "Tap"
        }

        return ExecutionResult.Success(
            message = "$actionName at ($x, $y)",
            data = mapOf(
                "x" to x.toString(),
                "y" to y.toString(),
                "type" to action.type.name
            )
        )
    }

    /**
     * Extracts x,y coordinates from action parameters.
     *
     * Supports multiple parameter formats:
     * - element: [x, y]
     * - x: int, y: int
     * - coordinates: [x, y]
     */
    private fun extractCoordinates(action: Action): Pair<Int, Int>? {
        val params = action.parameters

        // Try "element" parameter (most common format)
        params["element"]?.let { element ->
            when (element) {
                is List<*> -> {
                    if (element.size >= 2) {
                        val x = (element[0] as? Number)?.toInt()
                        val y = (element[1] as? Number)?.toInt()
                        if (x != null && y != null) {
                            return Pair(x, y)
                        }
                    }
                }
                is String -> {
                    // Try parsing "[x, y]" format
                    val coords = element.trim('[', ']').split(',')
                    if (coords.size == 2) {
                        val x = coords[0].trim().toIntOrNull()
                        val y = coords[1].trim().toIntOrNull()
                        if (x != null && y != null) {
                            return Pair(x, y)
                        }
                    }
                }
            }
        }

        // Try separate "x" and "y" parameters
        val x = (params["x"] as? Number)?.toInt()
        val y = (params["y"] as? Number)?.toInt()
        if (x != null && y != null) {
            return Pair(x, y)
        }

        // Try "coordinates" parameter
        (params["coordinates"] as? List<*>)?.let { coords ->
            if (coords.size >= 2) {
                val coordX = (coords[0] as? Number)?.toInt()
                val coordY = (coords[1] as? Number)?.toInt()
                if (coordX != null && coordY != null) {
                    return Pair(coordX, coordY)
                }
            }
        }

        return null
    }

    /**
     * Validates a normalized coordinate (0-999 range).
     */
    private fun isValidCoordinate(value: Int): Boolean {
        return value in 0..999
    }
}
