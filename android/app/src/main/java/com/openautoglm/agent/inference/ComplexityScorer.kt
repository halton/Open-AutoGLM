package com.openautoglm.agent.inference

import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.data.entities.InferenceMode

/**
 * Scores task complexity to determine optimal inference routing (on-device vs cloud).
 *
 * The complexity scorer analyzes task characteristics to decide whether a task
 * should be handled by on-device inference (faster, more private) or cloud inference
 * (more capable, handles complex scenarios).
 *
 * Scoring criteria:
 * - Task type and keywords (e.g., "compare prices" is complex)
 * - App category (e.g., e-commerce tasks are more complex)
 * - UI state complexity (number of elements, nested structures)
 * - Historical success rate for similar tasks
 */
object ComplexityScorer {

    /**
     * Complexity thresholds for routing decisions.
     */
    private const val LOW_COMPLEXITY_THRESHOLD = 30
    private const val MEDIUM_COMPLEXITY_THRESHOLD = 60

    /**
     * Task keyword patterns indicating high complexity.
     */
    private val COMPLEX_KEYWORDS = setOf(
        // Comparison and analysis
        "compare", "比较", "分析", "analyze", "find best", "最好", "最便宜", "cheapest",

        // Multi-step workflows
        "book", "预订", "购买", "buy", "order", "下单", "pay", "支付", "checkout",

        // Search and filtering
        "search for", "搜索", "find", "查找", "filter", "筛选", "sort", "排序",

        // Extraction and summarization
        "extract", "提取", "summarize", "总结", "translate", "翻译",

        // Multi-app coordination
        "across apps", "跨应用", "all apps", "所有应用", "track", "追踪"
    )

    /**
     * Task keyword patterns indicating low complexity (simple UI operations).
     */
    private val SIMPLE_KEYWORDS = setOf(
        // Basic navigation
        "open", "打开", "close", "关闭", "back", "返回", "home", "主页",

        // Simple actions
        "tap", "点击", "click", "swipe", "滑动", "scroll", "滚动",

        // Single-step operations
        "send message", "发送消息", "take photo", "拍照", "play", "播放"
    )

    /**
     * App categories that typically require more complex reasoning.
     */
    private val COMPLEX_APP_CATEGORIES = setOf(
        AppCategory.ECOMMERCE,
        AppCategory.FINANCE,
        AppCategory.TRAVEL
    )

    /**
     * Calculates a complexity score for a given task.
     *
     * @param taskDescription The natural language task description
     * @param appCategory The category of the target app (if known)
     * @param uiElementCount Number of UI elements on the current screen (optional)
     * @return Complexity score from 0-100, where higher = more complex
     */
    fun scoreTaskComplexity(
        taskDescription: String,
        appCategory: AppCategory? = null,
        uiElementCount: Int? = null
    ): Int {
        var score = 0

        // 1. Keyword-based scoring (0-40 points)
        score += scoreByKeywords(taskDescription)

        // 2. App category scoring (0-20 points)
        if (appCategory != null) {
            score += scoreByAppCategory(appCategory)
        }

        // 3. UI complexity scoring (0-20 points)
        if (uiElementCount != null) {
            score += scoreByUIComplexity(uiElementCount)
        }

        // 4. Task length and structure scoring (0-20 points)
        score += scoreByTaskStructure(taskDescription)

        return score.coerceIn(0, 100)
    }

    /**
     * Recommends an inference mode based on complexity score.
     *
     * @param complexityScore The calculated complexity score (0-100)
     * @param batteryLevel Device battery level percentage (0-100), optional
     * @param networkAvailable Whether cloud network is available
     * @return Recommended inference mode
     */
    fun recommendInferenceMode(
        complexityScore: Int,
        batteryLevel: Int? = null,
        networkAvailable: Boolean = true
    ): InferenceMode {
        // If network unavailable, must use on-device
        if (!networkAvailable) {
            return InferenceMode.ON_DEVICE
        }

        // If battery is critically low (<15%), prefer on-device
        if (batteryLevel != null && batteryLevel < 15) {
            return InferenceMode.ON_DEVICE
        }

        // Route based on complexity
        return when {
            complexityScore < LOW_COMPLEXITY_THRESHOLD -> InferenceMode.ON_DEVICE
            complexityScore < MEDIUM_COMPLEXITY_THRESHOLD -> {
                // Medium complexity: consider battery level
                if (batteryLevel != null && batteryLevel < 30) {
                    InferenceMode.ON_DEVICE
                } else {
                    InferenceMode.CLOUD
                }
            }
            else -> InferenceMode.CLOUD
        }
    }

    /**
     * Scores complexity based on task keywords.
     */
    private fun scoreByKeywords(taskDescription: String): Int {
        val lowercaseTask = taskDescription.lowercase()

        var score = 0

        // Check for complex keywords (+10 per match, max 30)
        val complexMatches = COMPLEX_KEYWORDS.count { keyword ->
            lowercaseTask.contains(keyword.lowercase())
        }
        score += (complexMatches * 10).coerceAtMost(30)

        // Check for simple keywords (-5 per match, min -10)
        val simpleMatches = SIMPLE_KEYWORDS.count { keyword ->
            lowercaseTask.contains(keyword.lowercase())
        }
        score -= (simpleMatches * 5).coerceAtMost(10)

        return score.coerceIn(0, 40)
    }

    /**
     * Scores complexity based on app category.
     */
    private fun scoreByAppCategory(category: AppCategory): Int {
        return when (category) {
            in COMPLEX_APP_CATEGORIES -> 15
            AppCategory.PRODUCTIVITY -> 10
            AppCategory.SOCIAL -> 5
            else -> 0
        }
    }

    /**
     * Scores complexity based on UI element count.
     */
    private fun scoreByUIComplexity(elementCount: Int): Int {
        return when {
            elementCount > 50 -> 20  // Very complex UI
            elementCount > 30 -> 15  // Complex UI
            elementCount > 15 -> 10  // Moderate UI
            elementCount > 5 -> 5    // Simple UI
            else -> 0                // Minimal UI
        }
    }

    /**
     * Scores complexity based on task description structure.
     */
    private fun scoreByTaskStructure(taskDescription: String): Int {
        var score = 0

        // Long tasks tend to be more complex
        val wordCount = taskDescription.split("\\s+".toRegex()).size
        score += when {
            wordCount > 20 -> 10
            wordCount > 10 -> 5
            else -> 0
        }

        // Multiple clauses/sentences indicate complexity
        val clauseCount = taskDescription.split("[,;]".toRegex()).size +
                         taskDescription.split("[.!?]".toRegex()).filter { it.isNotBlank() }.size
        score += when {
            clauseCount > 3 -> 10
            clauseCount > 1 -> 5
            else -> 0
        }

        return score.coerceIn(0, 20)
    }

    /**
     * Data class representing a complexity analysis result.
     */
    data class ComplexityAnalysis(
        val score: Int,
        val recommendedMode: InferenceMode,
        val reasoning: String
    )

    /**
     * Performs a full complexity analysis with reasoning.
     *
     * @param taskDescription The task description
     * @param appCategory The app category (optional)
     * @param uiElementCount UI element count (optional)
     * @param batteryLevel Battery level (optional)
     * @param networkAvailable Network availability
     * @return ComplexityAnalysis with score, recommendation, and reasoning
     */
    fun analyzeComplexity(
        taskDescription: String,
        appCategory: AppCategory? = null,
        uiElementCount: Int? = null,
        batteryLevel: Int? = null,
        networkAvailable: Boolean = true
    ): ComplexityAnalysis {
        val score = scoreTaskComplexity(taskDescription, appCategory, uiElementCount)
        val mode = recommendInferenceMode(score, batteryLevel, networkAvailable)

        val reasoning = buildString {
            append("Complexity score: $score/100. ")

            when {
                !networkAvailable -> append("Network unavailable - forcing on-device. ")
                batteryLevel != null && batteryLevel < 15 ->
                    append("Low battery ($batteryLevel%) - preferring on-device. ")
                score < LOW_COMPLEXITY_THRESHOLD ->
                    append("Simple task - using fast on-device inference. ")
                score < MEDIUM_COMPLEXITY_THRESHOLD ->
                    append("Moderate complexity - using ${if (mode == InferenceMode.CLOUD) "cloud" else "on-device"}. ")
                else ->
                    append("Complex task - using powerful cloud inference. ")
            }
        }

        return ComplexityAnalysis(score, mode, reasoning)
    }
}
