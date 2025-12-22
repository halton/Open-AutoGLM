package com.openautoglm.agent.agent

import android.util.Log

/**
 * Detects the type of task from a natural language description.
 *
 * Used for:
 * - Routing to appropriate inference backend
 * - Selecting task-specific prompts
 * - Determining UI flow (e.g., text extraction results vs. action execution)
 */
object TaskTypeDetector {

    private const val TAG = "TaskTypeDetector"

    /**
     * Task types that can be detected.
     */
    enum class TaskType {
        /** Extract text from image/screenshot */
        TEXT_EXTRACTION,

        /** Translate text (may combine with extraction) */
        TRANSLATION,

        /** Search and compare prices across apps */
        PRICE_COMPARISON,

        /** Order food from delivery apps */
        FOOD_ORDERING,

        /** Book travel tickets (train, flight) */
        TRAVEL_BOOKING,

        /** Track packages across shopping apps */
        LOGISTICS_TRACKING,

        /** Manage calendar events */
        CALENDAR,

        /** Send messages or respond to notifications */
        MESSAGING,

        /** Install or configure apps */
        APP_MANAGEMENT,

        /** Generic automation task */
        GENERAL_AUTOMATION
    }

    // Keyword patterns for each task type
    private val TEXT_EXTRACTION_KEYWORDS = listOf(
        // Chinese
        "提取文字", "识别文字", "读取文字", "文字识别", "OCR",
        "提取文本", "识别文本", "读取文本", "文本识别",
        "这图上写的什么", "图片里的文字", "截图里的文字",
        "帮我看看", "帮我读一下", "这是什么字",
        // English
        "extract text", "read text", "ocr", "recognize text",
        "what does this say", "read this", "text in image",
        "text from screenshot", "copy text", "get text"
    )

    private val TRANSLATION_KEYWORDS = listOf(
        // Chinese
        "翻译", "翻成", "译成", "译为",
        "中译英", "英译中", "日译中", "中译日",
        // English
        "translate", "translation", "to english", "to chinese",
        "in english", "in chinese", "convert to"
    )

    private val PRICE_COMPARISON_KEYWORDS = listOf(
        // Chinese
        "比价", "最便宜", "最低价", "价格对比", "哪里便宜",
        "淘宝", "京东", "拼多多", "天猫",
        // English
        "compare price", "cheapest", "lowest price", "best price",
        "price comparison", "amazon", "ebay"
    )

    private val FOOD_ORDERING_KEYWORDS = listOf(
        // Chinese
        "点餐", "外卖", "订餐", "叫外卖", "点外卖",
        "美团", "饿了么", "吃什么", "想吃",
        // English
        "order food", "food delivery", "uber eats", "doordash",
        "grubhub", "deliveroo"
    )

    private val TRAVEL_BOOKING_KEYWORDS = listOf(
        // Chinese
        "订票", "买票", "火车票", "机票", "高铁票",
        "12306", "携程", "去哪儿", "飞猪",
        // English
        "book ticket", "train ticket", "flight", "travel",
        "airline", "train", "amtrak", "expedia"
    )

    private val LOGISTICS_TRACKING_KEYWORDS = listOf(
        // Chinese
        "快递", "物流", "包裹", "查快递", "我的快递",
        "发货了吗", "到哪了", "签收",
        // English
        "track package", "delivery status", "shipping",
        "where is my order", "package tracking"
    )

    private val CALENDAR_KEYWORDS = listOf(
        // Chinese
        "日程", "日历", "预约", "安排", "会议",
        "提醒", "日程安排", "行程",
        // English
        "calendar", "schedule", "appointment", "meeting",
        "reminder", "event"
    )

    private val MESSAGING_KEYWORDS = listOf(
        // Chinese
        "发消息", "发短信", "发微信", "回复", "通知",
        "告诉", "转告", "联系",
        // English
        "send message", "text", "reply", "whatsapp",
        "telegram", "notify", "contact"
    )

    private val APP_MANAGEMENT_KEYWORDS = listOf(
        // Chinese
        "安装", "下载", "更新", "卸载", "应用",
        "软件", "APP", "程序",
        // English
        "install", "download", "update", "uninstall",
        "app", "application"
    )

    /**
     * Detects the task type from a task description.
     *
     * @param description The natural language task description
     * @return Detected task type
     */
    fun detect(description: String): TaskType {
        val lowerDescription = description.lowercase()

        Log.d(TAG, "Detecting task type for: ${description.take(100)}...")

        // Check each task type by priority
        val detectedType = when {
            containsAny(lowerDescription, TEXT_EXTRACTION_KEYWORDS) -> TaskType.TEXT_EXTRACTION
            containsAny(lowerDescription, TRANSLATION_KEYWORDS) -> TaskType.TRANSLATION
            containsAny(lowerDescription, PRICE_COMPARISON_KEYWORDS) -> TaskType.PRICE_COMPARISON
            containsAny(lowerDescription, FOOD_ORDERING_KEYWORDS) -> TaskType.FOOD_ORDERING
            containsAny(lowerDescription, TRAVEL_BOOKING_KEYWORDS) -> TaskType.TRAVEL_BOOKING
            containsAny(lowerDescription, LOGISTICS_TRACKING_KEYWORDS) -> TaskType.LOGISTICS_TRACKING
            containsAny(lowerDescription, CALENDAR_KEYWORDS) -> TaskType.CALENDAR
            containsAny(lowerDescription, MESSAGING_KEYWORDS) -> TaskType.MESSAGING
            containsAny(lowerDescription, APP_MANAGEMENT_KEYWORDS) -> TaskType.APP_MANAGEMENT
            else -> TaskType.GENERAL_AUTOMATION
        }

        Log.i(TAG, "Detected task type: $detectedType")
        return detectedType
    }

    /**
     * Detects all applicable task types (for complex multi-part tasks).
     *
     * @param description The natural language task description
     * @return Set of detected task types
     */
    fun detectAll(description: String): Set<TaskType> {
        val lowerDescription = description.lowercase()
        val types = mutableSetOf<TaskType>()

        if (containsAny(lowerDescription, TEXT_EXTRACTION_KEYWORDS)) types.add(TaskType.TEXT_EXTRACTION)
        if (containsAny(lowerDescription, TRANSLATION_KEYWORDS)) types.add(TaskType.TRANSLATION)
        if (containsAny(lowerDescription, PRICE_COMPARISON_KEYWORDS)) types.add(TaskType.PRICE_COMPARISON)
        if (containsAny(lowerDescription, FOOD_ORDERING_KEYWORDS)) types.add(TaskType.FOOD_ORDERING)
        if (containsAny(lowerDescription, TRAVEL_BOOKING_KEYWORDS)) types.add(TaskType.TRAVEL_BOOKING)
        if (containsAny(lowerDescription, LOGISTICS_TRACKING_KEYWORDS)) types.add(TaskType.LOGISTICS_TRACKING)
        if (containsAny(lowerDescription, CALENDAR_KEYWORDS)) types.add(TaskType.CALENDAR)
        if (containsAny(lowerDescription, MESSAGING_KEYWORDS)) types.add(TaskType.MESSAGING)
        if (containsAny(lowerDescription, APP_MANAGEMENT_KEYWORDS)) types.add(TaskType.APP_MANAGEMENT)

        if (types.isEmpty()) types.add(TaskType.GENERAL_AUTOMATION)

        return types
    }

    /**
     * Checks if a task is a text extraction task.
     */
    fun isTextExtractionTask(description: String): Boolean {
        return detect(description) == TaskType.TEXT_EXTRACTION
    }

    /**
     * Checks if a task involves translation.
     */
    fun isTranslationTask(description: String): Boolean {
        val types = detectAll(description)
        return types.contains(TaskType.TRANSLATION)
    }

    /**
     * Checks if a task should use on-device inference (for privacy/speed).
     *
     * Text extraction and simple tasks prefer on-device inference.
     */
    fun prefersOnDeviceInference(description: String): Boolean {
        val type = detect(description)
        return type in setOf(
            TaskType.TEXT_EXTRACTION,
            TaskType.TRANSLATION  // If purely translation without complex context
        )
    }

    /**
     * Checks if a task requires cloud inference (for complexity/accuracy).
     *
     * Multi-app tasks and complex automation prefer cloud inference.
     */
    fun prefersCloudInference(description: String): Boolean {
        val type = detect(description)
        return type in setOf(
            TaskType.PRICE_COMPARISON,
            TaskType.FOOD_ORDERING,
            TaskType.TRAVEL_BOOKING,
            TaskType.LOGISTICS_TRACKING
        )
    }

    /**
     * Gets the recommended prompt template for a task type.
     */
    fun getPromptTemplateHint(taskType: TaskType): String {
        return when (taskType) {
            TaskType.TEXT_EXTRACTION -> "text_extraction"
            TaskType.TRANSLATION -> "translation"
            TaskType.PRICE_COMPARISON -> "price_comparison"
            TaskType.FOOD_ORDERING -> "food_ordering"
            TaskType.TRAVEL_BOOKING -> "travel_booking"
            TaskType.LOGISTICS_TRACKING -> "logistics"
            TaskType.CALENDAR -> "calendar"
            TaskType.MESSAGING -> "messaging"
            TaskType.APP_MANAGEMENT -> "app_management"
            TaskType.GENERAL_AUTOMATION -> "general"
        }
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            text.contains(keyword.lowercase())
        }
    }
}
