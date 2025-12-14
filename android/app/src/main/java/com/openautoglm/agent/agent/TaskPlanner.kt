package com.openautoglm.agent.agent

import com.openautoglm.agent.data.entities.ActionType
import com.openautoglm.agent.data.entities.AppCategory

/**
 * Task planner for multi-step task orchestration.
 *
 * The TaskPlanner analyzes natural language task descriptions and breaks them
 * down into logical execution phases. It helps the agent understand:
 * - What apps need to be involved
 * - What sequence of actions is likely needed
 * - What intermediate goals to achieve
 * - When to switch between apps
 *
 * This is used for complex tasks like:
 * - "Compare prices for iPhone 15 across Taobao, JD, and Amazon"
 * - "Order food from restaurant X on delivery app Y"
 * - "Book a train ticket from A to B on date C"
 */
object TaskPlanner {

    /**
     * Analyzes a task and creates an execution plan.
     *
     * @param taskDescription Natural language task description
     * @param language Language code ("en" or "zh")
     * @return TaskPlan with analysis and suggested phases
     */
    fun analyzeTask(taskDescription: String, language: String = "en"): TaskPlan {
        val normalizedTask = taskDescription.lowercase()

        // Detect task category
        val category = detectTaskCategory(normalizedTask)

        // Extract key entities (apps, products, locations, etc.)
        val entities = extractEntities(normalizedTask, language)

        // Determine execution phases
        val phases = planExecutionPhases(category, entities, normalizedTask)

        // Estimate complexity
        val complexity = estimateComplexity(category, entities, phases.size)

        return TaskPlan(
            description = taskDescription,
            category = category,
            entities = entities,
            phases = phases,
            complexity = complexity,
            requiresMultipleApps = entities.apps.size > 1,
            requiresUserInput = detectUserInputNeeded(normalizedTask)
        )
    }

    /**
     * Detects the category of the task.
     */
    private fun detectTaskCategory(task: String): TaskCategory {
        return when {
            // E-commerce tasks
            task.contains("buy") || task.contains("purchase") || task.contains("shop") ||
            task.contains("购买") || task.contains("下单") || task.contains("买") ->
                TaskCategory.ECOMMERCE_PURCHASE

            task.contains("compare price") || task.contains("find best price") ||
            task.contains("比价") || task.contains("比较价格") ->
                TaskCategory.ECOMMERCE_COMPARISON

            // Food ordering
            task.contains("order food") || task.contains("delivery") ||
            task.contains("点外卖") || task.contains("叫外卖") || task.contains("订餐") ->
                TaskCategory.FOOD_ORDERING

            // Travel booking
            task.contains("book") && (task.contains("train") || task.contains("flight") || task.contains("hotel")) ||
            task.contains("预订") && (task.contains("火车") || task.contains("飞机") || task.contains("酒店")) ->
                TaskCategory.TRAVEL_BOOKING

            // Messaging
            task.contains("send message") || task.contains("text") || task.contains("chat") ||
            task.contains("发消息") || task.contains("发送") || task.contains("聊天") ->
                TaskCategory.MESSAGING

            // Information extraction
            task.contains("extract") || task.contains("read") || task.contains("get text") ||
            task.contains("提取") || task.contains("读取") || task.contains("识别") ->
                TaskCategory.TEXT_EXTRACTION

            // Package tracking
            task.contains("track") || task.contains("delivery status") || task.contains("package") ||
            task.contains("追踪") || task.contains("物流") || task.contains("快递") ->
                TaskCategory.PACKAGE_TRACKING

            // App installation
            task.contains("install") || task.contains("download app") ||
            task.contains("安装") || task.contains("下载应用") ->
                TaskCategory.APP_INSTALLATION

            // Default
            else -> TaskCategory.GENERAL
        }
    }

    /**
     * Extracts key entities from the task description.
     */
    private fun extractEntities(task: String, language: String): TaskEntities {
        val apps = mutableListOf<String>()
        val items = mutableListOf<String>()
        val locations = mutableListOf<String>()
        val contacts = mutableListOf<String>()

        // Common app names (English and Chinese)
        val appKeywords = mapOf(
            "taobao" to "淘宝",
            "jd" to "京东",
            "amazon" to "亚马逊",
            "meituan" to "美团",
            "eleme" to "饿了么",
            "wechat" to "微信",
            "12306" to "12306",
            "ctrip" to "携程"
        )

        for ((en, zh) in appKeywords) {
            if (task.contains(en, ignoreCase = true) || task.contains(zh)) {
                apps.add(en)
            }
        }

        // Extract quoted items (e.g., "iPhone 15", "宫保鸡丁")
        val quotedItems = Regex("""["']([^"']+)["']""").findAll(task)
        items.addAll(quotedItems.map { it.groupValues[1] })

        return TaskEntities(
            apps = apps,
            items = items,
            locations = locations,
            contacts = contacts
        )
    }

    /**
     * Plans execution phases based on task analysis.
     */
    private fun planExecutionPhases(
        category: TaskCategory,
        entities: TaskEntities,
        task: String
    ): List<ExecutionPhase> {
        return when (category) {
            TaskCategory.ECOMMERCE_COMPARISON -> {
                buildList {
                    entities.apps.forEach { app ->
                        add(ExecutionPhase(
                            name = "Search on $app",
                            description = "Open $app and search for ${entities.items.firstOrNull() ?: "product"}",
                            requiredActions = listOf(ActionType.LAUNCH, ActionType.TAP, ActionType.TYPE, ActionType.SWIPE),
                            estimatedSteps = 5
                        ))
                        add(ExecutionPhase(
                            name = "Extract price from $app",
                            description = "Find and record the price",
                            requiredActions = listOf(ActionType.TAP, ActionType.SWIPE),
                            estimatedSteps = 3
                        ))
                    }
                    add(ExecutionPhase(
                        name = "Compare and decide",
                        description = "Compare prices and select best option",
                        requiredActions = listOf(ActionType.FINISH),
                        estimatedSteps = 1
                    ))
                }
            }

            TaskCategory.ECOMMERCE_PURCHASE -> {
                listOf(
                    ExecutionPhase(
                        name = "Open shopping app",
                        description = "Launch ${entities.apps.firstOrNull() ?: "shopping app"}",
                        requiredActions = listOf(ActionType.LAUNCH),
                        estimatedSteps = 1
                    ),
                    ExecutionPhase(
                        name = "Search for product",
                        description = "Search for ${entities.items.firstOrNull() ?: "product"}",
                        requiredActions = listOf(ActionType.TAP, ActionType.TYPE),
                        estimatedSteps = 3
                    ),
                    ExecutionPhase(
                        name = "Add to cart",
                        description = "Select product and add to cart",
                        requiredActions = listOf(ActionType.TAP, ActionType.SWIPE),
                        estimatedSteps = 4
                    ),
                    ExecutionPhase(
                        name = "Checkout",
                        description = "Complete purchase",
                        requiredActions = listOf(ActionType.TAP),
                        estimatedSteps = 3
                    )
                )
            }

            TaskCategory.FOOD_ORDERING -> {
                listOf(
                    ExecutionPhase(
                        name = "Open delivery app",
                        description = "Launch ${entities.apps.firstOrNull() ?: "delivery app"}",
                        requiredActions = listOf(ActionType.LAUNCH),
                        estimatedSteps = 1
                    ),
                    ExecutionPhase(
                        name = "Search restaurant",
                        description = "Find and select restaurant",
                        requiredActions = listOf(ActionType.TAP, ActionType.TYPE),
                        estimatedSteps = 3
                    ),
                    ExecutionPhase(
                        name = "Select items",
                        description = "Browse menu and add items to cart",
                        requiredActions = listOf(ActionType.TAP, ActionType.SWIPE),
                        estimatedSteps = 5
                    ),
                    ExecutionPhase(
                        name = "Place order",
                        description = "Checkout and confirm order",
                        requiredActions = listOf(ActionType.TAP),
                        estimatedSteps = 3
                    )
                )
            }

            TaskCategory.MESSAGING -> {
                listOf(
                    ExecutionPhase(
                        name = "Open messaging app",
                        description = "Launch ${entities.apps.firstOrNull() ?: "messaging app"}",
                        requiredActions = listOf(ActionType.LAUNCH),
                        estimatedSteps = 1
                    ),
                    ExecutionPhase(
                        name = "Find contact",
                        description = "Search for and select contact",
                        requiredActions = listOf(ActionType.TAP, ActionType.TYPE),
                        estimatedSteps = 2
                    ),
                    ExecutionPhase(
                        name = "Send message",
                        description = "Type and send message",
                        requiredActions = listOf(ActionType.TAP, ActionType.TYPE),
                        estimatedSteps = 2
                    )
                )
            }

            else -> {
                listOf(
                    ExecutionPhase(
                        name = "Execute task",
                        description = "Complete the requested task",
                        requiredActions = listOf(ActionType.TAP, ActionType.SWIPE, ActionType.TYPE),
                        estimatedSteps = 5
                    )
                )
            }
        }
    }

    /**
     * Estimates task complexity (0-100).
     */
    private fun estimateComplexity(
        category: TaskCategory,
        entities: TaskEntities,
        phaseCount: Int
    ): Int {
        var complexity = 0

        // Base complexity by category
        complexity += when (category) {
            TaskCategory.ECOMMERCE_COMPARISON -> 60
            TaskCategory.ECOMMERCE_PURCHASE -> 50
            TaskCategory.TRAVEL_BOOKING -> 55
            TaskCategory.FOOD_ORDERING -> 45
            TaskCategory.PACKAGE_TRACKING -> 40
            TaskCategory.MESSAGING -> 30
            TaskCategory.TEXT_EXTRACTION -> 25
            TaskCategory.APP_INSTALLATION -> 35
            TaskCategory.GENERAL -> 40
        }

        // Additional complexity for multiple apps
        complexity += (entities.apps.size - 1) * 10

        // Additional complexity for multiple items
        complexity += entities.items.size * 5

        // Phase count factor
        complexity += phaseCount * 2

        return complexity.coerceIn(0, 100)
    }

    /**
     * Detects if user input will be needed during execution.
     */
    private fun detectUserInputNeeded(task: String): Boolean {
        val userInputKeywords = listOf(
            "login", "password", "pay", "confirm", "verification",
            "登录", "密码", "支付", "确认", "验证"
        )
        return userInputKeywords.any { task.contains(it, ignoreCase = true) }
    }
}

/**
 * Task execution plan.
 */
data class TaskPlan(
    val description: String,
    val category: TaskCategory,
    val entities: TaskEntities,
    val phases: List<ExecutionPhase>,
    val complexity: Int,
    val requiresMultipleApps: Boolean,
    val requiresUserInput: Boolean
)

/**
 * Task category classification.
 */
enum class TaskCategory {
    ECOMMERCE_PURCHASE,
    ECOMMERCE_COMPARISON,
    FOOD_ORDERING,
    TRAVEL_BOOKING,
    MESSAGING,
    TEXT_EXTRACTION,
    PACKAGE_TRACKING,
    APP_INSTALLATION,
    GENERAL
}

/**
 * Extracted entities from task description.
 */
data class TaskEntities(
    val apps: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val contacts: List<String> = emptyList()
)

/**
 * Single execution phase of a task.
 */
data class ExecutionPhase(
    val name: String,
    val description: String,
    val requiredActions: List<ActionType>,
    val estimatedSteps: Int
)
