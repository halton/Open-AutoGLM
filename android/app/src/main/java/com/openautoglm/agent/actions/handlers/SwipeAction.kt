package com.openautoglm.agent.actions.handlers

import com.openautoglm.agent.accessibility.AutoGLMAccessibilityService
import com.openautoglm.agent.actions.BaseActionHandler
import com.openautoglm.agent.actions.ExecutionResult
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionType
import kotlinx.coroutines.delay

/**
 * Handler for swipe actions.
 *
 * Executes swipe gestures from start coordinates to end coordinates.
 * Useful for scrolling, navigating between screens, or gesture-based navigation.
 *
 * Supports multiple parameter formats:
 * - start=[x1,y1], end=[x2,y2]
 * - element=[x1,y1,x2,y2]
 */
class SwipeActionHandler : BaseActionHandler() {

    companion object {
        private const val DEFAULT_SWIPE_DURATION_MS = 300L
        private const val POST_SWIPE_DELAY_MS = 400L
    }

    override fun canHandle(action: Action): Boolean {
        return action.type == ActionType.SWIPE
    }

    override fun getDescription(): String {
        return "Executes swipe gestures from start to end coordinates"
    }

    override suspend fun executeInternal(action: Action): ExecutionResult {
        // Get AccessibilityService instance
        val service = AutoGLMAccessibilityService.instance
            ?: return ExecutionResult.Failure(
                error = "AccessibilityService not available",
                errorCode = "SERVICE_UNAVAILABLE",
                isRetryable = false
            )

        // Extract swipe coordinates
        val swipeCoords = extractSwipeCoordinates(action)
            ?: return ExecutionResult.Failure(
                error = "Invalid swipe coordinates in action parameters",
                errorCode = "INVALID_PARAMETERS",
                isRetryable = false
            )

        val (startX, startY, endX, endY) = swipeCoords

        // Validate coordinates
        if (!isValidCoordinate(startX) || !isValidCoordinate(startY) ||
            !isValidCoordinate(endX) || !isValidCoordinate(endY)) {
            return ExecutionResult.Failure(
                error = "All coordinates must be between 0 and 999, got: ($startX, $startY) -> ($endX, $endY)",
                errorCode = "INVALID_COORDINATES",
                isRetryable = false
            )
        }

        // Get duration from parameters or use default
        val duration = (action.parameters["duration"] as? Number)?.toLong()
            ?: DEFAULT_SWIPE_DURATION_MS

        // Execute swipe
        val success = service.performSwipe(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            duration = duration
        )

        if (!success) {
            return ExecutionResult.Failure(
                error = "Failed to execute swipe from ($startX, $startY) to ($endX, $endY)",
                errorCode = "EXECUTION_FAILED",
                isRetryable = true
            )
        }

        // Wait for UI to respond to swipe
        delay(POST_SWIPE_DELAY_MS)

        return ExecutionResult.Success(
            message = "Swipe from ($startX, $startY) to ($endX, $endY)",
            data = mapOf(
                "startX" to startX.toString(),
                "startY" to startY.toString(),
                "endX" to endX.toString(),
                "endY" to endY.toString(),
                "duration" to duration.toString()
            )
        )
    }

    /**
     * Extracts swipe coordinates from action parameters.
     *
     * Supports formats:
     * - start=[x1,y1], end=[x2,y2]
     * - element=[x1,y1,x2,y2]
     * - startX=x1, startY=y1, endX=x2, endY=y2
     */
    private fun extractSwipeCoordinates(action: Action): SwipeCoordinates? {
        val params = action.parameters

        // Try "start" and "end" parameters
        val start = params["start"]
        val end = params["end"]
        if (start != null && end != null) {
            val startCoords = parseCoordinateList(start)
            val endCoords = parseCoordinateList(end)
            if (startCoords != null && endCoords != null) {
                return SwipeCoordinates(
                    startX = startCoords.first,
                    startY = startCoords.second,
                    endX = endCoords.first,
                    endY = endCoords.second
                )
            }
        }

        // Try "element" parameter with 4 values
        params["element"]?.let { element ->
            when (element) {
                is List<*> -> {
                    if (element.size >= 4) {
                        val x1 = (element[0] as? Number)?.toInt()
                        val y1 = (element[1] as? Number)?.toInt()
                        val x2 = (element[2] as? Number)?.toInt()
                        val y2 = (element[3] as? Number)?.toInt()
                        if (x1 != null && y1 != null && x2 != null && y2 != null) {
                            return SwipeCoordinates(x1, y1, x2, y2)
                        }
                    }
                }
            }
        }

        // Try separate parameters
        val startX = (params["startX"] as? Number)?.toInt()
        val startY = (params["startY"] as? Number)?.toInt()
        val endX = (params["endX"] as? Number)?.toInt()
        val endY = (params["endY"] as? Number)?.toInt()
        if (startX != null && startY != null && endX != null && endY != null) {
            return SwipeCoordinates(startX, startY, endX, endY)
        }

        return null
    }

    /**
     * Parses a coordinate list from various formats.
     */
    private fun parseCoordinateList(value: Any): Pair<Int, Int>? {
        return when (value) {
            is List<*> -> {
                if (value.size >= 2) {
                    val x = (value[0] as? Number)?.toInt()
                    val y = (value[1] as? Number)?.toInt()
                    if (x != null && y != null) Pair(x, y) else null
                } else null
            }
            is String -> {
                val coords = value.trim('[', ']').split(',')
                if (coords.size == 2) {
                    val x = coords[0].trim().toIntOrNull()
                    val y = coords[1].trim().toIntOrNull()
                    if (x != null && y != null) Pair(x, y) else null
                } else null
            }
            else -> null
        }
    }

    /**
     * Validates a normalized coordinate (0-999 range).
     */
    private fun isValidCoordinate(value: Int): Boolean {
        return value in 0..999
    }

    /**
     * Data class for swipe coordinates.
     */
    private data class SwipeCoordinates(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int
    )
}
