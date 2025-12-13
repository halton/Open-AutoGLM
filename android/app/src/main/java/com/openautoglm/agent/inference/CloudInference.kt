package com.openautoglm.agent.inference

import android.content.Context
import com.openautoglm.agent.model.ChatCompletionRequest
import com.openautoglm.agent.model.ChatCompletionResponse
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ModelClient
import com.openautoglm.agent.model.ModelResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cloud inference client for making API calls to remote VLM providers.
 *
 * Supports multiple cloud providers:
 * - BigModel (AutoGLM-Phone-9B)
 * - DashScope (Qwen2.5-VL-72B)
 * - OpenAI-compatible endpoints
 * - Custom self-hosted models
 *
 * Uses OkHttp for HTTP communication with proper error handling,
 * timeouts, and retry logic.
 *
 * @param context Application context for accessing secure storage
 */
class CloudInference(
    context: Context
) : ModelClient {

    private val secureStorage = SecureKeyStorage(context)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
    }

    /**
     * Generates a chat completion using the configured cloud provider.
     *
     * @param messages List of chat messages (system, user, assistant)
     * @param temperature Sampling temperature (0.0-2.0)
     * @param maxTokens Maximum tokens to generate
     * @param modelOverride Optional model ID to override default
     * @return ModelResponse with thinking and action
     */
    override suspend fun generateChatCompletion(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int,
        modelOverride: String?
    ): ModelResponse = withContext(Dispatchers.IO) {
        val provider = secureStorage.getSelectedProvider()
        val apiKey = getApiKeyForProvider(provider)
            ?: throw IllegalStateException("No API key configured for provider: $provider")

        val (baseUrl, model) = when (provider) {
            InferenceProvider.BIGMODEL -> {
                BigModelConfig.getChatCompletionsUrl() to
                    (modelOverride ?: secureStorage.getBigModelModelId())
            }
            InferenceProvider.DASHSCOPE -> {
                DashScopeConfig.getChatCompletionsUrl() to
                    (modelOverride ?: secureStorage.getDashScopeModelId())
            }
            InferenceProvider.OPENAI -> {
                "https://api.openai.com/v1/chat/completions" to
                    (modelOverride ?: "gpt-4-vision-preview")
            }
            InferenceProvider.CUSTOM -> {
                val customUrl = secureStorage.getCustomApiBaseUrl()
                    ?: throw IllegalStateException("Custom base URL not configured")
                "$customUrl/chat/completions" to (modelOverride ?: "default")
            }
        }

        val request = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            topP = 0.9f,
            stream = false
        )

        val response = executeRequest(baseUrl, apiKey, request, provider)
        parseModelResponse(response)
    }

    /**
     * Executes an HTTP request to the cloud API.
     */
    private suspend fun executeRequest(
        url: String,
        apiKey: String,
        request: ChatCompletionRequest,
        provider: InferenceProvider
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        val requestBody = json.encodeToString(request)
            .toRequestBody(MEDIA_TYPE_JSON.toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .apply {
                // Add provider-specific headers
                when (provider) {
                    InferenceProvider.DASHSCOPE -> {
                        addHeader("X-DashScope-SSE", "disable")
                    }
                    else -> {}
                }
            }
            .build()

        try {
            val response = httpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw IOException("API request failed (${response.code}): $errorBody")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            json.decodeFromString<ChatCompletionResponse>(responseBody)
        } catch (e: IOException) {
            throw IOException("Network error: ${e.message}", e)
        } catch (e: Exception) {
            throw IOException("Failed to parse response: ${e.message}", e)
        }
    }

    /**
     * Parses the ChatCompletionResponse into a ModelResponse.
     */
    private fun parseModelResponse(response: ChatCompletionResponse): ModelResponse {
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("No content in response")

        // Parse <think>...</think> and <answer>...</answer> tags
        val thinkRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
        val answerRegex = Regex("<answer>(.*?)</answer>", RegexOption.DOT_MATCHES_ALL)

        val thinkMatch = thinkRegex.find(content)
        val answerMatch = answerRegex.find(content)

        val thinking = thinkMatch?.groupValues?.get(1)?.trim() ?: ""
        val action = answerMatch?.groupValues?.get(1)?.trim()
            ?: throw IllegalStateException("No <answer> tag found in response")

        return ModelResponse(
            thinking = thinking,
            action = action,
            rawResponse = content,
            modelId = response.model,
            usage = mapOf(
                "prompt_tokens" to response.usage.promptTokens.toString(),
                "completion_tokens" to response.usage.completionTokens.toString(),
                "total_tokens" to response.usage.totalTokens.toString()
            )
        )
    }

    /**
     * Gets the API key for the specified provider.
     */
    private fun getApiKeyForProvider(provider: InferenceProvider): String? {
        return when (provider) {
            InferenceProvider.BIGMODEL -> secureStorage.getBigModelApiKey()
            InferenceProvider.DASHSCOPE -> secureStorage.getDashScopeApiKey()
            InferenceProvider.OPENAI -> secureStorage.getOpenAIApiKey()
            InferenceProvider.CUSTOM -> secureStorage.getCustomApiKey()
        }
    }

    /**
     * Checks if the client is properly configured with an API key.
     *
     * @return true if configured and ready to use
     */
    fun isConfigured(): Boolean {
        return secureStorage.hasSelectedProviderApiKey()
    }

    /**
     * Gets the currently selected provider.
     *
     * @return The active inference provider
     */
    fun getProvider(): InferenceProvider {
        return secureStorage.getSelectedProvider()
    }

    /**
     * Gets the model ID being used for the current provider.
     *
     * @return The model ID string
     */
    fun getCurrentModelId(): String {
        return when (secureStorage.getSelectedProvider()) {
            InferenceProvider.BIGMODEL -> secureStorage.getBigModelModelId()
            InferenceProvider.DASHSCOPE -> secureStorage.getDashScopeModelId()
            InferenceProvider.OPENAI -> "gpt-4-vision-preview"
            InferenceProvider.CUSTOM -> "custom-model"
        }
    }

    /**
     * Validates the current configuration by making a test request.
     *
     * @return true if the configuration is valid and working
     */
    suspend fun validateConfiguration(): Boolean {
        return try {
            val testMessages = listOf(
                ChatMessage.system("Test configuration"),
                ChatMessage.user("Hello")
            )
            generateChatCompletion(testMessages, temperature = 0.1f, maxTokens = 10)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Estimates the cost of a request based on token usage.
     * Returns cost in USD (approximate).
     *
     * @param promptTokens Number of tokens in the prompt
     * @param completionTokens Number of tokens in the completion
     * @return Estimated cost in USD
     */
    fun estimateCost(promptTokens: Int, completionTokens: Int): Double {
        return when (secureStorage.getSelectedProvider()) {
            InferenceProvider.BIGMODEL -> {
                // BigModel pricing (example rates)
                (promptTokens * 0.0001 + completionTokens * 0.0002) / 1000.0
            }
            InferenceProvider.DASHSCOPE -> {
                // DashScope pricing (example rates for Qwen-VL-72B)
                (promptTokens * 0.0002 + completionTokens * 0.0004) / 1000.0
            }
            InferenceProvider.OPENAI -> {
                // OpenAI GPT-4V pricing
                (promptTokens * 0.01 + completionTokens * 0.03) / 1000.0
            }
            InferenceProvider.CUSTOM -> {
                // Custom provider - no cost estimate
                0.0
            }
        }
    }
}
