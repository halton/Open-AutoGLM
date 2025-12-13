package com.openautoglm.agent.model

import com.google.gson.annotations.SerializedName

/**
 * Request model for OpenAI-compatible chat completion API.
 * Follows the model-client.yaml contract specification.
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 3000,
    val temperature: Float = 0.0f,
    @SerializedName("top_p")
    val topP: Float = 0.85f,
    @SerializedName("frequency_penalty")
    val frequencyPenalty: Float = 0.2f,
    val stream: Boolean = false
)
