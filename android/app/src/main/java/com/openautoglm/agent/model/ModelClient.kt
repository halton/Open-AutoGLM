package com.openautoglm.agent.model

/**
 * Base interface for model inference clients.
 *
 * This interface abstracts the communication layer for both on-device
 * and cloud-based inference, providing a unified API for sending
 * chat messages and receiving model responses.
 */
interface ModelClient {

    /**
     * Sends a list of chat messages to the model and returns the response.
     *
     * @param messages The conversation history as a list of [ChatMessage] objects.
     * @return [ModelResponse] containing the model's output and metadata.
     * @throws ModelClientException if the request fails.
     */
    suspend fun request(messages: List<ChatMessage>): ModelResponse
}
