package com.openautoglm.agent.agent

import android.util.Log

/**
 * Helper class for managing passenger information in travel bookings.
 *
 * This class provides utilities for:
 * - Managing saved passenger profiles
 * - Generating VLM prompts for passenger info entry
 * - Handling ID verification requirements
 *
 * Works in conjunction with TrainSearcher for train booking flow.
 */
object PassengerManager {

    private const val TAG = "PassengerManager"

    /**
     * ID document types supported.
     */
    enum class IdType {
        CHINESE_ID,         // 身份证
        PASSPORT,           // 护照
        HK_MACAO_PERMIT,    // 港澳通行证
        TAIWAN_PERMIT,      // 台湾通行证
        FOREIGN_PASSPORT;   // 外国护照

        fun toDisplayString(language: String = "en"): String {
            return if (language == "zh") {
                when (this) {
                    CHINESE_ID -> "身份证"
                    PASSPORT -> "护照"
                    HK_MACAO_PERMIT -> "港澳通行证"
                    TAIWAN_PERMIT -> "台湾通行证"
                    FOREIGN_PASSPORT -> "外国护照"
                }
            } else {
                when (this) {
                    CHINESE_ID -> "Chinese ID Card"
                    PASSPORT -> "Passport"
                    HK_MACAO_PERMIT -> "HK/Macao Permit"
                    TAIWAN_PERMIT -> "Taiwan Permit"
                    FOREIGN_PASSPORT -> "Foreign Passport"
                }
            }
        }
    }

    /**
     * Passenger type for ticketing.
     */
    enum class PassengerType {
        ADULT,
        CHILD,
        STUDENT,
        DISABLED,
        MILITARY;

        fun toDisplayString(language: String = "en"): String {
            return if (language == "zh") {
                when (this) {
                    ADULT -> "成人"
                    CHILD -> "儿童"
                    STUDENT -> "学生"
                    DISABLED -> "残疾人"
                    MILITARY -> "军人"
                }
            } else {
                when (this) {
                    ADULT -> "Adult"
                    CHILD -> "Child"
                    STUDENT -> "Student"
                    DISABLED -> "Disabled"
                    MILITARY -> "Military"
                }
            }
        }
    }

    /**
     * Data class representing a passenger profile.
     */
    data class PassengerProfile(
        val name: String,
        val idType: IdType = IdType.CHINESE_ID,
        val idNumber: String = "",
        val phoneNumber: String = "",
        val passengerType: PassengerType = PassengerType.ADULT,
        val isDefault: Boolean = false
    ) {
        /**
         * Masks sensitive information for display.
         */
        fun toMaskedString(language: String = "en"): String {
            val maskedId = if (idNumber.length > 4) {
                idNumber.take(3) + "***" + idNumber.takeLast(4)
            } else {
                "***"
            }

            return if (language == "zh") {
                "$name (${idType.toDisplayString("zh")}: $maskedId)"
            } else {
                "$name (${idType.toDisplayString("en")}: $maskedId)"
            }
        }
    }

    /**
     * In-memory storage for passenger profiles.
     * In production, this would be persisted to database.
     */
    private val savedPassengers = mutableListOf<PassengerProfile>()

    /**
     * Gets all saved passenger profiles.
     *
     * @return List of saved passengers
     */
    fun getSavedPassengers(): List<PassengerProfile> {
        return savedPassengers.toList()
    }

    /**
     * Gets the default passenger profile.
     *
     * @return Default passenger or null if none set
     */
    fun getDefaultPassenger(): PassengerProfile? {
        return savedPassengers.find { it.isDefault }
    }

    /**
     * Saves a new passenger profile.
     *
     * @param passenger The passenger profile to save
     */
    fun savePassenger(passenger: PassengerProfile) {
        // Remove existing default if new one is default
        if (passenger.isDefault) {
            savedPassengers.replaceAll { it.copy(isDefault = false) }
        }
        savedPassengers.add(passenger)
        Log.d(TAG, "Saved passenger: ${passenger.name}")
    }

    /**
     * Removes a saved passenger profile.
     *
     * @param name The passenger name to remove
     */
    fun removePassenger(name: String) {
        savedPassengers.removeAll { it.name == name }
        Log.d(TAG, "Removed passenger: $name")
    }

    /**
     * Generates VLM prompt for passenger selection.
     *
     * @param passengers List of passengers to select from
     * @param language Language code
     * @return Prompt text for passenger selection
     */
    fun generatePassengerSelectionHint(
        passengers: List<PassengerProfile>,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("选择乘客提示：\n")
                if (passengers.isEmpty()) {
                    append("没有已保存的乘客信息，请手动输入乘客信息。\n")
                } else {
                    append("已保存的乘客：\n")
                    passengers.forEachIndexed { index, passenger ->
                        val defaultMark = if (passenger.isDefault) " (默认)" else ""
                        append("${index + 1}. ${passenger.toMaskedString("zh")}$defaultMark\n")
                    }
                    append("\n请从列表中选择乘客，或添加新乘客。")
                }
            }
        } else {
            buildString {
                append("Passenger selection hints:\n")
                if (passengers.isEmpty()) {
                    append("No saved passengers found. Please enter passenger info manually.\n")
                } else {
                    append("Saved passengers:\n")
                    passengers.forEachIndexed { index, passenger ->
                        val defaultMark = if (passenger.isDefault) " (Default)" else ""
                        append("${index + 1}. ${passenger.toMaskedString("en")}$defaultMark\n")
                    }
                    append("\nPlease select a passenger from the list, or add a new one.")
                }
            }
        }
    }

    /**
     * Generates VLM prompt for entering new passenger info.
     *
     * @param passengerType The type of passenger
     * @param language Language code
     * @return Prompt text for passenger info entry
     */
    fun generatePassengerEntryHint(
        passengerType: PassengerType = PassengerType.ADULT,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("输入乘客信息提示：\n")
                append("乘客类型：${passengerType.toDisplayString("zh")}\n\n")
                append("需要填写的信息：\n")
                append("1. 姓名（需与证件姓名一致）\n")
                append("2. 证件类型（身份证/护照等）\n")
                append("3. 证件号码\n")
                append("4. 联系电话\n\n")
                append("请按顺序填写各项信息，确保信息准确无误。\n")
                append("注意：证件信息将用于实名制验证，请务必填写正确。")
            }
        } else {
            buildString {
                append("Passenger info entry hints:\n")
                append("Passenger type: ${passengerType.toDisplayString("en")}\n\n")
                append("Required information:\n")
                append("1. Full name (must match ID document)\n")
                append("2. ID type (ID card/Passport etc.)\n")
                append("3. ID number\n")
                append("4. Phone number\n\n")
                append("Please fill in each field accurately.\n")
                append("Note: ID information is required for identity verification.")
            }
        }
    }

    /**
     * Generates VLM prompt for ID verification steps.
     *
     * @param language Language code
     * @return Prompt text for ID verification
     */
    fun generateIdVerificationHint(language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("身份验证提示：\n")
                append("部分操作需要验证身份：\n")
                append("1. 可能需要输入验证码\n")
                append("2. 可能需要进行人脸识别\n")
                append("3. 可能需要短信验证\n\n")
                append("如果遇到验证步骤，请暂停自动操作，等待用户完成验证。")
            }
        } else {
            buildString {
                append("ID verification hints:\n")
                append("Some operations require identity verification:\n")
                append("1. May need to enter verification code\n")
                append("2. May need face recognition\n")
                append("3. May need SMS verification\n\n")
                append("If verification is required, pause automation and wait for user to complete.")
            }
        }
    }

    /**
     * Common field labels for passenger forms.
     */
    object FieldLabels {
        val nameLabels = listOf(
            "Name", "Full Name", "姓名", "乘客姓名", "真实姓名"
        )
        val idTypeLabels = listOf(
            "ID Type", "Document Type", "证件类型", "证件种类"
        )
        val idNumberLabels = listOf(
            "ID Number", "Document Number", "证件号码", "身份证号"
        )
        val phoneLabels = listOf(
            "Phone", "Mobile", "Phone Number", "手机号", "联系电话", "手机号码"
        )
        val addPassengerLabels = listOf(
            "Add Passenger", "Add", "添加乘客", "新增乘客", "+"
        )
    }
}
