package com.openautoglm.agent.agent

import android.util.Log

/**
 * Helper class for matching menu items from natural language descriptions.
 *
 * This class provides utilities for:
 * - Parsing food item descriptions from user requests
 * - Generating VLM prompts for menu navigation
 * - Matching requested items to menu items on screen
 * - Handling quantity and customization options
 *
 * Works in conjunction with the VLM agent to navigate food delivery app menus.
 */
object MenuMatcher {

    private const val TAG = "MenuMatcher"

    /**
     * Data class representing a menu item request.
     */
    data class MenuItemRequest(
        val itemName: String,
        val quantity: Int = 1,
        val size: String? = null,
        val customizations: List<String> = emptyList(),
        val excludeIngredients: List<String> = emptyList()
    ) {
        /**
         * Generates a search-friendly string for the item.
         */
        fun toSearchTerm(): String {
            return buildString {
                append(itemName)
                size?.let { append(" $it") }
            }
        }

        /**
         * Checks if this request has customizations.
         */
        fun hasCustomizations(): Boolean {
            return customizations.isNotEmpty() || excludeIngredients.isNotEmpty()
        }
    }

    /**
     * Data class representing a found menu item.
     */
    data class MenuItem(
        val name: String,
        val price: String? = null,
        val description: String? = null,
        val isAvailable: Boolean = true,
        val category: String? = null,
        val sizes: List<String> = emptyList(),
        val options: List<String> = emptyList()
    )

    /**
     * Common food item synonyms for matching.
     */
    private val foodSynonyms = mapOf(
        // Pizza variations
        "pepperoni pizza" to listOf("pepperoni", "pizza pepperoni"),
        "cheese pizza" to listOf("plain pizza", "margherita", "cheese"),
        "hawaiian pizza" to listOf("hawaiian", "ham pineapple"),

        // Burger variations
        "cheeseburger" to listOf("cheese burger", "burger with cheese"),
        "hamburger" to listOf("burger", "beef burger"),
        "chicken burger" to listOf("chicken sandwich", "crispy chicken"),

        // Chinese food
        "kung pao chicken" to listOf("gong bao chicken", "kung pao", "宫保鸡丁"),
        "fried rice" to listOf("炒饭", "chao fan"),
        "dumplings" to listOf("jiaozi", "饺子", "pot stickers"),
        "noodles" to listOf("mian", "面条", "lo mein", "chow mein"),

        // Japanese food
        "ramen" to listOf("拉面", "la mian", "noodle soup"),
        "sushi" to listOf("寿司", "maki", "nigiri"),
        "teriyaki" to listOf("照烧", "teriyaki chicken", "teriyaki beef"),

        // Drinks
        "cola" to listOf("coke", "coca cola", "可乐", "pepsi"),
        "milk tea" to listOf("bubble tea", "boba", "奶茶", "珍珠奶茶"),
        "coffee" to listOf("咖啡", "latte", "cappuccino", "americano")
    )

    /**
     * Size keywords mapping.
     */
    private val sizeKeywords = mapOf(
        "small" to listOf("小", "S", "small", "mini"),
        "medium" to listOf("中", "M", "medium", "regular"),
        "large" to listOf("大", "L", "large", "big"),
        "extra large" to listOf("特大", "XL", "extra large", "jumbo")
    )

    /**
     * Parses a natural language food order into a MenuItemRequest.
     *
     * Examples:
     * - "2 large pepperoni pizzas"
     * - "kung pao chicken, extra spicy"
     * - "cheeseburger without onions"
     *
     * @param request The natural language request
     * @return Parsed MenuItemRequest
     */
    fun parseMenuItemRequest(request: String): MenuItemRequest {
        val lowerRequest = request.lowercase()

        // Extract quantity
        val quantity = extractQuantity(lowerRequest)

        // Extract size
        val size = extractSize(lowerRequest)

        // Extract customizations
        val customizations = extractCustomizations(lowerRequest)

        // Extract exclusions
        val exclusions = extractExclusions(lowerRequest)

        // Extract item name (after removing quantity, size, customizations)
        val itemName = extractItemName(lowerRequest)

        Log.d(TAG, "Parsed menu request: item=$itemName, qty=$quantity, size=$size, customs=$customizations, exclude=$exclusions")

        return MenuItemRequest(
            itemName = itemName,
            quantity = quantity,
            size = size,
            customizations = customizations,
            excludeIngredients = exclusions
        )
    }

    /**
     * Generates VLM prompt hints for finding a menu item.
     *
     * @param request The menu item request
     * @param language Language code ("en" or "zh")
     * @return Hint text to include in VLM prompt
     */
    fun generateMenuSearchHint(request: MenuItemRequest, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("菜单搜索提示：\n")
                append("- 寻找的菜品：${request.itemName}\n")
                if (request.quantity > 1) {
                    append("- 数量：${request.quantity}\n")
                }
                request.size?.let { append("- 规格：$it\n") }
                if (request.customizations.isNotEmpty()) {
                    append("- 定制选项：${request.customizations.joinToString(", ")}\n")
                }
                if (request.excludeIngredients.isNotEmpty()) {
                    append("- 不要：${request.excludeIngredients.joinToString(", ")}\n")
                }
                append("请在菜单中找到该菜品并点击选择。如果有多个选项，选择最匹配的那个。")
            }
        } else {
            buildString {
                append("Menu search hints:\n")
                append("- Looking for: ${request.itemName}\n")
                if (request.quantity > 1) {
                    append("- Quantity: ${request.quantity}\n")
                }
                request.size?.let { append("- Size: $it\n") }
                if (request.customizations.isNotEmpty()) {
                    append("- Customizations: ${request.customizations.joinToString(", ")}\n")
                }
                if (request.excludeIngredients.isNotEmpty()) {
                    append("- Exclude: ${request.excludeIngredients.joinToString(", ")}\n")
                }
                append("Please find this item in the menu and tap to select it. If there are multiple options, choose the best match.")
            }
        }
    }

    /**
     * Gets synonyms for a food item to improve matching.
     *
     * @param itemName The item name to look up
     * @return List of synonyms including the original name
     */
    fun getSynonyms(itemName: String): List<String> {
        val lowerName = itemName.lowercase()
        val synonyms = mutableListOf(itemName)

        // Check if item matches any known synonym group
        for ((key, values) in foodSynonyms) {
            if (lowerName.contains(key) || values.any { lowerName.contains(it) }) {
                synonyms.add(key)
                synonyms.addAll(values)
            }
        }

        return synonyms.distinct()
    }

    /**
     * Calculates match score between requested item and menu item.
     *
     * @param request The menu item request
     * @param menuItem The menu item to match against
     * @return Match score from 0.0 to 1.0
     */
    fun calculateMatchScore(request: MenuItemRequest, menuItem: MenuItem): Float {
        val requestTerms = getSynonyms(request.itemName).map { it.lowercase() }
        val menuItemName = menuItem.name.lowercase()
        val menuItemDesc = menuItem.description?.lowercase() ?: ""

        var score = 0.0f

        // Exact name match
        if (requestTerms.any { menuItemName == it }) {
            score = 1.0f
        }
        // Name contains match
        else if (requestTerms.any { menuItemName.contains(it) || it.contains(menuItemName) }) {
            score = 0.8f
        }
        // Description contains match
        else if (requestTerms.any { menuItemDesc.contains(it) }) {
            score = 0.6f
        }
        // Partial word match
        else {
            val requestWords = request.itemName.lowercase().split(" ")
            val menuWords = menuItemName.split(" ")
            val matchingWords = requestWords.count { reqWord ->
                menuWords.any { menuWord -> menuWord.contains(reqWord) || reqWord.contains(menuWord) }
            }
            score = (matchingWords.toFloat() / requestWords.size.coerceAtLeast(1)) * 0.5f
        }

        // Bonus for size availability
        if (request.size != null && menuItem.sizes.isNotEmpty()) {
            if (menuItem.sizes.any { it.lowercase().contains(request.size.lowercase()) }) {
                score += 0.1f
            }
        }

        // Penalty for unavailable items
        if (!menuItem.isAvailable) {
            score *= 0.5f
        }

        return score.coerceIn(0.0f, 1.0f)
    }

    /**
     * Generates VLM prompt for adding item to cart.
     *
     * @param request The menu item request
     * @param language Language code
     * @return Prompt text for cart addition
     */
    fun generateAddToCartHint(request: MenuItemRequest, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("添加到购物车提示：\n")
                append("- 确保数量为：${request.quantity}\n")
                request.size?.let { append("- 选择规格：$it\n") }
                if (request.customizations.isNotEmpty()) {
                    append("- 添加定制：${request.customizations.joinToString(", ")}\n")
                }
                if (request.excludeIngredients.isNotEmpty()) {
                    append("- 取消勾选或移除：${request.excludeIngredients.joinToString(", ")}\n")
                }
                append("请点击'加入购物车'或'添加'按钮完成添加。")
            }
        } else {
            buildString {
                append("Add to cart hints:\n")
                append("- Ensure quantity is: ${request.quantity}\n")
                request.size?.let { append("- Select size: $it\n") }
                if (request.customizations.isNotEmpty()) {
                    append("- Add customizations: ${request.customizations.joinToString(", ")}\n")
                }
                if (request.excludeIngredients.isNotEmpty()) {
                    append("- Remove/uncheck: ${request.excludeIngredients.joinToString(", ")}\n")
                }
                append("Please tap 'Add to Cart' or 'Add' button to complete.")
            }
        }
    }

    // ==================== Private Helper Methods ====================

    private fun extractQuantity(request: String): Int {
        // Pattern: number at start or after "order"
        val patterns = listOf(
            Regex("""^(\d+)\s"""),
            Regex("""order\s+(\d+)\s"""),
            Regex("""get\s+(\d+)\s"""),
            Regex("""(\d+)\s*(?:pieces?|pcs?|份|个)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(request)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 1
            }
        }

        // Word numbers
        val wordNumbers = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "一" to 1, "两" to 2, "二" to 2, "三" to 3, "四" to 4, "五" to 5
        )

        for ((word, num) in wordNumbers) {
            if (request.contains(word)) {
                return num
            }
        }

        return 1
    }

    private fun extractSize(request: String): String? {
        for ((size, keywords) in sizeKeywords) {
            if (keywords.any { request.contains(it.lowercase()) }) {
                return size
            }
        }
        return null
    }

    private fun extractCustomizations(request: String): List<String> {
        val customizations = mutableListOf<String>()

        val patterns = listOf(
            Regex("""extra\s+(\w+)"""),
            Regex("""add\s+(\w+)"""),
            Regex("""with\s+extra\s+(\w+)"""),
            Regex("""加\s*(\w+)"""),
            Regex("""多加\s*(\w+)""")
        )

        for (pattern in patterns) {
            val matches = pattern.findAll(request)
            for (match in matches) {
                customizations.add(match.groupValues[1])
            }
        }

        // Spice level
        val spiceLevels = listOf(
            "mild" to "mild",
            "medium spicy" to "medium spicy",
            "spicy" to "spicy",
            "extra spicy" to "extra spicy",
            "微辣" to "mild",
            "中辣" to "medium spicy",
            "辣" to "spicy",
            "特辣" to "extra spicy"
        )

        for ((keyword, level) in spiceLevels) {
            if (request.contains(keyword)) {
                customizations.add(level)
                break
            }
        }

        return customizations.distinct()
    }

    private fun extractExclusions(request: String): List<String> {
        val exclusions = mutableListOf<String>()

        val patterns = listOf(
            Regex("""without\s+(\w+)"""),
            Regex("""no\s+(\w+)"""),
            Regex("""hold\s+the\s+(\w+)"""),
            Regex("""不要\s*(\w+)"""),
            Regex("""去\s*(\w+)""")
        )

        for (pattern in patterns) {
            val matches = pattern.findAll(request)
            for (match in matches) {
                exclusions.add(match.groupValues[1])
            }
        }

        return exclusions.distinct()
    }

    private fun extractItemName(request: String): String {
        var name = request

        // Remove quantity
        name = name.replace(Regex("""^\d+\s+"""), "")
        name = name.replace(Regex("""\d+\s*(?:pieces?|pcs?|份|个)"""), "")

        // Remove size keywords
        for (keywords in sizeKeywords.values) {
            for (keyword in keywords) {
                name = name.replace(Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE), "")
            }
        }

        // Remove customization phrases
        name = name.replace(Regex("""extra\s+\w+"""), "")
        name = name.replace(Regex("""add\s+\w+"""), "")
        name = name.replace(Regex("""with\s+extra\s+\w+"""), "")
        name = name.replace(Regex("""without\s+\w+"""), "")
        name = name.replace(Regex("""no\s+\w+"""), "")
        name = name.replace(Regex("""hold\s+the\s+\w+"""), "")

        // Remove order keywords
        name = name.replace(Regex("""^(?:order|get|buy|i want|i'd like|give me)\s+""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""^(?:点|买|要|来一?份?)\s*"""), "")

        // Clean up
        name = name.replace(Regex("""\s+"""), " ").trim()

        return name.ifEmpty { request }
    }
}
