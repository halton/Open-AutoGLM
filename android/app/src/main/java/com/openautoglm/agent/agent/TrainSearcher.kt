package com.openautoglm.agent.agent

import android.util.Log
import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.knowledge.DefaultAppMappings

/**
 * Helper class for searching and comparing train tickets.
 *
 * This class provides utilities for:
 * - Parsing train booking requests from natural language
 * - Generating VLM prompts for train search
 * - Managing train comparison data
 * - Identifying travel booking apps
 *
 * Works in conjunction with the VLM agent to navigate train booking apps.
 */
object TrainSearcher {

    private const val TAG = "TrainSearcher"

    /**
     * Data class representing a train search query.
     */
    data class TrainQuery(
        val origin: String,
        val destination: String,
        val departureDate: TravelDateParser.TravelDateTime? = null,
        val returnDate: TravelDateParser.TravelDateTime? = null,
        val timePreference: TravelDateParser.TimePreference? = null,
        val trainType: TrainType? = null,
        val seatClass: SeatClass? = null,
        val passengerCount: Int = 1,
        val preferredApp: String? = null
    ) {
        val isRoundTrip: Boolean get() = returnDate != null

        fun toSearchSummary(language: String = "en"): String {
            return if (language == "zh") {
                buildString {
                    append("$origin → $destination")
                    departureDate?.let { append("，${it.toDisplayString("zh")}") }
                    trainType?.let { append("，${it.toDisplayString("zh")}") }
                    if (passengerCount > 1) append("，${passengerCount}人")
                }
            } else {
                buildString {
                    append("$origin to $destination")
                    departureDate?.let { append(", ${it.toDisplayString("en")}") }
                    trainType?.let { append(", ${it.toDisplayString("en")}") }
                    if (passengerCount > 1) append(", $passengerCount passengers")
                }
            }
        }
    }

    /**
     * Train types available in China.
     */
    enum class TrainType {
        HIGH_SPEED,     // G (高铁)
        INTERCITY,      // C (城际)
        EXPRESS,        // D (动车)
        FAST,           // Z/T/K (直达/特快/快速)
        REGULAR,        // Other
        ANY;

        fun toDisplayString(language: String = "en"): String {
            return if (language == "zh") {
                when (this) {
                    HIGH_SPEED -> "高铁 (G)"
                    INTERCITY -> "城际 (C)"
                    EXPRESS -> "动车 (D)"
                    FAST -> "直达/特快 (Z/T/K)"
                    REGULAR -> "普通列车"
                    ANY -> "所有车次"
                }
            } else {
                when (this) {
                    HIGH_SPEED -> "High-speed (G)"
                    INTERCITY -> "Intercity (C)"
                    EXPRESS -> "Express (D)"
                    FAST -> "Fast train (Z/T/K)"
                    REGULAR -> "Regular train"
                    ANY -> "All trains"
                }
            }
        }

        fun getTrainCodes(): List<String> {
            return when (this) {
                HIGH_SPEED -> listOf("G")
                INTERCITY -> listOf("C")
                EXPRESS -> listOf("D")
                FAST -> listOf("Z", "T", "K")
                REGULAR -> listOf("L", "Y", "S")
                ANY -> emptyList()
            }
        }
    }

    /**
     * Seat classes for trains.
     */
    enum class SeatClass {
        BUSINESS,       // 商务座
        FIRST_CLASS,    // 一等座
        SECOND_CLASS,   // 二等座
        SOFT_SLEEPER,   // 软卧
        HARD_SLEEPER,   // 硬卧
        SOFT_SEAT,      // 软座
        HARD_SEAT,      // 硬座
        STANDING,       // 无座
        ANY;

        fun toDisplayString(language: String = "en"): String {
            return if (language == "zh") {
                when (this) {
                    BUSINESS -> "商务座"
                    FIRST_CLASS -> "一等座"
                    SECOND_CLASS -> "二等座"
                    SOFT_SLEEPER -> "软卧"
                    HARD_SLEEPER -> "硬卧"
                    SOFT_SEAT -> "软座"
                    HARD_SEAT -> "硬座"
                    STANDING -> "无座"
                    ANY -> "任意席位"
                }
            } else {
                when (this) {
                    BUSINESS -> "Business Class"
                    FIRST_CLASS -> "First Class"
                    SECOND_CLASS -> "Second Class"
                    SOFT_SLEEPER -> "Soft Sleeper"
                    HARD_SLEEPER -> "Hard Sleeper"
                    SOFT_SEAT -> "Soft Seat"
                    HARD_SEAT -> "Hard Seat"
                    STANDING -> "Standing"
                    ANY -> "Any class"
                }
            }
        }
    }

    /**
     * Data class representing a train result.
     */
    data class TrainResult(
        val trainNumber: String,
        val trainType: TrainType,
        val departureStation: String,
        val arrivalStation: String,
        val departureTime: String,
        val arrivalTime: String,
        val duration: String,
        val availableSeats: Map<SeatClass, SeatAvailability> = emptyMap(),
        val isBookable: Boolean = true
    ) {
        fun toDisplayString(language: String = "en"): String {
            return if (language == "zh") {
                "$trainNumber | $departureTime→$arrivalTime | $duration"
            } else {
                "$trainNumber | $departureTime→$arrivalTime | $duration"
            }
        }
    }

    /**
     * Seat availability info.
     */
    data class SeatAvailability(
        val seatClass: SeatClass,
        val price: String,
        val available: Int,  // -1 for unknown, 0 for sold out
        val status: String = ""
    ) {
        val isAvailable: Boolean get() = available != 0

        fun toDisplayString(language: String = "en"): String {
            val availStr = when {
                available < 0 -> ""
                available == 0 -> if (language == "zh") "无票" else "Sold out"
                available < 10 -> if (language == "zh") "${available}张" else "$available left"
                else -> if (language == "zh") "有票" else "Available"
            }
            return "${seatClass.toDisplayString(language)} $price $availStr".trim()
        }
    }

    /**
     * Gets the list of supported train booking apps.
     *
     * @return List of package names for travel apps
     */
    fun getSupportedTravelApps(): List<String> {
        return DefaultAppMappings.getByCategory(AppCategory.TRAVEL)
            .map { it.packageName }
    }

    /**
     * Gets the preferred train booking app based on availability.
     *
     * @param installedApps List of installed app package names
     * @param region Region preference ("cn" for China, "intl" for international)
     * @return The package name of the preferred app, or null if none installed
     */
    fun getPreferredTravelApp(installedApps: List<String>, region: String = "cn"): String? {
        val priorityOrder = if (region == "cn") {
            listOf(
                "com.MobileTicket",      // 12306
                "ctrip.android.view",    // Ctrip
                "com.Qunar"              // Qunar
            )
        } else {
            listOf(
                "com.ctrip.ibu.trip",    // Trip.com
                "com.trainline.android", // Trainline
                "com.amtrak.rider"       // Amtrak
            )
        }

        return priorityOrder.firstOrNull { it in installedApps }
    }

    /**
     * Parses a natural language train booking request.
     *
     * Examples:
     * - "Book a train from Beijing to Shanghai tomorrow morning"
     * - "订一张后天从北京到上海的高铁票"
     *
     * @param request The natural language request
     * @return Parsed TrainQuery
     */
    fun parseTrainRequest(request: String): TrainQuery {
        val lowerRequest = request.lowercase()

        // Extract origin and destination
        val (origin, destination) = extractCities(request)

        // Extract date
        val departureDate = TravelDateParser.parse(request)

        // Extract time preference
        val timePreference = TravelDateParser.parseTimePreference(request)

        // Extract train type
        val trainType = extractTrainType(request)

        // Extract seat class
        val seatClass = extractSeatClass(request)

        // Extract passenger count
        val passengerCount = extractPassengerCount(request)

        Log.d(TAG, "Parsed train request: $origin → $destination, date=$departureDate, type=$trainType")

        return TrainQuery(
            origin = origin ?: "",
            destination = destination ?: "",
            departureDate = departureDate,
            timePreference = timePreference,
            trainType = trainType,
            seatClass = seatClass,
            passengerCount = passengerCount
        )
    }

    /**
     * Generates VLM prompt for train search.
     *
     * @param query The train query
     * @param language Language code
     * @return Prompt text for train search
     */
    fun generateSearchHint(query: TrainQuery, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("火车票搜索提示：\n")
                append("- 出发地：${query.origin}\n")
                append("- 目的地：${query.destination}\n")
                query.departureDate?.let { append("- 出发日期：${it.toDisplayString("zh")}\n") }
                query.timePreference?.let { append("- 时间偏好：${it.toDisplayString("zh")}\n") }
                query.trainType?.let { append("- 车次类型：${it.toDisplayString("zh")}\n") }
                query.seatClass?.let { append("- 座位等级：${it.toDisplayString("zh")}\n") }
                if (query.passengerCount > 1) {
                    append("- 乘客数量：${query.passengerCount}人\n")
                }
                append("\n操作步骤：\n")
                append("1. 在出发地输入框输入：${query.origin}\n")
                append("2. 在目的地输入框输入：${query.destination}\n")
                append("3. 选择出发日期\n")
                append("4. 点击搜索/查询按钮\n")
                append("5. 在结果中选择合适的车次")
            }
        } else {
            buildString {
                append("Train search hints:\n")
                append("- From: ${query.origin}\n")
                append("- To: ${query.destination}\n")
                query.departureDate?.let { append("- Date: ${it.toDisplayString("en")}\n") }
                query.timePreference?.let { append("- Time preference: ${it.toDisplayString("en")}\n") }
                query.trainType?.let { append("- Train type: ${it.toDisplayString("en")}\n") }
                query.seatClass?.let { append("- Seat class: ${it.toDisplayString("en")}\n") }
                if (query.passengerCount > 1) {
                    append("- Passengers: ${query.passengerCount}\n")
                }
                append("\nSteps:\n")
                append("1. Enter origin: ${query.origin}\n")
                append("2. Enter destination: ${query.destination}\n")
                append("3. Select departure date\n")
                append("4. Tap Search button\n")
                append("5. Select a suitable train from results")
            }
        }
    }

    /**
     * Generates VLM prompt for selecting a train from results.
     *
     * @param query The original query
     * @param language Language code
     * @return Prompt text for train selection
     */
    fun generateSelectionHint(query: TrainQuery, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("选择车次提示：\n")
                append("请选择符合以下条件的车次：\n")
                query.timePreference?.let {
                    val (start, end) = it.getTimeRange()
                    append("- 出发时间在 ${start}:00 到 ${end}:00 之间\n")
                }
                query.trainType?.let { type ->
                    if (type != TrainType.ANY) {
                        append("- 优先选择 ${type.toDisplayString("zh")} 车次\n")
                    }
                }
                query.seatClass?.let { seat ->
                    if (seat != SeatClass.ANY) {
                        append("- 确保 ${seat.toDisplayString("zh")} 有票\n")
                    }
                }
                append("\n点击选择合适的车次，然后选择座位等级进行预订。")
            }
        } else {
            buildString {
                append("Train selection hints:\n")
                append("Please select a train matching these criteria:\n")
                query.timePreference?.let {
                    val (start, end) = it.getTimeRange()
                    append("- Departure time between ${start}:00 and ${end}:00\n")
                }
                query.trainType?.let { type ->
                    if (type != TrainType.ANY) {
                        append("- Prefer ${type.toDisplayString("en")} trains\n")
                    }
                }
                query.seatClass?.let { seat ->
                    if (seat != SeatClass.ANY) {
                        append("- Ensure ${seat.toDisplayString("en")} is available\n")
                    }
                }
                append("\nTap to select a suitable train, then choose seat class to book.")
            }
        }
    }

    // ==================== Private Helper Methods ====================

    private fun extractCities(request: String): Pair<String?, String?> {
        // Pattern: "from [origin] to [destination]"
        val fromToPattern = Regex(
            """(?:from|从)\s+(\S+(?:\s+\S+)?)\s+(?:to|到)\s+(\S+(?:\s+\S+)?)""",
            RegexOption.IGNORE_CASE
        )
        val fromToMatch = fromToPattern.find(request)
        if (fromToMatch != null) {
            return Pair(
                fromToMatch.groupValues[1].trim(),
                fromToMatch.groupValues[2].trim()
            )
        }

        // Pattern: "[origin] to [destination]"
        val simplePattern = Regex(
            """(\S+(?:\s+\S+)?)\s+(?:to|→|->|至|到)\s+(\S+(?:\s+\S+)?)""",
            RegexOption.IGNORE_CASE
        )
        val simpleMatch = simplePattern.find(request)
        if (simpleMatch != null) {
            return Pair(
                simpleMatch.groupValues[1].trim(),
                simpleMatch.groupValues[2].trim()
            )
        }

        return Pair(null, null)
    }

    private fun extractTrainType(request: String): TrainType? {
        val lowerRequest = request.lowercase()

        val typeKeywords = mapOf(
            "high speed" to TrainType.HIGH_SPEED,
            "high-speed" to TrainType.HIGH_SPEED,
            "bullet train" to TrainType.HIGH_SPEED,
            "高铁" to TrainType.HIGH_SPEED,
            "g train" to TrainType.HIGH_SPEED,
            "intercity" to TrainType.INTERCITY,
            "城际" to TrainType.INTERCITY,
            "c train" to TrainType.INTERCITY,
            "express" to TrainType.EXPRESS,
            "动车" to TrainType.EXPRESS,
            "d train" to TrainType.EXPRESS,
            "fast train" to TrainType.FAST,
            "直达" to TrainType.FAST,
            "特快" to TrainType.FAST
        )

        for ((keyword, type) in typeKeywords) {
            if (lowerRequest.contains(keyword) || request.contains(keyword)) {
                return type
            }
        }

        return null
    }

    private fun extractSeatClass(request: String): SeatClass? {
        val lowerRequest = request.lowercase()

        val seatKeywords = mapOf(
            "business" to SeatClass.BUSINESS,
            "商务" to SeatClass.BUSINESS,
            "first class" to SeatClass.FIRST_CLASS,
            "一等" to SeatClass.FIRST_CLASS,
            "second class" to SeatClass.SECOND_CLASS,
            "二等" to SeatClass.SECOND_CLASS,
            "soft sleeper" to SeatClass.SOFT_SLEEPER,
            "软卧" to SeatClass.SOFT_SLEEPER,
            "hard sleeper" to SeatClass.HARD_SLEEPER,
            "硬卧" to SeatClass.HARD_SLEEPER,
            "soft seat" to SeatClass.SOFT_SEAT,
            "软座" to SeatClass.SOFT_SEAT,
            "hard seat" to SeatClass.HARD_SEAT,
            "硬座" to SeatClass.HARD_SEAT
        )

        for ((keyword, seatClass) in seatKeywords) {
            if (lowerRequest.contains(keyword) || request.contains(keyword)) {
                return seatClass
            }
        }

        return null
    }

    private fun extractPassengerCount(request: String): Int {
        // Pattern: "X tickets" or "X人" or "X passengers"
        val patterns = listOf(
            Regex("""(\d+)\s*(?:tickets?|张票?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*人"""),
            Regex("""(\d+)\s*passengers?""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(request)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 1
            }
        }

        return 1
    }
}
