package com.openautoglm.agent.agent

import android.util.Log
import java.util.*

/**
 * Helper class for suggesting alternative dates when trains are unavailable.
 *
 * This class provides utilities for:
 * - Detecting date availability issues
 * - Suggesting alternative dates
 * - Generating VLM prompts for date changes
 *
 * Works in conjunction with TrainSearcher for handling unavailable dates.
 */
object DateAlternativeSuggester {

    private const val TAG = "DateAlternativeSuggester"

    /**
     * Reason for suggesting an alternative date.
     */
    enum class UnavailabilityReason {
        SOLD_OUT,           // All trains sold out
        NO_TRAINS,          // No trains on this date
        TOO_EARLY,          // Date is before booking opens
        TOO_LATE,           // Date is past
        MAINTENANCE,        // Railway maintenance
        HOLIDAY_RUSH,       // High demand period
        UNKNOWN
    }

    /**
     * Data class representing an alternative date suggestion.
     */
    data class DateAlternative(
        val date: Calendar,
        val reason: String,
        val daysDiff: Int,  // Positive = future, negative = past
        val expectedAvailability: String = "Good"
    ) {
        fun toDisplayString(language: String = "en"): String {
            val dateParser = TravelDateParser
            val travelDate = TravelDateParser.TravelDateTime(date = date)

            val diffStr = when {
                daysDiff == 0 -> if (language == "zh") "今天" else "Today"
                daysDiff == 1 -> if (language == "zh") "明天" else "Tomorrow"
                daysDiff == -1 -> if (language == "zh") "昨天" else "Yesterday"
                daysDiff > 0 -> if (language == "zh") "${daysDiff}天后" else "In $daysDiff days"
                else -> if (language == "zh") "${-daysDiff}天前" else "$daysDiff days ago"
            }

            return "${travelDate.toDisplayString(language)} ($diffStr)"
        }
    }

    /**
     * Gets alternative date suggestions when the requested date is unavailable.
     *
     * @param originalDate The originally requested date
     * @param reason Reason for unavailability
     * @param maxSuggestions Maximum number of suggestions
     * @return List of alternative date suggestions
     */
    fun getAlternatives(
        originalDate: Calendar,
        reason: UnavailabilityReason = UnavailabilityReason.SOLD_OUT,
        maxSuggestions: Int = 5
    ): List<DateAlternative> {
        val alternatives = mutableListOf<DateAlternative>()
        val today = Calendar.getInstance()

        // Don't suggest dates in the past
        val minDate = if (originalDate.before(today)) today else originalDate

        // Suggest dates around the original date
        val offsets = when (reason) {
            UnavailabilityReason.SOLD_OUT,
            UnavailabilityReason.HOLIDAY_RUSH -> {
                // Try nearby dates first
                listOf(-1, 1, -2, 2, -3, 3, 7, -7)
            }
            UnavailabilityReason.NO_TRAINS,
            UnavailabilityReason.MAINTENANCE -> {
                // Try dates further out
                listOf(1, 2, 3, 7, 14)
            }
            UnavailabilityReason.TOO_EARLY -> {
                // Only suggest later dates
                listOf(1, 2, 3, 7, 14, 21)
            }
            UnavailabilityReason.TOO_LATE -> {
                // This date is past, suggest from today
                val daysUntilNow = daysBetween(originalDate, today)
                listOf(daysUntilNow, daysUntilNow + 1, daysUntilNow + 2)
            }
            else -> listOf(-1, 1, -2, 2, 3)
        }

        for (offset in offsets) {
            if (alternatives.size >= maxSuggestions) break

            val altDate = originalDate.clone() as Calendar
            altDate.add(Calendar.DAY_OF_MONTH, offset)

            // Skip past dates
            if (altDate.before(today)) continue

            // Skip if too far in the future (60 days max for most systems)
            val daysFromToday = daysBetween(today, altDate)
            if (daysFromToday > 60) continue

            alternatives.add(DateAlternative(
                date = altDate,
                reason = getReasonDescription(offset, reason),
                daysDiff = offset,
                expectedAvailability = estimateAvailability(altDate, reason)
            ))
        }

        Log.d(TAG, "Generated ${alternatives.size} alternative dates for ${reason.name}")
        return alternatives
    }

    /**
     * Detects unavailability reason from screen text.
     *
     * @param screenText Text content from the screen
     * @return Detected UnavailabilityReason
     */
    fun detectUnavailabilityReason(screenText: String): UnavailabilityReason {
        val lowerText = screenText.lowercase()

        val indicators = mapOf(
            UnavailabilityReason.SOLD_OUT to listOf(
                "sold out", "no tickets", "no seats", "not available",
                "无票", "售罄", "已售完", "暂无余票"
            ),
            UnavailabilityReason.NO_TRAINS to listOf(
                "no trains", "no service", "not running",
                "无车次", "暂无列车", "未开通"
            ),
            UnavailabilityReason.TOO_EARLY to listOf(
                "not yet", "booking opens", "advance booking",
                "暂未开售", "预售期", "尚未开始"
            ),
            UnavailabilityReason.MAINTENANCE to listOf(
                "maintenance", "suspended", "temporary closure",
                "维护", "停运", "暂停服务"
            ),
            UnavailabilityReason.HOLIDAY_RUSH to listOf(
                "high demand", "peak season", "holiday",
                "客流高峰", "节假日", "春运"
            )
        )

        for ((reason, keywords) in indicators) {
            if (keywords.any { lowerText.contains(it) }) {
                return reason
            }
        }

        return UnavailabilityReason.UNKNOWN
    }

    /**
     * Generates VLM prompt for handling unavailable dates.
     *
     * @param originalDate The originally requested date
     * @param alternatives List of alternative suggestions
     * @param reason Unavailability reason
     * @param language Language code
     * @return Prompt text for handling unavailability
     */
    fun generateAlternativeDateHint(
        originalDate: TravelDateParser.TravelDateTime,
        alternatives: List<DateAlternative>,
        reason: UnavailabilityReason,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("日期不可用提示：\n")
                append("原日期：${originalDate.toDisplayString("zh")}\n")
                append("原因：${getReasonDisplayString(reason, "zh")}\n\n")

                if (alternatives.isEmpty()) {
                    append("暂无可用的替代日期建议。\n")
                    append("请尝试更改出发地或目的地。")
                } else {
                    append("建议的替代日期：\n")
                    alternatives.forEachIndexed { index, alt ->
                        append("${index + 1}. ${alt.toDisplayString("zh")}\n")
                        append("   预计票量：${alt.expectedAvailability}\n")
                    }
                    append("\n请选择一个替代日期，或返回修改搜索条件。")
                }
            }
        } else {
            buildString {
                append("Date unavailable:\n")
                append("Original date: ${originalDate.toDisplayString("en")}\n")
                append("Reason: ${getReasonDisplayString(reason, "en")}\n\n")

                if (alternatives.isEmpty()) {
                    append("No alternative dates available.\n")
                    append("Please try changing origin or destination.")
                } else {
                    append("Suggested alternatives:\n")
                    alternatives.forEachIndexed { index, alt ->
                        append("${index + 1}. ${alt.toDisplayString("en")}\n")
                        append("   Expected availability: ${alt.expectedAvailability}\n")
                    }
                    append("\nPlease select an alternative date, or go back to modify search.")
                }
            }
        }
    }

    /**
     * Generates user-friendly message about date changes.
     *
     * @param originalDate Original requested date
     * @param newDate New selected date
     * @param language Language code
     * @return User message
     */
    fun generateDateChangeMessage(
        originalDate: TravelDateParser.TravelDateTime,
        newDate: TravelDateParser.TravelDateTime,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            "原日期 ${originalDate.toDisplayString("zh")} 无票，已改为 ${newDate.toDisplayString("zh")}"
        } else {
            "Original date ${originalDate.toDisplayString("en")} unavailable, changed to ${newDate.toDisplayString("en")}"
        }
    }

    /**
     * Checks if a date is during a known high-demand period.
     *
     * @param date The date to check
     * @return true if it's a high-demand period
     */
    fun isHighDemandPeriod(date: Calendar): Boolean {
        val month = date.get(Calendar.MONTH)
        val day = date.get(Calendar.DAY_OF_MONTH)

        // Chinese New Year period (late Jan - early Feb)
        if ((month == Calendar.JANUARY && day >= 20) ||
            (month == Calendar.FEBRUARY && day <= 20)) {
            return true
        }

        // National Day (Oct 1-7)
        if (month == Calendar.OCTOBER && day <= 10) {
            return true
        }

        // Labor Day (May 1-5)
        if (month == Calendar.MAY && day <= 7) {
            return true
        }

        // Dragon Boat Festival, Mid-Autumn (varies)
        // For simplicity, check weekend proximity
        val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.SUNDAY) {
            return true
        }

        return false
    }

    // ==================== Private Helper Methods ====================

    private fun daysBetween(start: Calendar, end: Calendar): Int {
        val startTime = start.clone() as Calendar
        startTime.set(Calendar.HOUR_OF_DAY, 0)
        startTime.set(Calendar.MINUTE, 0)
        startTime.set(Calendar.SECOND, 0)
        startTime.set(Calendar.MILLISECOND, 0)

        val endTime = end.clone() as Calendar
        endTime.set(Calendar.HOUR_OF_DAY, 0)
        endTime.set(Calendar.MINUTE, 0)
        endTime.set(Calendar.SECOND, 0)
        endTime.set(Calendar.MILLISECOND, 0)

        val diffMs = endTime.timeInMillis - startTime.timeInMillis
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun getReasonDescription(dayOffset: Int, reason: UnavailabilityReason): String {
        return when {
            dayOffset == 0 -> "Same day"
            dayOffset == 1 -> "Next day option"
            dayOffset == -1 -> "Previous day option"
            dayOffset > 0 -> "Later option"
            else -> "Earlier option"
        }
    }

    private fun estimateAvailability(date: Calendar, originalReason: UnavailabilityReason): String {
        return if (isHighDemandPeriod(date)) {
            "Limited"
        } else {
            val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                "Moderate"
            } else {
                "Good"
            }
        }
    }

    private fun getReasonDisplayString(reason: UnavailabilityReason, language: String): String {
        return if (language == "zh") {
            when (reason) {
                UnavailabilityReason.SOLD_OUT -> "车票已售罄"
                UnavailabilityReason.NO_TRAINS -> "当日无列车运行"
                UnavailabilityReason.TOO_EARLY -> "车票尚未开售"
                UnavailabilityReason.TOO_LATE -> "日期已过"
                UnavailabilityReason.MAINTENANCE -> "线路维护中"
                UnavailabilityReason.HOLIDAY_RUSH -> "节假日客流高峰"
                UnavailabilityReason.UNKNOWN -> "暂时不可用"
            }
        } else {
            when (reason) {
                UnavailabilityReason.SOLD_OUT -> "Tickets sold out"
                UnavailabilityReason.NO_TRAINS -> "No trains running"
                UnavailabilityReason.TOO_EARLY -> "Tickets not yet on sale"
                UnavailabilityReason.TOO_LATE -> "Date has passed"
                UnavailabilityReason.MAINTENANCE -> "Railway maintenance"
                UnavailabilityReason.HOLIDAY_RUSH -> "Holiday rush period"
                UnavailabilityReason.UNKNOWN -> "Temporarily unavailable"
            }
        }
    }
}
