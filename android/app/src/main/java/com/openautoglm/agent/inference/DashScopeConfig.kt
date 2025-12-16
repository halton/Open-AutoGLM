package com.openautoglm.agent.inference

/**
 * Configuration for DashScope (Alibaba Cloud) API integration.
 *
 * DashScope provides the Qwen2.5-VL-72B model for advanced vision-language tasks
 * requiring high accuracy and complex reasoning.
 *
 * API Documentation: https://help.aliyun.com/zh/dashscope/
 */
object DashScopeConfig {

    /**
     * Base URL for DashScope API.
     */
    const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/"

    /**
     * Chat completions endpoint (OpenAI-compatible).
     */
    const val CHAT_COMPLETIONS_ENDPOINT = "chat/completions"

    /**
     * Default model ID for vision-language tasks.
     * Qwen2.5-VL-72B provides state-of-the-art VLM capabilities.
     */
    const val DEFAULT_MODEL = "qwen2.5-vl-72b-instruct"

    /**
     * Supported Qwen VL models.
     */
    object Models {
        /** 72B parameter model - highest accuracy */
        const val QWEN_VL_72B = "qwen2.5-vl-72b-instruct"

        /** 7B parameter model - balanced performance */
        const val QWEN_VL_7B = "qwen2.5-vl-7b-instruct"

        /** 3B parameter model - fast inference */
        const val QWEN_VL_3B = "qwen2.5-vl-3b-instruct"

        /** Alternative naming for 72B model */
        const val QWEN_VL_MAX = "qwen-vl-max"

        /** Plus version with enhanced features */
        const val QWEN_VL_PLUS = "qwen-vl-plus"
    }

    /**
     * Default inference parameters for VLM tasks.
     */
    object DefaultParams {
        /** Temperature for sampling */
        const val TEMPERATURE = 0.3f

        /** Top-p nucleus sampling */
        const val TOP_P = 0.9f

        /** Maximum tokens in response */
        const val MAX_TOKENS = 2048

        /** Request timeout in milliseconds */
        const val TIMEOUT_MS = 45_000L  // Longer timeout for 72B model

        /** Enable search augmentation (if available) */
        const val ENABLE_SEARCH = false

        /** Seed for reproducible results */
        val SEED: Int? = null
    }

    /**
     * HTTP headers required for DashScope API.
     */
    object Headers {
        const val AUTHORIZATION = "Authorization"
        const val CONTENT_TYPE = "Content-Type"
        const val CONTENT_TYPE_JSON = "application/json"
        const val X_DASHSCOPE_SSE = "X-DashScope-SSE"
    }

    /**
     * Formats the authorization header value.
     *
     * @param apiKey The DashScope API key
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
     * Validates a DashScope API key format.
     *
     * @param apiKey The API key to validate
     * @return true if the key appears valid
     */
    fun isValidApiKey(apiKey: String?): Boolean {
        if (apiKey.isNullOrBlank()) return false
        // DashScope API keys start with "sk-"
        return apiKey.startsWith("sk-") && apiKey.length >= 30
    }

    /**
     * Configuration for image encoding in requests.
     */
    object ImageConfig {
        /** Maximum image size in bytes (10MB for DashScope) */
        const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

        /** Recommended image quality for screenshots */
        const val SCREENSHOT_QUALITY = 90  // Higher quality for better VLM understanding

        /** Maximum image dimension (width or height) */
        const val MAX_DIMENSION = 4096

        /** Supported image formats */
        val SUPPORTED_FORMATS = setOf("png", "jpg", "jpeg", "webp", "gif")

        /** Maximum number of images per request */
        const val MAX_IMAGES_PER_REQUEST = 10
    }

    /**
     * Error codes specific to DashScope API.
     */
    object ErrorCodes {
        const val INVALID_API_KEY = "InvalidApiKey"
        const val RATE_LIMIT_EXCEEDED = "Throttling.RateQuota"
        const val MODEL_NOT_FOUND = "ModelNotFound"
        const val INVALID_REQUEST = "InvalidParameter"
        const val SERVER_ERROR = "InternalError"
        const val QUOTA_EXCEEDED = "QuotaExceeded"
    }

    /**
     * Rate limiting configuration.
     */
    object RateLimits {
        /** Maximum requests per minute (free tier) */
        const val FREE_TIER_RPM = 30

        /** Maximum requests per minute (standard tier) */
        const val STANDARD_TIER_RPM = 120

        /** Maximum requests per minute (enterprise tier) */
        const val ENTERPRISE_TIER_RPM = 600

        /** Maximum tokens per minute */
        const val MAX_TOKENS_PER_MINUTE = 300_000
    }

    /**
     * Model capabilities and recommended use cases.
     */
    object ModelCapabilities {
        /** Use cases best suited for 72B model */
        val QWEN_VL_72B_USE_CASES = listOf(
            "Complex multi-step reasoning",
            "Fine-grained visual understanding",
            "Cross-app task orchestration",
            "Price comparison and analysis",
            "Document understanding and extraction"
        )

        /** Use cases best suited for 7B model */
        val QWEN_VL_7B_USE_CASES = listOf(
            "Standard UI automation",
            "Simple visual Q&A",
            "Basic text extraction",
            "Navigation tasks"
        )

        /** Use cases best suited for 3B model */
        val QWEN_VL_3B_USE_CASES = listOf(
            "Fast UI element detection",
            "Simple button/icon recognition",
            "Quick screenshot analysis"
        )
    }

    /**
     * Recommends a Qwen model based on task complexity.
     *
     * @param complexityScore Task complexity score (0-100)
     * @return Recommended model ID
     */
    fun recommendModel(complexityScore: Int): String {
        return when {
            complexityScore >= 70 -> Models.QWEN_VL_72B
            complexityScore >= 40 -> Models.QWEN_VL_7B
            else -> Models.QWEN_VL_3B
        }
    }
}
