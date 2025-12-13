package com.openautoglm.agent.core

import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionType
import com.openautoglm.agent.model.ModelResponse
import java.util.UUID

/**
 * Parser for converting VLM model responses into executable Action objects.
 *
 * Supports multiple model output formats:
 * - do(action=Tap, element=[500, 800])
 * - do(action=Swipe, element=[100, 500, 100, 200]) or do(action=Swipe, start=[100, 500], end=[100, 200])
 * - do(action=Type, text="hello")
 * - do(action=Launch, app="com.example.app")
 * - finish(message="Task completed")
 *
 * Reference: Python implementation in phone_agent/actions/handler.py
 */
object ResponseParser {

    // Regex patterns for parsing model output
    private val ACTION_TYPE_PATTERN = Regex(
        """do\s*\(\s*action\s*=\s*(\w+(?:\s+\w+)?)""",
        RegexOption.IGNORE_CASE
    )

    private val ELEMENT_PATTERN = Regex(
        """element\s*=\s*\[([^\]]+)]"""
    )

    private val START_PATTERN = Regex(
        """start\s*=\s*\[([^\]]+)]"""
    )

    private val END_PATTERN = Regex(
        """end\s*=\s*\[([^\]]+)]"""
    )

    private val TEXT_PATTERN = Regex(
        """text\s*=\s*["']([^"']*?)["']"""
    )

    private val APP_PATTERN = Regex(
        """app\s*=\s*["']([^"']*?)["']"""
    )

    private val DURATION_PATTERN = Regex(
        """duration\s*=\s*["']?([^"',)]+)["']?"""
    )

    private val MESSAGE_PATTERN = Regex(
        """message\s*=\s*["']?([^"')]+)["']?"""
    )

    private val FINISH_PATTERN = Regex(
        """finish\s*\(\s*message\s*=""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses a ModelResponse into an executable Action.
     *
     * @param modelResponse The parsed model response containing action string
     * @param taskId The UUID of the task this action belongs to
     * @return Parsed Action or null if parsing fails
     */
    fun parseAction(modelResponse: ModelResponse, taskId: UUID): Action? {
        val actionString = modelResponse.action

        // Check if it's a finish action
        if (isFinishAction(modelResponse)) {
            return Action(
                taskId = taskId,
                type = ActionType.FINISH,
                parameters = buildMap {
                    getFinishMessage(modelResponse)?.let { put("message", it) }
                },
                thinking = modelResponse.thinking
            )
        }

        // Parse the action type
        val actionType = parseActionType(actionString) ?: return null

        // Build parameters based on action type
        val parameters = buildParametersForAction(actionType, actionString)

        return Action(
            taskId = taskId,
            type = actionType,
            parameters = parameters,
            thinking = modelResponse.thinking
        )
    }

    /**
     * Extracts the action type from an action string.
     *
     * Supports action names like:
     * - Tap, Double Tap, Long Press
     * - Swipe, Type, Type_Name
     * - Back, Home, Launch
     * - Wait, Take_over
     *
     * @param actionString The raw action string from model output
     * @return ActionType enum value or null if not recognized
     */
    fun parseActionType(actionString: String): ActionType? {
        val match = ACTION_TYPE_PATTERN.find(actionString) ?: return null
        val actionName = match.groupValues[1].trim()

        return when (actionName.lowercase().replace("_", " ").replace("-", " ")) {
            "tap" -> ActionType.TAP
            "double tap", "doubletap" -> ActionType.DOUBLE_TAP
            "long press", "longpress" -> ActionType.LONG_PRESS
            "swipe" -> ActionType.SWIPE
            "type", "type name", "typename" -> ActionType.TYPE
            "back" -> ActionType.BACK
            "home" -> ActionType.HOME
            "launch" -> ActionType.LAUNCH
            "wait" -> ActionType.WAIT
            "take over", "takeover" -> ActionType.TAKE_OVER
            "finish" -> ActionType.FINISH
            else -> null
        }
    }

    /**
     * Extracts tap coordinates [x, y] from an action string.
     *
     * Parses the element parameter from formats like:
     * - do(action=Tap, element=[500, 800])
     * - do(action=Tap, element=[500,800])
     *
     * @param actionString The raw action string
     * @return Pair of (x, y) coordinates or null if not found
     */
    fun parseCoordinates(actionString: String): Pair<Int, Int>? {
        val match = ELEMENT_PATTERN.find(actionString) ?: return null
        val coordsString = match.groupValues[1]

        val coords = parseIntList(coordsString)
        if (coords.size < 2) return null

        return Pair(coords[0], coords[1])
    }

    /**
     * Extracts swipe coordinates [x1, y1, x2, y2] from an action string.
     *
     * Supports two formats:
     * - element=[x1, y1, x2, y2] - all four coordinates in one array
     * - start=[x1, y1], end=[x2, y2] - separate start and end coordinates
     *
     * @param actionString The raw action string
     * @return List of [x1, y1, x2, y2] or null if not found
     */
    fun parseSwipeCoordinates(actionString: String): List<Int>? {
        // First try element format with 4 coordinates
        val elementMatch = ELEMENT_PATTERN.find(actionString)
        if (elementMatch != null) {
            val coords = parseIntList(elementMatch.groupValues[1])
            if (coords.size >= 4) {
                return coords.take(4)
            }
        }

        // Try start/end format
        val startMatch = START_PATTERN.find(actionString)
        val endMatch = END_PATTERN.find(actionString)

        if (startMatch != null && endMatch != null) {
            val startCoords = parseIntList(startMatch.groupValues[1])
            val endCoords = parseIntList(endMatch.groupValues[1])

            if (startCoords.size >= 2 && endCoords.size >= 2) {
                return listOf(
                    startCoords[0], startCoords[1],
                    endCoords[0], endCoords[1]
                )
            }
        }

        return null
    }

    /**
     * Extracts the text parameter for TYPE action.
     *
     * Parses formats like:
     * - do(action=Type, text="hello world")
     * - do(action=Type, text='hello world')
     *
     * @param actionString The raw action string
     * @return The text content or null if not found
     */
    fun parseText(actionString: String): String? {
        val match = TEXT_PATTERN.find(actionString) ?: return null
        return match.groupValues[1]
    }

    /**
     * Extracts the package/app name for LAUNCH action.
     *
     * Parses formats like:
     * - do(action=Launch, app="com.example.app")
     * - do(action=Launch, app='WeChat')
     *
     * @param actionString The raw action string
     * @return The package/app name or null if not found
     */
    fun parsePackageName(actionString: String): String? {
        val match = APP_PATTERN.find(actionString) ?: return null
        return match.groupValues[1]
    }

    /**
     * Parses the duration parameter for WAIT action.
     *
     * Parses formats like:
     * - do(action=Wait, duration="2 seconds")
     * - do(action=Wait, duration="1.5")
     *
     * @param actionString The raw action string
     * @return Duration in milliseconds or null if not found
     */
    fun parseDuration(actionString: String): Long? {
        val match = DURATION_PATTERN.find(actionString) ?: return null
        val durationStr = match.groupValues[1].trim()

        return try {
            // Remove "seconds" suffix if present
            val numericPart = durationStr
                .lowercase()
                .replace("seconds", "")
                .replace("second", "")
                .replace("s", "")
                .trim()

            (numericPart.toDouble() * 1000).toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Checks if the model response indicates task completion.
     *
     * @param modelResponse The model response to check
     * @return true if this is a finish action
     */
    fun isFinishAction(modelResponse: ModelResponse): Boolean {
        return modelResponse.isFinishAction()
    }

    /**
     * Extracts the completion message from a finish action.
     *
     * Parses formats like:
     * - finish(message="Task completed successfully")
     * - finish(message=Task completed)
     *
     * @param modelResponse The model response containing finish action
     * @return The finish message or null if not found
     */
    fun getFinishMessage(modelResponse: ModelResponse): String? {
        if (!isFinishAction(modelResponse)) return null

        // Use ModelResponse's built-in extraction first
        val builtInMessage = modelResponse.extractFinishMessage()
        if (builtInMessage != null) {
            return cleanQuotes(builtInMessage)
        }

        // Fallback to our own pattern
        val match = MESSAGE_PATTERN.find(modelResponse.action) ?: return null
        return cleanQuotes(match.groupValues[1])
    }

    /**
     * Parses the message parameter for actions that include messages.
     *
     * Used for:
     * - Sensitive operation confirmation messages in Tap actions
     * - Takeover request messages
     *
     * @param actionString The raw action string
     * @return The message content or null if not found
     */
    fun parseMessage(actionString: String): String? {
        val match = MESSAGE_PATTERN.find(actionString) ?: return null
        return cleanQuotes(match.groupValues[1])
    }

    // ==================== Private Helper Methods ====================

    /**
     * Builds the parameters map for a specific action type.
     */
    private fun buildParametersForAction(
        actionType: ActionType,
        actionString: String
    ): Map<String, Any> {
        return when (actionType) {
            ActionType.TAP, ActionType.DOUBLE_TAP, ActionType.LONG_PRESS -> {
                buildMap {
                    parseCoordinates(actionString)?.let { (x, y) ->
                        put("x", x)
                        put("y", y)
                    }
                    parseMessage(actionString)?.let { put("message", it) }
                }
            }

            ActionType.SWIPE -> {
                buildMap {
                    parseSwipeCoordinates(actionString)?.let { coords ->
                        put("startX", coords[0])
                        put("startY", coords[1])
                        put("endX", coords[2])
                        put("endY", coords[3])
                    }
                }
            }

            ActionType.TYPE -> {
                buildMap {
                    parseText(actionString)?.let { put("text", it) }
                }
            }

            ActionType.LAUNCH -> {
                buildMap {
                    parsePackageName(actionString)?.let { put("app", it) }
                }
            }

            ActionType.WAIT -> {
                buildMap {
                    parseDuration(actionString)?.let { put("durationMs", it) }
                }
            }

            ActionType.TAKE_OVER -> {
                buildMap {
                    parseMessage(actionString)?.let { put("message", it) }
                }
            }

            ActionType.BACK, ActionType.HOME -> {
                emptyMap()
            }

            ActionType.FINISH -> {
                buildMap {
                    parseMessage(actionString)?.let { put("message", it) }
                }
            }
        }
    }

    /**
     * Parses a comma-separated string of integers.
     */
    private fun parseIntList(coordsString: String): List<Int> {
        return coordsString
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    }

    /**
     * Removes surrounding quotes from a string.
     */
    private fun cleanQuotes(str: String): String {
        return str.trim().removeSurrounding("\"").removeSurrounding("'")
    }
}
