package com.openautoglm.agent.agent

import android.util.Log
import com.openautoglm.agent.inference.ModelClient
import com.openautoglm.agent.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Detects out-of-stock conditions in e-commerce apps (T064).
 *
 * Uses VLM to analyze product page screenshots and identify
 * stock availability indicators.
 */
class StockChecker(
    private val modelClient: ModelClient
) {
    companion object {
        private const val TAG = "StockChecker"

        // Common out-of-stock indicators in different languages
        private val OUT_OF_STOCK_KEYWORDS = setOf(
            // Chinese
            "缺货", "无货", "售罄", "暂无库存", "已售完", "已抢光",
            "补货中", "暂时缺货", "库存不足", "已下架",
            // English
            "out of stock", "sold out", "unavailable", "not available",
            "temporarily unavailable", "coming soon", "notify me",
            "back in stock", "currently unavailable", "out-of-stock"
        )
    }

    /**
     * Stock status result.
     */
    data class StockStatus(
        val isAvailable: Boolean,
        val confidence: Float,
        val reason: String,
        val indicators: List<String>,
        val suggestedAction: SuggestedAction
    ) {
        enum class SuggestedAction {
            PROCEED,           // Item is available, continue purchase
            SKIP_ITEM,         // Item unavailable, skip to next
            REQUEST_NOTIFY,    // Set up stock notification
            FIND_ALTERNATIVE,  // Search for similar products
            ABORT_TASK         // Critical item unavailable, stop task
        }
    }

    /**
     * Check stock availability on current screen.
     *
     * @param screenshot Base64 encoded screenshot
     * @param productName Product name for context (optional)
     * @param language Language for analysis ("en" or "zh")
     * @return StockStatus indicating availability and recommended action
     */
    suspend fun checkStockAvailability(
        screenshot: String,
        productName: String? = null,
        language: String = "en"
    ): StockStatus = withContext(Dispatchers.IO) {
        try {
            val prompt = buildStockCheckPrompt(productName, language)

            val messages = listOf(
                ChatMessage(
                    role = "user",
                    content = prompt,
                    images = listOf(screenshot)
                )
            )

            val response = modelClient.generateChatCompletion(
                messages = messages,
                temperature = 0.1f,  // Low temperature for consistent detection
                maxTokens = 500
            )

            parseStockResponse(response.content, language)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking stock availability", e)
            // Default to assuming available if check fails
            StockStatus(
                isAvailable = true,
                confidence = 0.5f,
                reason = "Stock check failed: ${e.message}",
                indicators = emptyList(),
                suggestedAction = StockStatus.SuggestedAction.PROCEED
            )
        }
    }

    /**
     * Build prompt for stock availability check.
     */
    private fun buildStockCheckPrompt(productName: String?, language: String): String {
        val productContext = productName?.let { " for '$it'" } ?: ""

        return when (language) {
            "zh", "zh-CN", "zh-TW" -> """
                请分析这个商品页面的截图$productContext。

                判断商品是否有货：
                1. 查找缺货指示器（如"缺货"、"售罄"、"暂无库存"等文字）
                2. 检查购买按钮的状态（灰色/禁用状态可能表示缺货）
                3. 查看库存数量提示
                4. 注意"补货中"、"预售"等特殊状态

                以JSON格式回复：
                {
                    "available": true/false,
                    "confidence": 0.0-1.0,
                    "reason": "判断依据的简短说明",
                    "indicators": ["检测到的具体指示器列表"]
                }
            """.trimIndent()

            else -> """
                Analyze this product page screenshot$productContext.

                Determine if the product is in stock:
                1. Look for out-of-stock indicators (text like "out of stock", "sold out", etc.)
                2. Check the buy button status (grayed out/disabled may indicate unavailable)
                3. Look for stock quantity indicators
                4. Note special statuses like "pre-order", "coming soon"

                Respond in JSON format:
                {
                    "available": true/false,
                    "confidence": 0.0-1.0,
                    "reason": "Brief explanation of your determination",
                    "indicators": ["List of specific indicators found"]
                }
            """.trimIndent()
        }
    }

    /**
     * Parse VLM response for stock status.
     */
    private fun parseStockResponse(content: String, language: String): StockStatus {
        return try {
            // Try to extract JSON from response
            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}')

            if (jsonStart == -1 || jsonEnd == -1) {
                // Fallback to text analysis if no JSON
                return analyzeTextForStock(content, language)
            }

            val jsonStr = content.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)

            val isAvailable = json.optBoolean("available", true)
            val confidence = json.optDouble("confidence", 0.7).toFloat()
            val reason = json.optString("reason", "No reason provided")

            val indicators = mutableListOf<String>()
            val indicatorsArray = json.optJSONArray("indicators")
            if (indicatorsArray != null) {
                for (i in 0 until indicatorsArray.length()) {
                    indicators.add(indicatorsArray.getString(i))
                }
            }

            val suggestedAction = determineSuggestedAction(isAvailable, confidence)

            StockStatus(
                isAvailable = isAvailable,
                confidence = confidence,
                reason = reason,
                indicators = indicators,
                suggestedAction = suggestedAction
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON response, using text analysis", e)
            analyzeTextForStock(content, language)
        }
    }

    /**
     * Fallback text analysis when JSON parsing fails.
     */
    private fun analyzeTextForStock(content: String, language: String): StockStatus {
        val contentLower = content.lowercase()
        val foundIndicators = mutableListOf<String>()

        // Check for out-of-stock keywords
        for (keyword in OUT_OF_STOCK_KEYWORDS) {
            if (contentLower.contains(keyword.lowercase())) {
                foundIndicators.add(keyword)
            }
        }

        val isAvailable = foundIndicators.isEmpty()
        val confidence = if (foundIndicators.isNotEmpty()) 0.8f else 0.6f

        val reason = if (isAvailable) {
            when (language) {
                "zh" -> "未检测到缺货指示器"
                else -> "No out-of-stock indicators detected"
            }
        } else {
            when (language) {
                "zh" -> "检测到缺货指示器: ${foundIndicators.joinToString()}"
                else -> "Found stock indicators: ${foundIndicators.joinToString()}"
            }
        }

        return StockStatus(
            isAvailable = isAvailable,
            confidence = confidence,
            reason = reason,
            indicators = foundIndicators,
            suggestedAction = determineSuggestedAction(isAvailable, confidence)
        )
    }

    /**
     * Determine what action to suggest based on stock status.
     */
    private fun determineSuggestedAction(
        isAvailable: Boolean,
        confidence: Float
    ): StockStatus.SuggestedAction {
        return when {
            isAvailable && confidence > 0.7f -> StockStatus.SuggestedAction.PROCEED
            isAvailable && confidence <= 0.7f -> StockStatus.SuggestedAction.PROCEED  // Proceed with caution
            !isAvailable && confidence > 0.8f -> StockStatus.SuggestedAction.SKIP_ITEM
            !isAvailable && confidence in 0.6f..0.8f -> StockStatus.SuggestedAction.REQUEST_NOTIFY
            else -> StockStatus.SuggestedAction.FIND_ALTERNATIVE
        }
    }

    /**
     * Check multiple products and filter out unavailable ones.
     *
     * @param screenshots List of product page screenshots
     * @param productNames Optional list of product names (must match screenshot count)
     * @param language Language for analysis
     * @return List of indices that are in stock
     */
    suspend fun filterAvailableProducts(
        screenshots: List<String>,
        productNames: List<String>? = null,
        language: String = "en"
    ): List<Int> = withContext(Dispatchers.IO) {
        require(productNames == null || productNames.size == screenshots.size) {
            "Product names count must match screenshots count"
        }

        val availableIndices = mutableListOf<Int>()

        screenshots.forEachIndexed { index, screenshot ->
            val productName = productNames?.getOrNull(index)
            val status = checkStockAvailability(screenshot, productName, language)

            Log.d(TAG, "Product $index status: available=${status.isAvailable}, confidence=${status.confidence}")

            if (status.isAvailable && status.confidence > 0.5f) {
                availableIndices.add(index)
            }
        }

        availableIndices
    }

    /**
     * Generate user-friendly message for stock status.
     */
    fun getStatusMessage(status: StockStatus, language: String = "en"): String {
        return when (language) {
            "zh", "zh-CN", "zh-TW" -> when {
                status.isAvailable -> "商品有货 (置信度: ${(status.confidence * 100).toInt()}%)"
                else -> "商品缺货 - ${status.reason}"
            }
            else -> when {
                status.isAvailable -> "Product available (confidence: ${(status.confidence * 100).toInt()}%)"
                else -> "Product unavailable - ${status.reason}"
            }
        }
    }
}
