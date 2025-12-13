package com.openautoglm.agent.model

import com.google.gson.annotations.SerializedName

/**
 * Response from the chat completion API (OpenAI-compatible format).
 */
data class ChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

/**
 * A single choice in the chat completion response.
 */
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

/**
 * Token usage statistics for the completion request.
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)
