package com.openautoglm.agent.agent

import android.util.Log

/**
 * Helper class for checking availability of restaurants and menu items.
 *
 * This class provides utilities for:
 * - Detecting unavailability indicators (sold out, closed, etc.)
 * - Generating VLM prompts for availability verification
 * - Parsing availability status from screen content
 *
 * Works in conjunction with the VLM agent to handle unavailable items gracefully.
 */
object AvailabilityChecker {

    private const val TAG = "AvailabilityChecker"

    /**
     * Availability status for restaurants or items.
     */
    enum class AvailabilityStatus {
        AVAILABLE,
        UNAVAILABLE,
        LIMITED,        // Low stock or limited time
        CLOSED,         // Restaurant closed
        OUT_OF_RANGE,   // Outside delivery area
        UNKNOWN
    }

    /**
     * Result of an availability check.
     */
    data class AvailabilityResult(
        val status: AvailabilityStatus,
        val reason: String? = null,
        val alternativeAvailable: Boolean = false,
        val reopenTime: String? = null
    ) {
        val isAvailable: Boolean get() = status == AvailabilityStatus.AVAILABLE

        fun toUserMessage(language: String = "en"): String {
            return if (language == "zh") {
                when (status) {
                    AvailabilityStatus.AVAILABLE -> "商品可用"
                    AvailabilityStatus.UNAVAILABLE -> reason ?: "商品已售罄"
                    AvailabilityStatus.LIMITED -> reason ?: "库存有限，请尽快下单"
                    AvailabilityStatus.CLOSED -> {
                        val msg = "餐厅已打烊"
                        if (reopenTime != null) "$msg，营业时间：$reopenTime" else msg
                    }
                    AvailabilityStatus.OUT_OF_RANGE -> "超出配送范围"
                    AvailabilityStatus.UNKNOWN -> "无法确认可用性"
                }
            } else {
                when (status) {
                    AvailabilityStatus.AVAILABLE -> "Item is available"
                    AvailabilityStatus.UNAVAILABLE -> reason ?: "Item is sold out"
                    AvailabilityStatus.LIMITED -> reason ?: "Limited stock, order soon"
                    AvailabilityStatus.CLOSED -> {
                        val msg = "Restaurant is closed"
                        if (reopenTime != null) "$msg, opens at $reopenTime" else msg
                    }
                    AvailabilityStatus.OUT_OF_RANGE -> "Outside delivery area"
                    AvailabilityStatus.UNKNOWN -> "Unable to verify availability"
                }
            }
        }
    }

    /**
     * Common unavailability indicators in different languages.
     */
    object UnavailabilityIndicators {
        val soldOut = listOf(
            // English
            "sold out", "out of stock", "unavailable", "not available",
            "currently unavailable", "temporarily unavailable",
            // Chinese
            "售罄", "已售完", "卖完了", "暂时缺货", "无货", "已售罄",
            "暂不可订", "暂停销售", "补货中"
        )

        val closed = listOf(
            // English
            "closed", "not accepting orders", "currently closed",
            "opens at", "reopens", "closed now",
            // Chinese
            "休息中", "已打烊", "暂停营业", "未营业", "歇业",
            "营业时间", "暂不接单", "明日开始"
        )

        val limitedStock = listOf(
            // English
            "limited", "few left", "only", "last", "hurry",
            "selling fast", "limited time",
            // Chinese
            "仅剩", "还剩", "最后", "限量", "即将售罄",
            "限时", "快抢"
        )

        val outOfRange = listOf(
            // English
            "out of range", "too far", "doesn't deliver",
            "not in delivery area", "outside delivery zone",
            // Chinese
            "超出配送范围", "不在配送区域", "配送范围外",
            "距离太远", "暂不支持配送"
        )
    }

    /**
     * Checks availability based on screen text content.
     *
     * @param screenText Text extracted from the screen
     * @return AvailabilityResult with status and details
     */
    fun checkFromScreenText(screenText: String): AvailabilityResult {
        val lowerText = screenText.lowercase()

        // Check for closed restaurant
        for (indicator in UnavailabilityIndicators.closed) {
            if (lowerText.contains(indicator.lowercase())) {
                val reopenTime = extractReopenTime(screenText)
                Log.d(TAG, "Detected closed status: $indicator")
                return AvailabilityResult(
                    status = AvailabilityStatus.CLOSED,
                    reason = "Restaurant is currently closed",
                    reopenTime = reopenTime
                )
            }
        }

        // Check for out of delivery range
        for (indicator in UnavailabilityIndicators.outOfRange) {
            if (lowerText.contains(indicator.lowercase())) {
                Log.d(TAG, "Detected out of range: $indicator")
                return AvailabilityResult(
                    status = AvailabilityStatus.OUT_OF_RANGE,
                    reason = "Outside delivery area"
                )
            }
        }

        // Check for sold out
        for (indicator in UnavailabilityIndicators.soldOut) {
            if (lowerText.contains(indicator.lowercase())) {
                Log.d(TAG, "Detected sold out: $indicator")
                return AvailabilityResult(
                    status = AvailabilityStatus.UNAVAILABLE,
                    reason = "Item is sold out",
                    alternativeAvailable = true
                )
            }
        }

        // Check for limited stock
        for (indicator in UnavailabilityIndicators.limitedStock) {
            if (lowerText.contains(indicator.lowercase())) {
                Log.d(TAG, "Detected limited stock: $indicator")
                return AvailabilityResult(
                    status = AvailabilityStatus.LIMITED,
                    reason = "Limited stock available"
                )
            }
        }

        // No unavailability indicators found
        return AvailabilityResult(status = AvailabilityStatus.AVAILABLE)
    }

    /**
     * Generates VLM prompt for checking item availability.
     *
     * @param itemName The item to check
     * @param language Language code
     * @return Prompt text for availability check
     */
    fun generateAvailabilityCheckHint(itemName: String, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("检查商品可用性：$itemName\n")
                append("请查看以下指示：\n")
                append("- 是否显示'售罄'、'暂不可订'等标签\n")
                append("- 商品是否灰显或有删除线\n")
                append("- '加入购物车'按钮是否可点击\n")
                append("- 是否有库存数量提示\n")
                append("如果商品可用，请继续添加到购物车。\n")
                append("如果商品不可用，请寻找类似替代品。")
            }
        } else {
            buildString {
                append("Check availability of: $itemName\n")
                append("Look for these indicators:\n")
                append("- 'Sold out', 'Unavailable' labels\n")
                append("- Grayed out item or strikethrough\n")
                append("- 'Add to Cart' button is disabled\n")
                append("- Stock count warnings\n")
                append("If available, proceed to add to cart.\n")
                append("If unavailable, look for similar alternatives.")
            }
        }
    }

    /**
     * Generates VLM prompt for checking restaurant availability.
     *
     * @param restaurantName The restaurant to check
     * @param language Language code
     * @return Prompt text for restaurant availability check
     */
    fun generateRestaurantCheckHint(restaurantName: String, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("检查餐厅状态：$restaurantName\n")
                append("请查看以下指示：\n")
                append("- 餐厅是否显示'休息中'或'已打烊'\n")
                append("- 是否显示营业时间\n")
                append("- 是否在配送范围内\n")
                append("- 配送时间和配送费\n")
                append("如果餐厅可用，请浏览菜单。\n")
                append("如果餐厅不可用，请返回寻找其他餐厅。")
            }
        } else {
            buildString {
                append("Check restaurant status: $restaurantName\n")
                append("Look for these indicators:\n")
                append("- 'Closed', 'Not accepting orders' status\n")
                append("- Operating hours display\n")
                append("- Delivery area coverage\n")
                append("- Delivery time and fee\n")
                append("If restaurant is available, browse the menu.\n")
                append("If unavailable, go back and find another restaurant.")
            }
        }
    }

    /**
     * Generates VLM prompt for handling unavailable items.
     *
     * @param result The availability check result
     * @param language Language code
     * @return Prompt text for handling unavailability
     */
    fun generateUnavailableItemHint(result: AvailabilityResult, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("处理不可用商品：\n")
                append("状态：${result.toUserMessage("zh")}\n\n")
                when (result.status) {
                    AvailabilityStatus.UNAVAILABLE -> {
                        append("建议操作：\n")
                        append("1. 在同一餐厅寻找类似商品\n")
                        append("2. 或返回上一页选择其他餐厅\n")
                        append("请查找并选择一个可用的替代品。")
                    }
                    AvailabilityStatus.CLOSED -> {
                        append("建议操作：\n")
                        append("1. 返回上一页\n")
                        append("2. 选择一家正在营业的餐厅\n")
                        append("请返回并寻找其他餐厅。")
                    }
                    AvailabilityStatus.OUT_OF_RANGE -> {
                        append("建议操作：\n")
                        append("1. 检查或更改配送地址\n")
                        append("2. 或选择在配送范围内的餐厅\n")
                        append("请返回并选择其他餐厅。")
                    }
                    else -> {
                        append("请尝试其他选项。")
                    }
                }
            }
        } else {
            buildString {
                append("Handling unavailable item:\n")
                append("Status: ${result.toUserMessage("en")}\n\n")
                when (result.status) {
                    AvailabilityStatus.UNAVAILABLE -> {
                        append("Suggested actions:\n")
                        append("1. Look for similar items at the same restaurant\n")
                        append("2. Or go back to select another restaurant\n")
                        append("Please find and select an available alternative.")
                    }
                    AvailabilityStatus.CLOSED -> {
                        append("Suggested actions:\n")
                        append("1. Go back to previous page\n")
                        append("2. Select a restaurant that is currently open\n")
                        append("Please go back and find another restaurant.")
                    }
                    AvailabilityStatus.OUT_OF_RANGE -> {
                        append("Suggested actions:\n")
                        append("1. Check or change delivery address\n")
                        append("2. Or select a restaurant within delivery range\n")
                        append("Please go back and select another restaurant.")
                    }
                    else -> {
                        append("Please try another option.")
                    }
                }
            }
        }
    }

    /**
     * Checks if a button or element appears to be disabled.
     *
     * @param elementText Text or description of the element
     * @return true if the element appears disabled
     */
    fun isElementDisabled(elementText: String): Boolean {
        val lowerText = elementText.lowercase()
        val disabledIndicators = listOf(
            "disabled", "grayed", "unavailable", "can't",
            "不可用", "已禁用", "灰色"
        )
        return disabledIndicators.any { lowerText.contains(it) }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts reopening time from screen text.
     */
    private fun extractReopenTime(screenText: String): String? {
        // Pattern: "opens at HH:MM" or "营业时间 HH:MM"
        val patterns = listOf(
            Regex("""opens?\s+(?:at\s+)?(\d{1,2}:\d{2}(?:\s*[AaPp][Mm])?)""", RegexOption.IGNORE_CASE),
            Regex("""reopens?\s+(?:at\s+)?(\d{1,2}:\d{2}(?:\s*[AaPp][Mm])?)""", RegexOption.IGNORE_CASE),
            Regex("""营业时间[：:]\s*(\d{1,2}:\d{2})"""),
            Regex("""(\d{1,2}:\d{2})\s*(?:开始营业|开门)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(screenText)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    /**
     * Logs availability check for debugging.
     */
    fun logCheck(itemName: String, result: AvailabilityResult) {
        Log.d(TAG, "Availability check for '$itemName': ${result.status} - ${result.reason ?: "OK"}")
    }
}
