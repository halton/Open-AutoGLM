package com.openautoglm.agent.inference

/**
 * Configuration for BigModel (ZhipuAI) API integration.
 *
 * BigModel provides the AutoGLM-Phone-9B model specifically designed for
 * phone automation tasks with vision-language understanding.
 *
 * API Documentation: https://open.bigmodel.cn/dev/api
 */
object BigModelConfig {

    /**
     * Base URL for BigModel API.
     */
    const val BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"

    /**
     * Chat completions endpoint (OpenAI-compatible).
     */
    const val CHAT_COMPLETIONS_ENDPOINT = "chat/completions"

    /**
     * Default model ID for phone automation.
     * AutoGLM-Phone-9B is optimized for Android UI understanding and action planning.
     */
    const val DEFAULT_MODEL = "AutoGLM-Phone-9B"

    /**
     * Alternative models supported by BigModel.
     */
    object Models {
        /** Main phone automation model (9B parameters) */
        const val AUTOGLM_PHONE_9B = "AutoGLM-Phone-9B"

        /** General-purpose vision-language model */
        const val GLM_4V = "glm-4v"

        /** Plus version with enhanced capabilities */
        const val GLM_4V_PLUS = "glm-4v-plus"
    }

    /**
     * Default inference parameters for phone automation tasks.
     */
    object DefaultParams {
        /** Temperature for sampling (0.0 = deterministic, 1.0 = creative) */
        const val TEMPERATURE = 0.3f

        /** Top-p nucleus sampling */
        const val TOP_P = 0.9f

        /** Maximum tokens in response */
        const val MAX_TOKENS = 1024

        /** Request timeout in milliseconds */
        const val TIMEOUT_MS = 30_000L
    }

    /**
     * HTTP headers required for BigModel API.
     */
    object Headers {
        const val AUTHORIZATION = "Authorization"
        const val CONTENT_TYPE = "Content-Type"
        const val CONTENT_TYPE_JSON = "application/json"
    }

    /**
     * Formats the authorization header value.
     *
     * @param apiKey The BigModel API key
     * @return Formatted authorization header value
     */
    fun getAuthorizationHeader(apiKey: String): String {
        return "Bearer $apiKey"
    }

    /**
     * Gets the full URL for chat completions.
     *
     * @return Full endpoint URL
     */
    fun getChatCompletionsUrl(): String {
        return "$BASE_URL$CHAT_COMPLETIONS_ENDPOINT"
    }

    /**
     * Validates a BigModel API key format.
     *
     * @param apiKey The API key to validate
     * @return true if the key appears valid
     */
    fun isValidApiKey(apiKey: String?): Boolean {
        if (apiKey.isNullOrBlank()) return false
        // BigModel API keys typically start with specific prefixes
        // Basic validation: non-empty and reasonable length
        return apiKey.length >= 20
    }

    /**
     * Configuration for image encoding in requests.
     */
    object ImageConfig {
        /** Maximum image size in bytes (5MB) */
        const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024

        /** Recommended image quality for screenshots */
        const val SCREENSHOT_QUALITY = 85

        /** Maximum image dimension (width or height) */
        const val MAX_DIMENSION = 2048

        /** Supported image formats */
        val SUPPORTED_FORMATS = setOf("png", "jpg", "jpeg", "webp")
    }

    /**
     * Error codes specific to BigModel API.
     */
    object ErrorCodes {
        const val INVALID_API_KEY = "invalid_api_key"
        const val RATE_LIMIT_EXCEEDED = "rate_limit_exceeded"
        const val MODEL_NOT_FOUND = "model_not_found"
        const val INVALID_REQUEST = "invalid_request"
        const val SERVER_ERROR = "server_error"
    }

    /**
     * Rate limiting configuration.
     */
    object RateLimits {
        /** Maximum requests per minute (free tier) */
        const val FREE_TIER_RPM = 60

        /** Maximum requests per minute (paid tier) */
        const val PAID_TIER_RPM = 300

        /** Maximum tokens per minute */
        const val MAX_TOKENS_PER_MINUTE = 100_000
    }
}
