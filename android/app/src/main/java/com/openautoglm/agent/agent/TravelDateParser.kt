package com.openautoglm.agent.agent

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for parsing travel dates and times from natural language.
 *
 * This class provides utilities for:
 * - Parsing dates from various formats (tomorrow, next Friday, 12/25, etc.)
 * - Parsing times from natural language (morning, 9am, afternoon)
 * - Handling relative date expressions
 * - Supporting both English and Chinese date formats
 *
 * Works in conjunction with the VLM agent to understand travel booking requests.
 */
object TravelDateParser {

    private const val TAG = "TravelDateParser"

    /**
     * Represents a parsed travel date/time.
     */
    data class TravelDateTime(
        val date: Calendar,
        val hasSpecificTime: Boolean = false,
        val timePreference: TimePreference? = null,
        val isFlexible: Boolean = false,
        val originalText: String = ""
    ) {
        fun toDateString(format: String = "yyyy-MM-dd"): String {
            return SimpleDateFormat(format, Locale.getDefault()).format(date.time)
        }

        fun toDisplayString(language: String = "en"): String {
            val dateFormat = if (language == "zh") {
                SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)
            } else {
                SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
            }

            val dateStr = dateFormat.format(date.time)

            return if (hasSpecificTime) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                "$dateStr ${timeFormat.format(date.time)}"
            } else {
                timePreference?.let { "$dateStr (${it.toDisplayString(language)})" } ?: dateStr
            }
        }
    }

    /**
     * Time of day preference.
     */
    enum class TimePreference {
        EARLY_MORNING,  // 5:00 - 8:00
        MORNING,        // 8:00 - 12:00
        AFTERNOON,      // 12:00 - 17:00
        EVENING,        // 17:00 - 21:00
        NIGHT,          // 21:00 - 24:00
        ANY;

        fun toDisplayString(language: String = "en"): String {
            return if (language == "zh") {
                when (this) {
                    EARLY_MORNING -> "清晨"
                    MORNING -> "上午"
                    AFTERNOON -> "下午"
                    EVENING -> "傍晚"
                    NIGHT -> "晚上"
                    ANY -> "任意时间"
                }
            } else {
                when (this) {
                    EARLY_MORNING -> "early morning"
                    MORNING -> "morning"
                    AFTERNOON -> "afternoon"
                    EVENING -> "evening"
                    NIGHT -> "night"
                    ANY -> "any time"
                }
            }
        }

        fun getTimeRange(): Pair<Int, Int> {
            return when (this) {
                EARLY_MORNING -> Pair(5, 8)
                MORNING -> Pair(8, 12)
                AFTERNOON -> Pair(12, 17)
                EVENING -> Pair(17, 21)
                NIGHT -> Pair(21, 24)
                ANY -> Pair(0, 24)
            }
        }
    }

    /**
     * Parses a natural language date/time expression.
     *
     * Supports formats:
     * - Relative: "today", "tomorrow", "next week", "in 3 days"
     * - Named days: "Monday", "this Friday", "next Sunday"
     * - Dates: "December 25", "12/25", "2024-01-15"
     * - Chinese: "明天", "下周一", "12月25日"
     *
     * @param input The natural language date expression
     * @return Parsed TravelDateTime or null if parsing fails
     */
    fun parse(input: String): TravelDateTime? {
        val lowerInput = input.lowercase().trim()
        val now = Calendar.getInstance()

        // Try relative date expressions
        parseRelativeDate(lowerInput, now)?.let { return it }

        // Try named day of week
        parseNamedDay(lowerInput, now)?.let { return it }

        // Try explicit date formats
        parseExplicitDate(lowerInput, now)?.let { return it }

        // Try Chinese date formats
        parseChineseDate(input, now)?.let { return it }

        Log.w(TAG, "Failed to parse date: $input")
        return null
    }

    /**
     * Parses time preference from input.
     *
     * @param input The input string
     * @return TimePreference or null
     */
    fun parseTimePreference(input: String): TimePreference? {
        val lowerInput = input.lowercase()

        // English time preferences
        val englishMappings = mapOf(
            "early morning" to TimePreference.EARLY_MORNING,
            "early" to TimePreference.EARLY_MORNING,
            "morning" to TimePreference.MORNING,
            "am" to TimePreference.MORNING,
            "afternoon" to TimePreference.AFTERNOON,
            "pm" to TimePreference.AFTERNOON,
            "evening" to TimePreference.EVENING,
            "night" to TimePreference.NIGHT,
            "late" to TimePreference.NIGHT
        )

        for ((keyword, pref) in englishMappings) {
            if (lowerInput.contains(keyword)) {
                return pref
            }
        }

        // Chinese time preferences
        val chineseMappings = mapOf(
            "清晨" to TimePreference.EARLY_MORNING,
            "早上" to TimePreference.MORNING,
            "上午" to TimePreference.MORNING,
            "中午" to TimePreference.AFTERNOON,
            "下午" to TimePreference.AFTERNOON,
            "傍晚" to TimePreference.EVENING,
            "晚上" to TimePreference.NIGHT
        )

        for ((keyword, pref) in chineseMappings) {
            if (input.contains(keyword)) {
                return pref
            }
        }

        return null
    }

    /**
     * Extracts a specific time from input (e.g., "9:30", "14:00").
     *
     * @param input The input string
     * @return Pair of (hour, minute) or null
     */
    fun parseSpecificTime(input: String): Pair<Int, Int>? {
        // Pattern: HH:MM or H:MM
        val timePattern = Regex("""(\d{1,2}):(\d{2})""")
        val match = timePattern.find(input)

        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null

            if (hour in 0..23 && minute in 0..59) {
                return Pair(hour, minute)
            }
        }

        // Pattern: 9am, 10pm
        val amPmPattern = Regex("""(\d{1,2})\s*(am|pm)""", RegexOption.IGNORE_CASE)
        val amPmMatch = amPmPattern.find(input)

        if (amPmMatch != null) {
            var hour = amPmMatch.groupValues[1].toIntOrNull() ?: return null
            val isPm = amPmMatch.groupValues[2].lowercase() == "pm"

            if (hour !in 1..12) return null

            if (isPm && hour != 12) hour += 12
            if (!isPm && hour == 12) hour = 0

            return Pair(hour, 0)
        }

        return null
    }

    /**
     * Generates VLM prompt for date selection.
     *
     * @param travelDate The parsed travel date
     * @param language Language code
     * @return Prompt text for date selection
     */
    fun generateDateSelectionHint(travelDate: TravelDateTime, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("日期选择提示：\n")
                append("- 目标日期：${travelDate.toDisplayString("zh")}\n")
                travelDate.timePreference?.let {
                    append("- 时间偏好：${it.toDisplayString("zh")}\n")
                }
                append("\n操作步骤：\n")
                append("1. 找到日期选择器或日历图标\n")
                append("2. 选择对应的年、月、日\n")
                append("3. 确认选择的日期正确\n")
                if (travelDate.isFlexible) {
                    append("提示：日期可以灵活调整，如果没有合适班次可选择前后一天")
                }
            }
        } else {
            buildString {
                append("Date selection hints:\n")
                append("- Target date: ${travelDate.toDisplayString("en")}\n")
                travelDate.timePreference?.let {
                    append("- Time preference: ${it.toDisplayString("en")}\n")
                }
                append("\nSteps:\n")
                append("1. Find the date picker or calendar icon\n")
                append("2. Select the correct year, month, and day\n")
                append("3. Verify the selected date is correct\n")
                if (travelDate.isFlexible) {
                    append("Note: Date is flexible, can adjust by a day if no suitable options")
                }
            }
        }
    }

    /**
     * Calculates the number of days from today.
     *
     * @param date The target date
     * @return Number of days (0 for today, 1 for tomorrow, etc.)
     */
    fun daysFromToday(date: Calendar): Int {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val targetDate = date.clone() as Calendar
        targetDate.set(Calendar.HOUR_OF_DAY, 0)
        targetDate.set(Calendar.MINUTE, 0)
        targetDate.set(Calendar.SECOND, 0)
        targetDate.set(Calendar.MILLISECOND, 0)

        val diffMs = targetDate.timeInMillis - today.timeInMillis
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }

    // ==================== Private Helper Methods ====================

    private fun parseRelativeDate(input: String, now: Calendar): TravelDateTime? {
        val calendar = now.clone() as Calendar

        when {
            input.contains("today") || input.contains("今天") -> {
                return TravelDateTime(
                    date = calendar,
                    timePreference = parseTimePreference(input),
                    originalText = input
                )
            }
            input.contains("tomorrow") || input.contains("明天") -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                return TravelDateTime(
                    date = calendar,
                    timePreference = parseTimePreference(input),
                    originalText = input
                )
            }
            input.contains("day after tomorrow") || input.contains("后天") -> {
                calendar.add(Calendar.DAY_OF_MONTH, 2)
                return TravelDateTime(
                    date = calendar,
                    timePreference = parseTimePreference(input),
                    originalText = input
                )
            }
            input.contains("next week") || input.contains("下周") || input.contains("下星期") -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                return TravelDateTime(
                    date = calendar,
                    isFlexible = true,
                    originalText = input
                )
            }
        }

        // Pattern: "in X days" or "X天后"
        val inDaysPattern = Regex("""in\s+(\d+)\s+days?""")
        val inDaysMatch = inDaysPattern.find(input)
        if (inDaysMatch != null) {
            val days = inDaysMatch.groupValues[1].toIntOrNull() ?: return null
            calendar.add(Calendar.DAY_OF_MONTH, days)
            return TravelDateTime(date = calendar, originalText = input)
        }

        val chineseDaysPattern = Regex("""(\d+)\s*天后""")
        val chineseDaysMatch = chineseDaysPattern.find(input)
        if (chineseDaysMatch != null) {
            val days = chineseDaysMatch.groupValues[1].toIntOrNull() ?: return null
            calendar.add(Calendar.DAY_OF_MONTH, days)
            return TravelDateTime(date = calendar, originalText = input)
        }

        return null
    }

    private fun parseNamedDay(input: String, now: Calendar): TravelDateTime? {
        val calendar = now.clone() as Calendar
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // English day names
        val englishDays = mapOf(
            "sunday" to Calendar.SUNDAY,
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY
        )

        // Chinese day names
        val chineseDays = mapOf(
            "周日" to Calendar.SUNDAY, "星期日" to Calendar.SUNDAY, "星期天" to Calendar.SUNDAY,
            "周一" to Calendar.MONDAY, "星期一" to Calendar.MONDAY,
            "周二" to Calendar.TUESDAY, "星期二" to Calendar.TUESDAY,
            "周三" to Calendar.WEDNESDAY, "星期三" to Calendar.WEDNESDAY,
            "周四" to Calendar.THURSDAY, "星期四" to Calendar.THURSDAY,
            "周五" to Calendar.FRIDAY, "星期五" to Calendar.FRIDAY,
            "周六" to Calendar.SATURDAY, "星期六" to Calendar.SATURDAY
        )

        val isNextWeek = input.contains("next") || input.contains("下") || input.contains("下周")
        val isThisWeek = input.contains("this") || input.contains("这") || input.contains("这周")

        // Find the target day
        var targetDay: Int? = null
        for ((name, day) in englishDays) {
            if (input.contains(name)) {
                targetDay = day
                break
            }
        }
        if (targetDay == null) {
            for ((name, day) in chineseDays) {
                if (input.contains(name)) {
                    targetDay = day
                    break
                }
            }
        }

        if (targetDay == null) return null

        // Calculate days to add
        var daysToAdd = targetDay - currentDayOfWeek
        if (daysToAdd <= 0 && !isThisWeek) {
            daysToAdd += 7
        }
        if (isNextWeek && daysToAdd < 7) {
            daysToAdd += 7
        }

        calendar.add(Calendar.DAY_OF_MONTH, daysToAdd)
        return TravelDateTime(
            date = calendar,
            timePreference = parseTimePreference(input),
            originalText = input
        )
    }

    private fun parseExplicitDate(input: String, now: Calendar): TravelDateTime? {
        val calendar = now.clone() as Calendar

        // Pattern: MM/DD or M/D
        val slashPattern = Regex("""(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?""")
        val slashMatch = slashPattern.find(input)
        if (slashMatch != null) {
            val month = slashMatch.groupValues[1].toIntOrNull() ?: return null
            val day = slashMatch.groupValues[2].toIntOrNull() ?: return null
            val yearStr = slashMatch.groupValues.getOrNull(3)
            val year = if (!yearStr.isNullOrEmpty()) {
                val y = yearStr.toIntOrNull() ?: return null
                if (y < 100) 2000 + y else y
            } else {
                calendar.get(Calendar.YEAR)
            }

            if (month !in 1..12 || day !in 1..31) return null

            calendar.set(year, month - 1, day)
            return TravelDateTime(date = calendar, originalText = input)
        }

        // Pattern: YYYY-MM-DD
        val isoPattern = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
        val isoMatch = isoPattern.find(input)
        if (isoMatch != null) {
            val year = isoMatch.groupValues[1].toIntOrNull() ?: return null
            val month = isoMatch.groupValues[2].toIntOrNull() ?: return null
            val day = isoMatch.groupValues[3].toIntOrNull() ?: return null

            if (month !in 1..12 || day !in 1..31) return null

            calendar.set(year, month - 1, day)
            return TravelDateTime(date = calendar, originalText = input)
        }

        // Pattern: "December 25" or "Dec 25"
        val monthNames = mapOf(
            "january" to 0, "jan" to 0,
            "february" to 1, "feb" to 1,
            "march" to 2, "mar" to 2,
            "april" to 3, "apr" to 3,
            "may" to 4,
            "june" to 5, "jun" to 5,
            "july" to 6, "jul" to 6,
            "august" to 7, "aug" to 7,
            "september" to 8, "sep" to 8, "sept" to 8,
            "october" to 9, "oct" to 9,
            "november" to 10, "nov" to 10,
            "december" to 11, "dec" to 11
        )

        for ((name, monthIndex) in monthNames) {
            val pattern = Regex("""$name\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s*,?\s*(\d{4}))?""", RegexOption.IGNORE_CASE)
            val match = pattern.find(input)
            if (match != null) {
                val day = match.groupValues[1].toIntOrNull() ?: continue
                val yearStr = match.groupValues.getOrNull(2)
                val year = if (!yearStr.isNullOrEmpty()) {
                    yearStr.toIntOrNull() ?: calendar.get(Calendar.YEAR)
                } else {
                    calendar.get(Calendar.YEAR)
                }

                if (day !in 1..31) continue

                calendar.set(year, monthIndex, day)
                return TravelDateTime(date = calendar, originalText = input)
            }
        }

        return null
    }

    private fun parseChineseDate(input: String, now: Calendar): TravelDateTime? {
        val calendar = now.clone() as Calendar

        // Pattern: X月X日 or X月X号
        val chinesePattern = Regex("""(\d{1,2})月(\d{1,2})[日号]""")
        val match = chinesePattern.find(input)

        if (match != null) {
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null

            if (month !in 1..12 || day !in 1..31) return null

            // Check for year
            val yearPattern = Regex("""(\d{4})年""")
            val yearMatch = yearPattern.find(input)
            val year = yearMatch?.groupValues?.get(1)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)

            calendar.set(year, month - 1, day)
            return TravelDateTime(
                date = calendar,
                timePreference = parseTimePreference(input),
                originalText = input
            )
        }

        return null
    }
}
