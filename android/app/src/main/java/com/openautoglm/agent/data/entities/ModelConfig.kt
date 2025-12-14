package com.openautoglm.agent.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a model configuration for VLM inference.
 * Supports both local (on-device) and cloud-based inference configurations.
 */
@Entity(tableName = "model_configs")
data class ModelConfig(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "type")
    val type: InferenceType,

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,

    @ColumnInfo(name = "api_key_encrypted")
    val apiKey: String? = null,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 3000,

    @ColumnInfo(name = "temperature")
    val temperature: Float = 0.0f,

    @ColumnInfo(name = "top_p")
    val topP: Float = 0.85f,

    @ColumnInfo(name = "frequency_penalty")
    val frequencyPenalty: Float = 0.2f,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
) {
    init {
        require(temperature in 0.0f..2.0f) {
            "Temperature must be between 0.0 and 2.0, got $temperature"
        }
        require(topP in 0.0f..1.0f) {
            "TopP must be between 0.0 and 1.0, got $topP"
        }
        require(frequencyPenalty in -2.0f..2.0f) {
            "FrequencyPenalty must be between -2.0 and 2.0, got $frequencyPenalty"
        }
        require(maxTokens > 0) {
            "MaxTokens must be positive, got $maxTokens"
        }
        if (type == InferenceType.CLOUD) {
            requireNotNull(baseUrl) {
                "baseUrl is required for CLOUD inference type"
            }
            requireNotNull(apiKey) {
                "apiKey is required for CLOUD inference type"
            }
        }
    }
}

/**
 * Type converters for ModelConfig entity.
 */
