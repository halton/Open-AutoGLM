package com.openautoglm.agent.agent

import android.util.Log

/**
 * Helper class for handling seat class selection in train booking.
 *
 * This class provides utilities for:
 * - Seat class recommendations based on preferences
 * - Generating VLM prompts for seat selection
 * - Handling seat availability checks
 *
 * Works in conjunction with TrainSearcher for train booking flow.
 */
object SeatSelector {

    private const val TAG = "SeatSelector"

    /**
     * Seat selection preference criteria.
     */
    data class SeatPreference(
        val preferredClass: TrainSearcher.SeatClass? = null,
        val maxPrice: Double? = null,
        val requireWindow: Boolean = false,
        val requireAisle: Boolean = false,
        val allowStanding: Boolean = false,
        val prioritizeComfort: Boolean = false,
        val prioritizePrice: Boolean = false
    )

    /**
     * Result of seat selection recommendation.
     */
    data class SeatRecommendation(
        val seatClass: TrainSearcher.SeatClass,
        val reason: String,
        val priority: Int  // 1 = highest priority
    )

    /**
     * Gets seat class recommendations based on preferences.
     *
     * @param preference User's seat preference
     * @param available Available seat classes with prices
     * @return List of recommendations sorted by priority
     */
    fun getRecommendations(
        preference: SeatPreference,
        available: Map<TrainSearcher.SeatClass, TrainSearcher.SeatAvailability>
    ): List<SeatRecommendation> {
        val recommendations = mutableListOf<SeatRecommendation>()
        var priority = 1

        // Filter available seats
        val availableSeats = available.filter { it.value.isAvailable }

        if (availableSeats.isEmpty()) {
            Log.w(TAG, "No available seats found")
            return emptyList()
        }

        // If user has preferred class and it's available
        preference.preferredClass?.let { preferred ->
            if (preferred in availableSeats) {
                recommendations.add(SeatRecommendation(
                    seatClass = preferred,
                    reason = "Your preferred seat class",
                    priority = priority++
                ))
            }
        }

        // Sort by comfort or price based on priority
        val sortedSeats = if (preference.prioritizePrice) {
            availableSeats.entries.sortedBy { extractPrice(it.value.price) }
        } else if (preference.prioritizeComfort) {
            availableSeats.keys.sortedBy { getComfortRanking(it) }
                .map { key -> availableSeats.entries.first { it.key == key } }
        } else {
            availableSeats.entries.toList()
        }

        // Add remaining recommendations
        for (entry in sortedSeats) {
            if (recommendations.none { it.seatClass == entry.key }) {
                // Skip standing if not allowed
                if (entry.key == TrainSearcher.SeatClass.STANDING && !preference.allowStanding) {
                    continue
                }

                // Check max price
                if (preference.maxPrice != null) {
                    val price = extractPrice(entry.value.price)
                    if (price != null && price > preference.maxPrice) {
                        continue
                    }
                }

                recommendations.add(SeatRecommendation(
                    seatClass = entry.key,
                    reason = getReasonForClass(entry.key, preference),
                    priority = priority++
                ))
            }
        }

        return recommendations.take(3)
    }

    /**
     * Generates VLM prompt for seat selection.
     *
     * @param recommendations List of seat recommendations
     * @param language Language code
     * @return Prompt text for seat selection
     */
    fun generateSeatSelectionHint(
        recommendations: List<SeatRecommendation>,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("座位选择提示：\n")
                if (recommendations.isEmpty()) {
                    append("暂无可用座位，请尝试其他车次。")
                } else {
                    append("推荐选择顺序：\n")
                    recommendations.forEach { rec ->
                        append("${rec.priority}. ${rec.seatClass.toDisplayString("zh")}（${rec.reason}）\n")
                    }
                    append("\n请点击选择可用的座位等级，然后继续预订流程。")
                }
            }
        } else {
            buildString {
                append("Seat selection hints:\n")
                if (recommendations.isEmpty()) {
                    append("No seats available, please try another train.")
                } else {
                    append("Recommended selection order:\n")
                    recommendations.forEach { rec ->
                        append("${rec.priority}. ${rec.seatClass.toDisplayString("en")} (${rec.reason})\n")
                    }
                    append("\nPlease tap to select an available seat class, then continue booking.")
                }
            }
        }
    }

    /**
     * Generates VLM prompt for specific seat position selection.
     *
     * @param preference Seat preference
     * @param language Language code
     * @return Prompt text for seat position selection
     */
    fun generatePositionSelectionHint(
        preference: SeatPreference,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("座位位置选择提示：\n")
                when {
                    preference.requireWindow -> append("- 请选择靠窗座位（A/F位置）\n")
                    preference.requireAisle -> append("- 请选择靠过道座位（C/D位置）\n")
                    else -> append("- 选择任意可用位置\n")
                }
                append("\n如果有座位图，请点击您偏好的位置。\n")
                append("如果没有选座功能，系统会自动分配座位。")
            }
        } else {
            buildString {
                append("Seat position selection hints:\n")
                when {
                    preference.requireWindow -> append("- Please select a window seat (A/F position)\n")
                    preference.requireAisle -> append("- Please select an aisle seat (C/D position)\n")
                    else -> append("- Select any available position\n")
                }
                append("\nIf a seat map is shown, tap your preferred position.\n")
                append("If no seat selection is available, the system will auto-assign.")
            }
        }
    }

    /**
     * Common seat labels for different apps.
     */
    object SeatLabels {
        val businessLabels = listOf(
            "Business", "商务座", "商务", "SW"
        )
        val firstClassLabels = listOf(
            "First Class", "First", "一等座", "一等", "YDZ"
        )
        val secondClassLabels = listOf(
            "Second Class", "Second", "二等座", "二等", "EDZ"
        )
        val softSleeperLabels = listOf(
            "Soft Sleeper", "软卧", "RW"
        )
        val hardSleeperLabels = listOf(
            "Hard Sleeper", "硬卧", "YW"
        )
        val hardSeatLabels = listOf(
            "Hard Seat", "硬座", "YZ"
        )
        val standingLabels = listOf(
            "Standing", "No Seat", "无座", "WZ"
        )
    }

    // ==================== Private Helper Methods ====================

    private fun extractPrice(priceStr: String): Double? {
        val pattern = Regex("""[\d.]+""")
        val match = pattern.find(priceStr)
        return match?.value?.toDoubleOrNull()
    }

    private fun getComfortRanking(seatClass: TrainSearcher.SeatClass): Int {
        return when (seatClass) {
            TrainSearcher.SeatClass.BUSINESS -> 1
            TrainSearcher.SeatClass.FIRST_CLASS -> 2
            TrainSearcher.SeatClass.SOFT_SLEEPER -> 3
            TrainSearcher.SeatClass.SECOND_CLASS -> 4
            TrainSearcher.SeatClass.HARD_SLEEPER -> 5
            TrainSearcher.SeatClass.SOFT_SEAT -> 6
            TrainSearcher.SeatClass.HARD_SEAT -> 7
            TrainSearcher.SeatClass.STANDING -> 8
            TrainSearcher.SeatClass.ANY -> 9
        }
    }

    private fun getReasonForClass(
        seatClass: TrainSearcher.SeatClass,
        preference: SeatPreference
    ): String {
        return when {
            preference.prioritizeComfort -> when (seatClass) {
                TrainSearcher.SeatClass.BUSINESS -> "Most comfortable option"
                TrainSearcher.SeatClass.FIRST_CLASS -> "Comfortable with more space"
                TrainSearcher.SeatClass.SOFT_SLEEPER -> "Best for overnight travel"
                else -> "Available option"
            }
            preference.prioritizePrice -> "Best value option"
            else -> "Available option"
        }
    }
}
