package com.openautoglm.agent.model

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.openautoglm.agent.data.entities.InferenceType
import com.openautoglm.agent.data.entities.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Exception thrown when a model client operation fails.
 *
 * @property message Human-readable error message
 * @property httpStatusCode HTTP status code if applicable (null for non-HTTP errors)
 * @property cause The underlying cause of the exception
 */
class ModelClientException(
    override val message: String,
    val httpStatusCode: Int? = null,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Cloud-based implementation of [ModelClient] for VLM inference.
 *
 * This client communicates with OpenAI-compatible APIs (such as AutoGLM-Phone API)
 * to perform vision-language model inference. It handles:
 * - API authentication via Bearer tokens
 * - Request serialization using Gson
 * - Response parsing and error handling
 * - Inference time measurement
 *
 * @property config The model configuration containing API endpoint and credentials
 */
class CloudModelClient(
    private val config: ModelConfig
) : ModelClient {

    companion object {
        private const val TAG = "CloudModelClient"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 120L
        private const val WRITE_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    init {
        require(config.type == InferenceType.CLOUD) {
            "CloudModelClient requires a CLOUD type ModelConfig, got ${config.type}"
        }
        requireNotNull(config.baseUrl) {
            "CloudModelClient requires a non-null baseUrl"
        }
        requireNotNull(config.apiKey) {
            "CloudModelClient requires a non-null apiKey"
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ChatMessage::class.java, ChatMessageSerializer())
        .registerTypeAdapter(MessageContent::class.java, MessageContentSerializer())
        .registerTypeAdapter(ContentPart::class.java, ContentPartSerializer())
        .registerTypeAdapter(ChatRole::class.java, ChatRoleSerializer())
        .registerTypeAdapter(ImageDetail::class.java, ImageDetailSerializer())
        .registerTypeHierarchyAdapter(MessageContent::class.java, MessageContentDeserializer())
        .create()

    /**
     * Sends a list of chat messages to the cloud VLM and returns the response.
     *
     * @param messages The conversation history as a list of [ChatMessage] objects
     * @return [ModelResponse] containing the model's output and metadata
     * @throws ModelClientException if the request fails due to network, API, or parsing errors
     */
    override suspend fun request(messages: List<ChatMessage>): ModelResponse {
        val startTime = System.currentTimeMillis()

        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = messages,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            topP = config.topP,
            frequencyPenalty = config.frequencyPenalty,
            stream = false
        )

        val requestJson = gson.toJson(request)
        Log.d(TAG, "Sending request to ${config.baseUrl}: ${requestJson.take(500)}...")

        val httpRequest = buildHttpRequest(requestJson)

        return withContext(Dispatchers.IO) {
            try {
                val response = executeRequest(httpRequest)
                val inferenceTimeMs = System.currentTimeMillis() - startTime

                parseResponse(response, inferenceTimeMs)
            } catch (e: ModelClientException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during request", e)
                throw ModelClientException(
                    message = "Unexpected error: ${e.message}",
                    cause = e
                )
            }
        }
    }

    /**
     * Builds the HTTP request with proper headers and body.
     */
    private fun buildHttpRequest(requestJson: String): Request {
        val endpoint = buildEndpointUrl()

        return Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /**
     * Builds the full endpoint URL from the base URL.
     * Handles various base URL formats (with or without trailing slash, with or without path).
     */
    private fun buildEndpointUrl(): String {
        val baseUrl = config.baseUrl!!.trimEnd('/')

        // If baseUrl already ends with a path like /v1/chat/completions, use it as-is
        return if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else if (baseUrl.endsWith("/v1")) {
            "$baseUrl/chat/completions"
        } else {
            "$baseUrl/v1/chat/completions"
        }
    }

    /**
     * Executes the HTTP request asynchronously using suspending coroutines.
     */
    private suspend fun executeRequest(request: Request): String {
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return

                    Log.e(TAG, "Request failed", e)
                    continuation.resumeWithException(
                        ModelClientException(
                            message = "Network error: ${e.message}",
                            cause = e
                        )
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) return

                    response.use { resp ->
                        val body = resp.body?.string()

                        if (!resp.isSuccessful) {
                            Log.e(TAG, "API error: ${resp.code} - $body")
                            continuation.resumeWithException(
                                ModelClientException(
                                    message = "API error: ${resp.code} - ${body?.take(500) ?: "No body"}",
                                    httpStatusCode = resp.code
                                )
                            )
                            return
                        }

                        if (body.isNullOrEmpty()) {
                            continuation.resumeWithException(
                                ModelClientException(
                                    message = "Empty response body",
                                    httpStatusCode = resp.code
                                )
                            )
                            return
                        }

                        continuation.resume(body)
                    }
                }
            })
        }
    }

    /**
     * Parses the API response into a [ModelResponse].
     */
    private fun parseResponse(responseBody: String, inferenceTimeMs: Long): ModelResponse {
        Log.d(TAG, "Parsing response: ${responseBody.take(500)}...")

        try {
            val response = gson.fromJson(responseBody, ChatCompletionResponse::class.java)

            if (response.choices.isEmpty()) {
                throw ModelClientException(
                    message = "No choices in response"
                )
            }

            val choice = response.choices.first()
            val content = choice.message.content.asText()

            Log.d(TAG, "Response content: ${content.take(200)}...")

            return ModelResponse.fromRawContent(
                content = content,
                inferenceTimeMs = inferenceTimeMs,
                modelUsed = response.model,
                tokenCount = response.usage?.totalTokens
            )
        } catch (e: ModelClientException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            throw ModelClientException(
                message = "Failed to parse response: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Custom Gson serializer for [ChatMessage] that handles the OpenAI API format.
     */
    private class ChatMessageSerializer : JsonSerializer<ChatMessage> {
        override fun serialize(
            src: ChatMessage,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return JsonObject().apply {
                add("role", context.serialize(src.role))
                add("content", context.serialize(src.content))
            }
        }
    }

    /**
     * Custom Gson serializer for [MessageContent] that handles both string and array formats.
     */
    private class MessageContentSerializer : JsonSerializer<MessageContent> {
        override fun serialize(
            src: MessageContent,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return when (src) {
                is MessageContent.Text -> JsonPrimitive(src.text)
                is MessageContent.Parts -> JsonArray().apply {
                    src.parts.forEach { part ->
                        add(context.serialize(part))
                    }
                }
            }
        }
    }

    /**
     * Custom Gson deserializer for [MessageContent] that handles both string and array formats.
     */
    private class MessageContentDeserializer : JsonDeserializer<MessageContent> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): MessageContent {
            return when {
                json.isJsonPrimitive -> MessageContent.Text(json.asString)
                json.isJsonArray -> {
                    // For simplicity, extract text from array format
                    val text = json.asJsonArray
                        .filter { it.isJsonObject && it.asJsonObject.get("type")?.asString == "text" }
                        .mapNotNull { it.asJsonObject.get("text")?.asString }
                        .joinToString("")
                    MessageContent.Text(text)
                }
                else -> MessageContent.Text("")
            }
        }
    }

    /**
     * Custom Gson serializer for [ContentPart] that handles text and image_url parts.
     */
    private class ContentPartSerializer : JsonSerializer<ContentPart> {
        override fun serialize(
            src: ContentPart,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return when (src) {
                is ContentPart.TextPart -> JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", src.text)
                }
                is ContentPart.ImageUrlPart -> JsonObject().apply {
                    addProperty("type", "image_url")
                    add("image_url", JsonObject().apply {
                        addProperty("url", src.imageUrl.url)
                        // Only add detail if specified (some providers like BigModel don't support it)
                        src.imageUrl.detail?.let { detail ->
                            addProperty("detail", detail.name.lowercase())
                        }
                    })
                }
            }
        }
    }

    /**
     * Custom Gson serializer for [ChatRole] that outputs lowercase role names.
     */
    private class ChatRoleSerializer : JsonSerializer<ChatRole> {
        override fun serialize(
            src: ChatRole,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return JsonPrimitive(src.name.lowercase())
        }
    }

    /**
     * Custom Gson serializer for [ImageDetail] that outputs lowercase detail names.
     */
    private class ImageDetailSerializer : JsonSerializer<ImageDetail> {
        override fun serialize(
            src: ImageDetail,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return JsonPrimitive(src.name.lowercase())
        }
    }
}
