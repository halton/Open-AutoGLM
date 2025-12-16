package com.openautoglm.agent.agent

import android.util.Log
import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.knowledge.DefaultAppMappings

/**
 * Helper class for finding and selecting restaurants in food delivery apps.
 *
 * This class provides utilities for:
 * - Identifying food delivery apps
 * - Generating search prompts for restaurants
 * - Parsing restaurant information from VLM responses
 *
 * Works in conjunction with the VLM agent to navigate food delivery app UIs.
 */
object RestaurantFinder {

    private const val TAG = "RestaurantFinder"

    /**
     * Data class representing a restaurant search query.
     */
    data class RestaurantQuery(
        val restaurantName: String?,
        val cuisineType: String?,
        val foodItem: String?,
        val deliveryApp: String?,
        val location: String? = null
    ) {
        /**
         * Generates a search term for the food delivery app.
         */
        fun toSearchTerm(): String {
            return when {
                !restaurantName.isNullOrBlank() -> restaurantName
                !foodItem.isNullOrBlank() -> foodItem
                !cuisineType.isNullOrBlank() -> cuisineType
                else -> ""
            }
        }

        /**
         * Checks if this query has enough information to search.
         */
        fun isValid(): Boolean {
            return !restaurantName.isNullOrBlank() ||
                   !foodItem.isNullOrBlank() ||
                   !cuisineType.isNullOrBlank()
        }
    }

    /**
     * Data class representing a found restaurant.
     */
    data class Restaurant(
        val name: String,
        val rating: Float? = null,
        val deliveryTime: String? = null,
        val deliveryFee: String? = null,
        val minOrder: String? = null,
        val distance: String? = null,
        val isOpen: Boolean = true,
        val cuisineType: String? = null
    )

    /**
     * Gets the list of supported food delivery apps.
     *
     * @return List of package names for food delivery apps
     */
    fun getSupportedDeliveryApps(): List<String> {
        return DefaultAppMappings.getByCategory(AppCategory.FOOD)
            .map { it.packageName }
    }

    /**
     * Gets the preferred delivery app based on availability.
     *
     * @param installedApps List of installed app package names
     * @return The package name of the preferred delivery app, or null if none installed
     */
    fun getPreferredDeliveryApp(installedApps: List<String>): String? {
        // Priority order: Meituan > Eleme > Uber Eats > DoorDash > Grubhub
        val priorityOrder = listOf(
            "com.sankuai.meituan",
            "me.ele",
            "com.ubercab.eats",
            "com.dd.doordash",
            "com.grubhub.android"
        )

        return priorityOrder.firstOrNull { it in installedApps }
    }

    /**
     * Parses a natural language food order request into a RestaurantQuery.
     *
     * @param request The natural language request (e.g., "Order pizza from Domino's on Uber Eats")
     * @return Parsed RestaurantQuery
     */
    fun parseOrderRequest(request: String): RestaurantQuery {
        val lowerRequest = request.lowercase()

        // Extract delivery app
        val deliveryApp = extractDeliveryApp(lowerRequest)

        // Extract restaurant name (after "from" keyword)
        val restaurantName = extractRestaurantName(lowerRequest)

        // Extract food item (after "order" keyword)
        val foodItem = extractFoodItem(lowerRequest)

        // Extract cuisine type
        val cuisineType = extractCuisineType(lowerRequest)

        Log.d(TAG, "Parsed order request: restaurant=$restaurantName, food=$foodItem, cuisine=$cuisineType, app=$deliveryApp")

        return RestaurantQuery(
            restaurantName = restaurantName,
            cuisineType = cuisineType,
            foodItem = foodItem,
            deliveryApp = deliveryApp
        )
    }

    /**
     * Generates VLM prompt hints for restaurant search.
     *
     * @param query The restaurant query
     * @param language Language code ("en" or "zh")
     * @return Hint text to include in VLM prompt
     */
    fun generateSearchHint(query: RestaurantQuery, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("搜索餐厅提示：\n")
                query.restaurantName?.let { append("- 餐厅名称：$it\n") }
                query.foodItem?.let { append("- 想要点的食物：$it\n") }
                query.cuisineType?.let { append("- 菜系类型：$it\n") }
                append("请在搜索框中输入相关关键词，然后在搜索结果中选择合适的餐厅。")
            }
        } else {
            buildString {
                append("Restaurant search hints:\n")
                query.restaurantName?.let { append("- Restaurant name: $it\n") }
                query.foodItem?.let { append("- Food item wanted: $it\n") }
                query.cuisineType?.let { append("- Cuisine type: $it\n") }
                append("Please enter relevant keywords in the search box, then select an appropriate restaurant from the results.")
            }
        }
    }

    /**
     * Checks if a given app is a food delivery app.
     *
     * @param packageName The package name to check
     * @return true if it's a food delivery app
     */
    fun isFoodDeliveryApp(packageName: String): Boolean {
        return packageName in getSupportedDeliveryApps()
    }

    /**
     * Gets the app name from package name.
     *
     * @param packageName The package name
     * @return The app name, or the package name if not found
     */
    fun getAppName(packageName: String): String {
        return DefaultAppMappings.getByPackageName(packageName)?.appName ?: packageName
    }

    // Private helper methods

    private fun extractDeliveryApp(request: String): String? {
        val appKeywords = mapOf(
            "meituan" to "com.sankuai.meituan",
            "美团" to "com.sankuai.meituan",
            "eleme" to "me.ele",
            "饿了么" to "me.ele",
            "uber eats" to "com.ubercab.eats",
            "ubereats" to "com.ubercab.eats",
            "doordash" to "com.dd.doordash",
            "grubhub" to "com.grubhub.android"
        )

        for ((keyword, packageName) in appKeywords) {
            if (request.contains(keyword)) {
                return packageName
            }
        }
        return null
    }

    private fun extractRestaurantName(request: String): String? {
        // Pattern: "from [restaurant]" or "at [restaurant]"
        val fromPattern = Regex("""(?:from|at|在)\s+([^on\s]+(?:\s+[^on\s]+)*)(?:\s+on|\s+用|\s*$)""", RegexOption.IGNORE_CASE)
        val match = fromPattern.find(request)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractFoodItem(request: String): String? {
        // Pattern: "order [food]" or "点 [food]" or "买 [food]"
        val orderPatterns = listOf(
            Regex("""(?:order|get|buy|点|买|要)\s+(?:some\s+)?([^from]+?)(?:\s+from|\s+at|\s+on|\s+在|\s*$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in orderPatterns) {
            val match = pattern.find(request)
            val result = match?.groupValues?.get(1)?.trim()
            if (!result.isNullOrBlank() && result.length > 1) {
                return result
            }
        }
        return null
    }

    private fun extractCuisineType(request: String): String? {
        val cuisineKeywords = mapOf(
            "chinese" to "中餐",
            "中餐" to "中餐",
            "japanese" to "日本料理",
            "日料" to "日本料理",
            "korean" to "韩国料理",
            "韩餐" to "韩国料理",
            "italian" to "意大利菜",
            "意大利" to "意大利菜",
            "mexican" to "墨西哥菜",
            "indian" to "印度菜",
            "thai" to "泰国菜",
            "泰餐" to "泰国菜",
            "pizza" to "披萨",
            "披萨" to "披萨",
            "burger" to "汉堡",
            "汉堡" to "汉堡",
            "sushi" to "寿司",
            "寿司" to "寿司",
            "noodle" to "面条",
            "面" to "面条",
            "bbq" to "烧烤",
            "烧烤" to "烧烤",
            "hotpot" to "火锅",
            "火锅" to "火锅"
        )

        for ((keyword, cuisine) in cuisineKeywords) {
            if (request.contains(keyword)) {
                return cuisine
            }
        }
        return null
    }
}
