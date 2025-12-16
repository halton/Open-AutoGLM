package com.openautoglm.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request model for OpenAI-compatible chat completion API.
 * Follows the model-client.yaml contract specification.
 *
 * Note: Some providers (e.g., BigModel) don't support all parameters.
 * Use null for unsupported parameters to exclude them from JSON.
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int = 3000,
    val temperature: Float = 0.0f,
    @SerialName("top_p")
    val topP: Float = 0.85f,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Float? = null,
    val stream: Boolean = false
)
