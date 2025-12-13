package com.openautoglm.agent.model

/**
 * Data class representing a parsed response from the VLM model.
 *
 * Handles parsing of model output to extract thinking and action components.
 * Supports multiple response formats including:
 * - do(action=...) format for actions
 * - finish(message=...) format for completion
 * - <think>...</think><answer>...</answer> XML-like format
 */
data class ModelResponse(
    /**
     * The thinking/reasoning portion of the model's response.
     * Maximum 2000 characters.
     */
    val thinking: String,

    /**
     * The raw action string from the model.
     * Examples: "do(action=Tap, element=[500,800])", "finish(message=Task completed)"
     */
    val action: String,

    /**
     * The full, unparsed response from the model.
     */
    val rawContent: String,

    /**
     * Time taken for model inference in milliseconds.
     */
    val inferenceTimeMs: Long,

    /**
     * Identifier of the model that generated this response.
     */
    val modelUsed: String,

    /**
     * Number of tokens used in the response, if available.
     */
    val tokenCount: Int? = null
) {
    init {
        require(thinking.length <= MAX_THINKING_LENGTH) {
            "Thinking exceeds maximum length of $MAX_THINKING_LENGTH characters"
        }
    }

    companion object {
        private const val MAX_THINKING_LENGTH = 2000

        // Patterns for action parsing
        private val DO_ACTION_PATTERN = Regex("""do\s*\(action\s*=""", RegexOption.IGNORE_CASE)
        private val FINISH_PATTERN = Regex("""finish\s*\(message\s*=""", RegexOption.IGNORE_CASE)

        // Patterns for XML-like format
        private val THINK_TAG_PATTERN = Regex("""<think>(.*?)</think>""", RegexOption.DOT_MATCHES_ALL)
        private val ANSWER_TAG_PATTERN = Regex("""<answer>(.*?)</answer>""", RegexOption.DOT_MATCHES_ALL)

        /**
         * Factory method to create a ModelResponse from raw model output.
         *
         * Parses the content to extract thinking and action components using
         * multiple format detection strategies.
         *
         * @param content The raw response content from the model
         * @param inferenceTimeMs Time taken for inference in milliseconds
         * @param modelUsed Identifier of the model used
         * @param tokenCount Optional token count for the response
         * @return Parsed ModelResponse with extracted thinking and action
         */
        fun fromRawContent(
            content: String,
            inferenceTimeMs: Long,
            modelUsed: String,
            tokenCount: Int? = null
        ): ModelResponse {
            val (thinking, action) = parseContent(content)

            // Truncate thinking if it exceeds max length
            val truncatedThinking = if (thinking.length > MAX_THINKING_LENGTH) {
                thinking.substring(0, MAX_THINKING_LENGTH)
            } else {
                thinking
            }

            return ModelResponse(
                thinking = truncatedThinking,
                action = action,
                rawContent = content,
                inferenceTimeMs = inferenceTimeMs,
                modelUsed = modelUsed,
                tokenCount = tokenCount
            )
        }

        /**
         * Parses content to extract thinking and action components.
         *
         * @param content Raw model response content
         * @return Pair of (thinking, action) strings
         */
        private fun parseContent(content: String): Pair<String, String> {
            val trimmedContent = content.trim()

            // Try parsing finish(message=...) format first
            val finishMatch = FINISH_PATTERN.find(trimmedContent)
            if (finishMatch != null) {
                val thinking = trimmedContent.substring(0, finishMatch.range.first).trim()
                val action = extractActionFromIndex(trimmedContent, finishMatch.range.first)
                return Pair(thinking, action)
            }

            // Try parsing do(action=...) format
            val doMatch = DO_ACTION_PATTERN.find(trimmedContent)
            if (doMatch != null) {
                val thinking = trimmedContent.substring(0, doMatch.range.first).trim()
                val action = extractActionFromIndex(trimmedContent, doMatch.range.first)
                return Pair(thinking, action)
            }

            // Fallback to <think>...</think><answer>...</answer> format
            val thinkMatch = THINK_TAG_PATTERN.find(trimmedContent)
            val answerMatch = ANSWER_TAG_PATTERN.find(trimmedContent)

            if (thinkMatch != null || answerMatch != null) {
                val thinking = thinkMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                val action = answerMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                return Pair(thinking, action)
            }

            // No recognizable pattern - treat entire content as action with empty thinking
            return Pair("", trimmedContent)
        }

        /**
         * Extracts the complete action string starting from the given index.
         * Handles nested parentheses to capture the full action.
         *
         * @param content Full content string
         * @param startIndex Index where the action begins
         * @return The complete action string
         */
        private fun extractActionFromIndex(content: String, startIndex: Int): String {
            val actionStart = content.substring(startIndex)

            // Find matching parentheses
            var parenCount = 0
            var inString = false
            var stringChar: Char? = null
            var endIndex = -1

            for (i in actionStart.indices) {
                val char = actionStart[i]

                // Handle string literals
                if ((char == '"' || char == '\'') && (i == 0 || actionStart[i - 1] != '\\')) {
                    if (!inString) {
                        inString = true
                        stringChar = char
                    } else if (char == stringChar) {
                        inString = false
                        stringChar = null
                    }
                    continue
                }

                if (!inString) {
                    when (char) {
                        '(' -> parenCount++
                        ')' -> {
                            parenCount--
                            if (parenCount == 0) {
                                endIndex = i + 1
                                break
                            }
                        }
                    }
                }
            }

            return if (endIndex > 0) {
                actionStart.substring(0, endIndex).trim()
            } else {
                actionStart.trim()
            }
        }
    }

    /**
     * Checks if this response represents a finished/completed action.
     */
    fun isFinishAction(): Boolean {
        return FINISH_PATTERN.containsMatchIn(action)
    }

    /**
     * Checks if this response contains a do action.
     */
    fun isDoAction(): Boolean {
        return DO_ACTION_PATTERN.containsMatchIn(action)
    }

    /**
     * Extracts the action type from a do() action string.
     * Example: "do(action=Tap, element=[500,800])" returns "Tap"
     *
     * @return The action type or null if not a do action
     */
    fun extractActionType(): String? {
        if (!isDoAction()) return null

        val actionTypePattern = Regex("""do\s*\(\s*action\s*=\s*(\w+)""", RegexOption.IGNORE_CASE)
        return actionTypePattern.find(action)?.groupValues?.getOrNull(1)
    }

    /**
     * Extracts the finish message from a finish() action.
     * Example: "finish(message=Task completed)" returns "Task completed"
     *
     * @return The finish message or null if not a finish action
     */
    fun extractFinishMessage(): String? {
        if (!isFinishAction()) return null

        val messagePattern = Regex("""finish\s*\(\s*message\s*=\s*(.+?)\s*\)""", RegexOption.IGNORE_CASE)
        return messagePattern.find(action)?.groupValues?.getOrNull(1)
    }
}
