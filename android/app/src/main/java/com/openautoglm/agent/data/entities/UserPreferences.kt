package com.openautoglm.agent.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User preferences entity for storing agent configuration and user settings.
 */
@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "default",

    @ColumnInfo(name = "language")
    val language: String = "zh",

    @ColumnInfo(name = "default_inference_mode")
    val defaultInferenceMode: InferenceMode = InferenceMode.AUTO,

    @ColumnInfo(name = "confirm_sensitive_actions")
    val confirmSensitiveActions: Boolean = true,

    @ColumnInfo(name = "enabled_apps")
    val enabledApps: Set<String>? = null, // null means all apps are enabled

    @ColumnInfo(name = "cloud_api_enabled")
    val cloudApiEnabled: Boolean = true,

    @ColumnInfo(name = "max_steps_per_task")
    val maxStepsPerTask: Int = 100,

    @ColumnInfo(name = "default_payment_method")
    val defaultPaymentMethod: String? = null,

    @ColumnInfo(name = "default_delivery_address")
    val defaultDeliveryAddress: String? = null,

    @ColumnInfo(name = "on_device_model_preference")
    val onDeviceModelPreference: String = "qwen2.5-vl-3b"
)

/**
 * Type converters for UserPreferences entity.
 */
