package com.openautoglm.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from the chat completion API (OpenAI-compatible format).
 */
@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object")
    val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

/**
 * A single choice in the chat completion response.
 */
@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason")
    val finishReason: String
)

/**
 * Token usage statistics for the completion request.
 */
@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)
