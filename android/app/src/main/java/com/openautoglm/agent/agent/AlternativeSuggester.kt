package com.openautoglm.agent.agent

import android.util.Log

/**
 * Helper class for suggesting alternatives when items or restaurants are unavailable.
 *
 * This class provides utilities for:
 * - Suggesting similar menu items
 * - Recommending alternative restaurants
 * - Generating VLM prompts for finding alternatives
 *
 * Works in conjunction with AvailabilityChecker to handle unavailability gracefully.
 */
object AlternativeSuggester {

    private const val TAG = "AlternativeSuggester"

    /**
     * Category of food items for alternative matching.
     */
    enum class FoodCategory {
        PIZZA,
        BURGER,
        CHICKEN,
        NOODLES,
        RICE,
        SUSHI,
        SALAD,
        DESSERT,
        DRINKS,
        OTHER
    }

    /**
     * Data class representing an alternative suggestion.
     */
    data class Alternative(
        val name: String,
        val reason: String,
        val similarity: Float,  // 0.0 to 1.0
        val category: FoodCategory = FoodCategory.OTHER
    ) {
        fun toDisplayString(language: String = "en"): String {
            return if (language == "zh") {
                "$name（$reason）"
            } else {
                "$name ($reason)"
            }
        }
    }

    /**
     * Food item category mappings.
     */
    private val categoryKeywords = mapOf(
        FoodCategory.PIZZA to listOf(
            "pizza", "披萨", "比萨", "margherita", "pepperoni"
        ),
        FoodCategory.BURGER to listOf(
            "burger", "汉堡", "hamburger", "cheeseburger"
        ),
        FoodCategory.CHICKEN to listOf(
            "chicken", "鸡", "炸鸡", "鸡翅", "wings", "nuggets"
        ),
        FoodCategory.NOODLES to listOf(
            "noodle", "面", "拉面", "pasta", "spaghetti", "ramen", "lo mein"
        ),
        FoodCategory.RICE to listOf(
            "rice", "饭", "炒饭", "盖饭", "fried rice", "biryani"
        ),
        FoodCategory.SUSHI to listOf(
            "sushi", "寿司", "maki", "roll", "sashimi"
        ),
        FoodCategory.SALAD to listOf(
            "salad", "沙拉", "greens", "caesar"
        ),
        FoodCategory.DESSERT to listOf(
            "dessert", "甜点", "cake", "蛋糕", "ice cream", "冰淇淋"
        ),
        FoodCategory.DRINKS to listOf(
            "drink", "饮料", "cola", "可乐", "tea", "茶", "coffee", "咖啡", "juice"
        )
    )

    /**
     * Similar items mapping for common foods.
     */
    private val similarItems = mapOf(
        // Pizza alternatives
        "pepperoni pizza" to listOf("cheese pizza", "meat lovers pizza", "supreme pizza"),
        "margherita" to listOf("cheese pizza", "tomato basil pizza", "veggie pizza"),

        // Burger alternatives
        "cheeseburger" to listOf("hamburger", "bacon burger", "double burger"),
        "chicken burger" to listOf("crispy chicken sandwich", "grilled chicken sandwich"),

        // Chinese food alternatives
        "kung pao chicken" to listOf("orange chicken", "general tso's chicken", "cashew chicken"),
        "fried rice" to listOf("chow mein", "lo mein", "steamed rice with dishes"),

        // Japanese food alternatives
        "salmon sushi" to listOf("tuna sushi", "california roll", "salmon sashimi"),
        "ramen" to listOf("udon", "soba", "pho")
    )

    /**
     * Determines the food category of an item.
     *
     * @param itemName The item name
     * @return The food category
     */
    fun categorizeItem(itemName: String): FoodCategory {
        val lowerName = itemName.lowercase()

        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { lowerName.contains(it) }) {
                return category
            }
        }

        return FoodCategory.OTHER
    }

    /**
     * Gets alternatives for an unavailable item.
     *
     * @param itemName The unavailable item name
     * @return List of alternative suggestions
     */
    fun getAlternatives(itemName: String): List<Alternative> {
        val alternatives = mutableListOf<Alternative>()
        val lowerName = itemName.lowercase()
        val category = categorizeItem(itemName)

        // Check for direct similar items
        for ((key, values) in similarItems) {
            if (lowerName.contains(key) || key.contains(lowerName)) {
                values.forEach { alt ->
                    alternatives.add(Alternative(
                        name = alt,
                        reason = "Similar to $itemName",
                        similarity = 0.8f,
                        category = category
                    ))
                }
                break
            }
        }

        // Add category-based suggestions if no direct matches
        if (alternatives.isEmpty()) {
            alternatives.addAll(getCategoryAlternatives(category))
        }

        Log.d(TAG, "Found ${alternatives.size} alternatives for '$itemName'")
        return alternatives.take(5)
    }

    /**
     * Gets alternatives based on food category.
     *
     * @param category The food category
     * @return List of alternative suggestions
     */
    private fun getCategoryAlternatives(category: FoodCategory): List<Alternative> {
        return when (category) {
            FoodCategory.PIZZA -> listOf(
                Alternative("Cheese Pizza", "Classic option", 0.7f, category),
                Alternative("Pepperoni Pizza", "Popular choice", 0.7f, category),
                Alternative("Veggie Pizza", "Vegetarian option", 0.6f, category)
            )
            FoodCategory.BURGER -> listOf(
                Alternative("Classic Burger", "Traditional choice", 0.7f, category),
                Alternative("Cheeseburger", "Popular option", 0.7f, category),
                Alternative("Chicken Sandwich", "Lighter alternative", 0.6f, category)
            )
            FoodCategory.CHICKEN -> listOf(
                Alternative("Fried Chicken", "Classic preparation", 0.7f, category),
                Alternative("Grilled Chicken", "Healthier option", 0.6f, category),
                Alternative("Chicken Wings", "Popular choice", 0.7f, category)
            )
            FoodCategory.NOODLES -> listOf(
                Alternative("Lo Mein", "Soft noodles", 0.7f, category),
                Alternative("Chow Mein", "Crispy noodles", 0.7f, category),
                Alternative("Pad Thai", "Thai style", 0.6f, category)
            )
            FoodCategory.RICE -> listOf(
                Alternative("Fried Rice", "Classic choice", 0.7f, category),
                Alternative("Steamed Rice", "Plain option", 0.6f, category),
                Alternative("Rice Bowl", "Complete meal", 0.7f, category)
            )
            FoodCategory.SUSHI -> listOf(
                Alternative("California Roll", "Popular choice", 0.7f, category),
                Alternative("Salmon Roll", "Fresh option", 0.7f, category),
                Alternative("Veggie Roll", "Vegetarian option", 0.6f, category)
            )
            FoodCategory.SALAD -> listOf(
                Alternative("Garden Salad", "Fresh greens", 0.7f, category),
                Alternative("Caesar Salad", "Classic choice", 0.7f, category),
                Alternative("Greek Salad", "Mediterranean option", 0.6f, category)
            )
            FoodCategory.DESSERT -> listOf(
                Alternative("Ice Cream", "Classic dessert", 0.7f, category),
                Alternative("Cheesecake", "Rich option", 0.6f, category),
                Alternative("Brownie", "Chocolate choice", 0.6f, category)
            )
            FoodCategory.DRINKS -> listOf(
                Alternative("Cola", "Classic soft drink", 0.7f, category),
                Alternative("Iced Tea", "Refreshing option", 0.6f, category),
                Alternative("Lemonade", "Fresh choice", 0.6f, category)
            )
            FoodCategory.OTHER -> listOf(
                Alternative("Chef's Special", "Recommended dish", 0.5f, category),
                Alternative("Popular Item", "Bestseller", 0.5f, category)
            )
        }
    }

    /**
     * Generates VLM prompt for finding alternatives.
     *
     * @param unavailableItem The item that is unavailable
     * @param alternatives List of suggested alternatives
     * @param language Language code
     * @return Prompt text for finding alternatives
     */
    fun generateFindAlternativeHint(
        unavailableItem: String,
        alternatives: List<Alternative>,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("寻找替代品提示：\n")
                append("'$unavailableItem' 不可用。\n\n")
                append("建议的替代品：\n")
                alternatives.forEachIndexed { index, alt ->
                    append("${index + 1}. ${alt.toDisplayString("zh")}\n")
                }
                append("\n请在菜单中查找以上替代品，或其他类似商品。\n")
                append("找到后点击选择最合适的选项。")
            }
        } else {
            buildString {
                append("Finding alternatives hint:\n")
                append("'$unavailableItem' is unavailable.\n\n")
                append("Suggested alternatives:\n")
                alternatives.forEachIndexed { index, alt ->
                    append("${index + 1}. ${alt.toDisplayString("en")}\n")
                }
                append("\nPlease look for these alternatives in the menu, or similar items.\n")
                append("Select the most suitable option when found.")
            }
        }
    }

    /**
     * Generates VLM prompt for finding alternative restaurants.
     *
     * @param unavailableRestaurant The restaurant that is unavailable
     * @param cuisineType The type of cuisine (optional)
     * @param language Language code
     * @return Prompt text for finding alternative restaurants
     */
    fun generateFindRestaurantHint(
        unavailableRestaurant: String,
        cuisineType: String? = null,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("寻找替代餐厅提示：\n")
                append("'$unavailableRestaurant' 不可用。\n\n")
                append("请寻找：\n")
                cuisineType?.let { append("- 提供 $it 的餐厅\n") }
                append("- 评分较高的餐厅\n")
                append("- 配送时间较短的餐厅\n")
                append("- 配送费用合理的餐厅\n\n")
                append("请返回搜索结果页面，选择一家类似的可用餐厅。")
            }
        } else {
            buildString {
                append("Finding alternative restaurant hint:\n")
                append("'$unavailableRestaurant' is unavailable.\n\n")
                append("Look for:\n")
                cuisineType?.let { append("- Restaurants serving $it\n") }
                append("- Highly rated restaurants\n")
                append("- Quick delivery options\n")
                append("- Reasonable delivery fees\n\n")
                append("Please go back to search results and select a similar available restaurant.")
            }
        }
    }

    /**
     * Generates user-friendly message about alternatives.
     *
     * @param unavailableItem The unavailable item
     * @param alternatives List of alternatives
     * @param language Language code
     * @return User-friendly message
     */
    fun generateUserMessage(
        unavailableItem: String,
        alternatives: List<Alternative>,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("'$unavailableItem' 已售罄。")
                if (alternatives.isNotEmpty()) {
                    append("\n\n我找到了以下替代品：\n")
                    alternatives.take(3).forEach { alt ->
                        append("• ${alt.name}\n")
                    }
                    append("\n您要选择其中一个吗？")
                } else {
                    append("\n\n我会尝试找一个类似的商品。")
                }
            }
        } else {
            buildString {
                append("'$unavailableItem' is sold out.")
                if (alternatives.isNotEmpty()) {
                    append("\n\nI found these alternatives:\n")
                    alternatives.take(3).forEach { alt ->
                        append("• ${alt.name}\n")
                    }
                    append("\nWould you like one of these instead?")
                } else {
                    append("\n\nI'll try to find something similar.")
                }
            }
        }
    }

    /**
     * Logs alternative suggestion for debugging.
     */
    fun logSuggestion(itemName: String, alternatives: List<Alternative>) {
        Log.d(TAG, "Alternatives for '$itemName': ${alternatives.map { it.name }}")
    }
}
