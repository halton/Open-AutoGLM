package com.openautoglm.agent.agent

import android.content.Context
import android.util.Log
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.data.entities.Shipment
import com.openautoglm.agent.data.entities.ShipmentStatus
import com.openautoglm.agent.data.entities.TrackingEvent
import com.openautoglm.agent.inference.InferenceRouterImpl
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ContentPart
import java.util.UUID

/**
 * Extracts shipment data from shopping app screens using VLM.
 *
 * Uses vision-language model to:
 * - Parse order list screens for shipment information
 * - Extract tracking details from order detail pages
 * - Recognize shipping status and estimated delivery dates
 *
 * @param context Application context
 * @param inferenceRouter Router for VLM inference
 */
class ShipmentExtractor(
    private val context: Context,
    private val inferenceRouter: InferenceRouterImpl
) {
    companion object {
        private const val TAG = "ShipmentExtractor"

        // Extraction prompt templates
        private val SHIPMENT_LIST_PROMPT_ZH = """
请分析这个购物应用的订单/物流页面截图，提取所有包裹的物流信息。

对于每个包裹，请提取：
1. 快递单号（如有）
2. 物流状态（待发货/已发货/运输中/派送中/已签收）
3. 快递公司名称
4. 商品描述/订单摘要
5. 预计送达时间（如有）
6. 最新物流位置（如有）

请以JSON数组格式输出，每个包裹一个对象：
[
  {
    "trackingNumber": "快递单号",
    "status": "状态",
    "carrier": "快递公司",
    "description": "商品描述",
    "estimatedDelivery": "预计送达",
    "lastLocation": "最新位置"
  }
]

如果页面没有物流信息，返回空数组 []
        """.trimIndent()

        private val SHIPMENT_LIST_PROMPT_EN = """
Please analyze this shopping app's order/logistics page screenshot and extract shipping information for all packages.

For each package, extract:
1. Tracking number (if available)
2. Shipping status (pending/shipped/in transit/out for delivery/delivered)
3. Carrier name
4. Item description/order summary
5. Estimated delivery date (if available)
6. Latest location (if available)

Output in JSON array format, one object per package:
[
  {
    "trackingNumber": "tracking number",
    "status": "status",
    "carrier": "carrier name",
    "description": "item description",
    "estimatedDelivery": "estimated delivery",
    "lastLocation": "latest location"
  }
]

If no shipping information is found, return empty array []
        """.trimIndent()

        private val SHIPMENT_DETAIL_PROMPT_ZH = """
请分析这个物流详情页面截图，提取详细的物流跟踪信息。

请提取：
1. 快递单号
2. 快递公司
3. 当前状态
4. 物流轨迹（时间、地点、描述）
5. 预计送达时间
6. 收件人信息（如有）
7. 发件人信息（如有）

请以JSON格式输出：
{
  "trackingNumber": "快递单号",
  "carrier": "快递公司",
  "status": "当前状态",
  "estimatedDelivery": "预计送达",
  "recipient": "收件人信息",
  "sender": "发件人信息",
  "trackingHistory": [
    {"time": "时间", "location": "地点", "description": "描述"}
  ]
}
        """.trimIndent()

        private val SHIPMENT_DETAIL_PROMPT_EN = """
Please analyze this logistics detail page screenshot and extract detailed tracking information.

Extract:
1. Tracking number
2. Carrier
3. Current status
4. Tracking history (time, location, description)
5. Estimated delivery time
6. Recipient information (if available)
7. Sender information (if available)

Output in JSON format:
{
  "trackingNumber": "tracking number",
  "carrier": "carrier name",
  "status": "current status",
  "estimatedDelivery": "estimated delivery",
  "recipient": "recipient info",
  "sender": "sender info",
  "trackingHistory": [
    {"time": "time", "location": "location", "description": "description"}
  ]
}
        """.trimIndent()

        // Status mapping
        private val STATUS_MAP_ZH = mapOf(
            "待发货" to ShipmentStatus.PENDING,
            "已发货" to ShipmentStatus.PICKED_UP,
            "运输中" to ShipmentStatus.IN_TRANSIT,
            "配送中" to ShipmentStatus.OUT_FOR_DELIVERY,
            "派送中" to ShipmentStatus.OUT_FOR_DELIVERY,
            "已签收" to ShipmentStatus.DELIVERED,
            "已送达" to ShipmentStatus.DELIVERED,
            "已取消" to ShipmentStatus.CANCELLED,
            "已退回" to ShipmentStatus.RETURNED
        )

        private val STATUS_MAP_EN = mapOf(
            "pending" to ShipmentStatus.PENDING,
            "shipped" to ShipmentStatus.PICKED_UP,
            "in transit" to ShipmentStatus.IN_TRANSIT,
            "out for delivery" to ShipmentStatus.OUT_FOR_DELIVERY,
            "delivered" to ShipmentStatus.DELIVERED,
            "cancelled" to ShipmentStatus.CANCELLED,
            "returned" to ShipmentStatus.RETURNED
        )
    }

    /**
     * Result of extracting shipment update.
     */
    data class ShipmentUpdateResult(
        val status: ShipmentStatus,
        val statusMessage: String?,
        val lastLocation: String?,
        val trackingHistory: List<TrackingEvent>?,
        val estimatedDeliveryDate: Long?
    )

    /**
     * Extracts all shipments from an app's order/logistics page.
     *
     * @param appPackage The package name of the app
     * @return List of extracted shipments
     */
    suspend fun extractAllShipments(appPackage: String): List<Shipment> {
        Log.i(TAG, "Extracting shipments from $appPackage")

        // TODO: Integrate with PhoneAgent to:
        // 1. Launch the app
        // 2. Navigate to orders/logistics page
        // 3. Capture screenshot
        // 4. Parse with VLM

        // For now, return empty list - actual implementation requires
        // integration with the agent loop
        return emptyList()
    }

    /**
     * Extracts update for a specific shipment.
     *
     * @param appPackage The source app package
     * @param trackingNumber The tracking number to look up
     * @param orderId Optional order ID for reference
     * @return Updated shipment data or null
     */
    suspend fun extractShipmentUpdate(
        appPackage: String,
        trackingNumber: String,
        orderId: String?
    ): ShipmentUpdateResult? {
        Log.i(TAG, "Extracting update for tracking: $trackingNumber")

        // TODO: Integrate with PhoneAgent to:
        // 1. Launch the app
        // 2. Search for the tracking number or order
        // 3. Navigate to detail page
        // 4. Capture screenshot
        // 5. Parse with VLM

        return null
    }

    /**
     * Parses shipment list from a screenshot using VLM.
     *
     * @param screenshotBase64 Base64-encoded screenshot
     * @param appPackage The app package name
     * @param language Preferred language (zh/en)
     * @return List of parsed shipments
     */
    suspend fun parseShipmentListScreen(
        screenshotBase64: String,
        appPackage: String,
        language: String = "zh"
    ): List<Shipment> {
        Log.d(TAG, "Parsing shipment list screen")

        val prompt = if (language == "zh") SHIPMENT_LIST_PROMPT_ZH else SHIPMENT_LIST_PROMPT_EN

        val messages = listOf(
            ChatMessage.system(getSystemPrompt(language)),
            ChatMessage.user(
                listOf(
                    ContentPart.imageBase64(screenshotBase64, "image/png"),
                    ContentPart.text(prompt)
                )
            )
        )

        val response = inferenceRouter.route(messages, InferenceMode.CLOUD)

        return parseShipmentListResponse(response.action, appPackage)
    }

    /**
     * Parses shipment detail from a screenshot using VLM.
     *
     * @param screenshotBase64 Base64-encoded screenshot
     * @param language Preferred language (zh/en)
     * @return Parsed shipment update result
     */
    suspend fun parseShipmentDetailScreen(
        screenshotBase64: String,
        language: String = "zh"
    ): ShipmentUpdateResult? {
        Log.d(TAG, "Parsing shipment detail screen")

        val prompt = if (language == "zh") SHIPMENT_DETAIL_PROMPT_ZH else SHIPMENT_DETAIL_PROMPT_EN

        val messages = listOf(
            ChatMessage.system(getSystemPrompt(language)),
            ChatMessage.user(
                listOf(
                    ContentPart.imageBase64(screenshotBase64, "image/png"),
                    ContentPart.text(prompt)
                )
            )
        )

        val response = inferenceRouter.route(messages, InferenceMode.CLOUD)

        return parseShipmentDetailResponse(response.action)
    }

    /**
     * Parses the VLM response for shipment list.
     */
    private fun parseShipmentListResponse(response: String, appPackage: String): List<Shipment> {
        val shipments = mutableListOf<Shipment>()

        try {
            // Extract JSON array from response
            val jsonMatch = Regex("\\[.*]", RegexOption.DOT_MATCHES_ALL).find(response)
            val jsonStr = jsonMatch?.value ?: return emptyList()

            // Parse JSON using simple regex extraction (avoiding external JSON library complexity)
            val objectPattern = Regex("\\{[^{}]*}")
            val objects = objectPattern.findAll(jsonStr)

            for (obj in objects) {
                val shipment = parseShipmentObject(obj.value, appPackage)
                if (shipment != null) {
                    shipments.add(shipment)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse shipment list: ${e.message}")
        }

        return shipments
    }

    /**
     * Parses a single shipment JSON object.
     */
    private fun parseShipmentObject(jsonObj: String, appPackage: String): Shipment? {
        try {
            val trackingNumber = extractJsonField(jsonObj, "trackingNumber") ?: return null
            val statusStr = extractJsonField(jsonObj, "status") ?: "unknown"
            val carrier = extractJsonField(jsonObj, "carrier") ?: "未知"
            val description = extractJsonField(jsonObj, "description") ?: "商品"
            val estimatedDelivery = extractJsonField(jsonObj, "estimatedDelivery")
            val lastLocation = extractJsonField(jsonObj, "lastLocation")

            val status = parseStatus(statusStr)
            val appName = LogisticsTracker.SUPPORTED_APPS[appPackage] ?: appPackage

            return Shipment(
                id = UUID.randomUUID(),
                trackingNumber = trackingNumber,
                carrier = carrier,
                sourceApp = appName,
                sourceAppPackage = appPackage,
                itemDescription = description,
                status = status,
                lastLocation = lastLocation,
                statusMessage = statusStr
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse shipment object: ${e.message}")
            return null
        }
    }

    /**
     * Parses the VLM response for shipment detail.
     */
    private fun parseShipmentDetailResponse(response: String): ShipmentUpdateResult? {
        try {
            val statusStr = extractJsonField(response, "status") ?: return null
            val lastLocation = extractJsonField(response, "lastLocation")
            val estimatedDelivery = extractJsonField(response, "estimatedDelivery")

            val status = parseStatus(statusStr)

            // Parse tracking history
            val history = parseTrackingHistory(response)

            return ShipmentUpdateResult(
                status = status,
                statusMessage = statusStr,
                lastLocation = lastLocation,
                trackingHistory = history,
                estimatedDeliveryDate = null // Would need date parsing
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse shipment detail: ${e.message}")
            return null
        }
    }

    /**
     * Parses tracking history from response.
     */
    private fun parseTrackingHistory(response: String): List<TrackingEvent> {
        val events = mutableListOf<TrackingEvent>()

        try {
            val historyPattern = Regex("\"trackingHistory\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            val historyMatch = historyPattern.find(response) ?: return events

            val eventPattern = Regex("\\{[^{}]*}")
            val eventMatches = eventPattern.findAll(historyMatch.groupValues[1])

            for (eventMatch in eventMatches) {
                val time = extractJsonField(eventMatch.value, "time")
                val location = extractJsonField(eventMatch.value, "location")
                val description = extractJsonField(eventMatch.value, "description") ?: continue

                events.add(
                    TrackingEvent(
                        timestamp = System.currentTimeMillis(), // Would need actual timestamp parsing
                        location = location,
                        description = description,
                        status = ""
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tracking history: ${e.message}")
        }

        return events
    }

    /**
     * Extracts a field value from a JSON-like string.
     */
    private fun extractJsonField(json: String, field: String): String? {
        val pattern = Regex("\"$field\"\\s*:\\s*\"([^\"]*?)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    /**
     * Parses status string to ShipmentStatus enum.
     */
    private fun parseStatus(statusStr: String): ShipmentStatus {
        val normalized = statusStr.lowercase().trim()

        // Try Chinese mapping
        for ((key, value) in STATUS_MAP_ZH) {
            if (normalized.contains(key)) {
                return value
            }
        }

        // Try English mapping
        for ((key, value) in STATUS_MAP_EN) {
            if (normalized.contains(key)) {
                return value
            }
        }

        return ShipmentStatus.UNKNOWN
    }

    private fun getSystemPrompt(language: String): String {
        return if (language == "zh") {
            "你是一个专业的物流信息提取助手，擅长从购物应用截图中识别和提取包裹物流信息。请严格按照要求的JSON格式输出。"
        } else {
            "You are a professional logistics information extraction assistant, skilled at recognizing and extracting package shipping information from shopping app screenshots. Please output strictly in the required JSON format."
        }
    }
}
