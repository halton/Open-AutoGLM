package com.openautoglm.agent.error

import android.content.Context
import com.openautoglm.agent.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Handles cloud inference errors with bilingual error messages.
 *
 * Provides user-friendly error messages in both English and Chinese
 * for common cloud API errors (T063).
 */
class InferenceErrorHandler(private val context: Context) {

    /**
     * Error type classification for cloud inference failures.
     */
    enum class ErrorType {
        NETWORK_ERROR,
        TIMEOUT,
        AUTH_ERROR,
        RATE_LIMIT,
        INVALID_RESPONSE,
        SERVER_ERROR,
        SSL_ERROR,
        UNKNOWN
    }

    /**
     * Error result with bilingual messages and recovery suggestions.
     */
    data class ErrorResult(
        val type: ErrorType,
        val englishMessage: String,
        val chineseMessage: String,
        val technicalDetails: String?,
        val recoverySuggestion: String
    ) {
        fun getMessage(language: String): String {
            return when (language) {
                "zh", "zh-CN", "zh-TW" -> chineseMessage
                else -> englishMessage
            }
        }

        fun getFullMessage(language: String): String {
            val msg = getMessage(language)
            val suggestion = if (language.startsWith("zh")) {
                "建议: $recoverySuggestion"
            } else {
                "Suggestion: $recoverySuggestion"
            }
            return "$msg\n\n$suggestion"
        }
    }

    /**
     * Handle cloud inference exception and return bilingual error.
     */
    fun handleError(exception: Throwable, httpCode: Int? = null): ErrorResult {
        return when {
            // Network connectivity errors
            exception is UnknownHostException -> ErrorResult(
                type = ErrorType.NETWORK_ERROR,
                englishMessage = "Unable to reach cloud service. Please check your internet connection.",
                chineseMessage = "无法连接到云服务。请检查您的网络连接。",
                technicalDetails = exception.message,
                recoverySuggestion = "Check network settings / 检查网络设置"
            )

            // Timeout errors
            exception is SocketTimeoutException -> ErrorResult(
                type = ErrorType.TIMEOUT,
                englishMessage = "Cloud service request timed out. The service may be slow or unavailable.",
                chineseMessage = "云服务请求超时。服务可能响应缓慢或不可用。",
                technicalDetails = exception.message,
                recoverySuggestion = "Try again later or switch provider / 稍后重试或切换服务商"
            )

            // SSL/TLS errors
            exception is SSLException -> ErrorResult(
                type = ErrorType.SSL_ERROR,
                englishMessage = "Secure connection failed. This may be a certificate issue.",
                chineseMessage = "安全连接失败。可能是证书问题。",
                technicalDetails = exception.message,
                recoverySuggestion = "Check system time and date / 检查系统时间和日期"
            )

            // HTTP error codes
            httpCode == 401 || httpCode == 403 -> ErrorResult(
                type = ErrorType.AUTH_ERROR,
                englishMessage = "Authentication failed. Please check your API key in Settings.",
                chineseMessage = "身份验证失败。请在设置中检查您的 API 密钥。",
                technicalDetails = "HTTP $httpCode",
                recoverySuggestion = "Verify API key in Settings / 在设置中验证 API 密钥"
            )

            httpCode == 429 -> ErrorResult(
                type = ErrorType.RATE_LIMIT,
                englishMessage = "Rate limit exceeded. Too many requests to the cloud service.",
                chineseMessage = "超出速率限制。对云服务的请求过多。",
                technicalDetails = "HTTP $httpCode",
                recoverySuggestion = "Wait a few minutes before trying again / 等待几分钟后再试"
            )

            httpCode in 500..599 -> ErrorResult(
                type = ErrorType.SERVER_ERROR,
                englishMessage = "Cloud service is experiencing problems. This is not your fault.",
                chineseMessage = "云服务正在遇到问题。这不是您的问题。",
                technicalDetails = "HTTP $httpCode",
                recoverySuggestion = "Try again in a few minutes / 几分钟后重试"
            )

            httpCode in 400..499 -> ErrorResult(
                type = ErrorType.INVALID_RESPONSE,
                englishMessage = "Invalid request to cloud service. This may be a configuration issue.",
                chineseMessage = "云服务请求无效。可能是配置问题。",
                technicalDetails = "HTTP $httpCode: ${exception.message}",
                recoverySuggestion = "Check settings or try different provider / 检查设置或尝试其他服务商"
            )

            // Generic IO errors
            exception is IOException -> ErrorResult(
                type = ErrorType.NETWORK_ERROR,
                englishMessage = "Network error occurred while contacting cloud service.",
                chineseMessage = "联系云服务时发生网络错误。",
                technicalDetails = exception.message,
                recoverySuggestion = "Check connection and try again / 检查连接并重试"
            )

            // Catch-all for unknown errors
            else -> ErrorResult(
                type = ErrorType.UNKNOWN,
                englishMessage = "An unexpected error occurred: ${exception.javaClass.simpleName}",
                chineseMessage = "发生意外错误: ${exception.javaClass.simpleName}",
                technicalDetails = exception.message,
                recoverySuggestion = "Restart app or contact support / 重启应用或联系支持"
            )
        }
    }

    /**
     * Handle malformed VLM response (missing tags, invalid JSON, etc.)
     */
    fun handleMalformedResponse(response: String, reason: String): ErrorResult {
        return ErrorResult(
            type = ErrorType.INVALID_RESPONSE,
            englishMessage = "Cloud service returned an invalid response format.",
            chineseMessage = "云服务返回的响应格式无效。",
            technicalDetails = "Reason: $reason\nResponse preview: ${response.take(200)}",
            recoverySuggestion = "Try again or switch to different model / 重试或切换到其他模型"
        )
    }

    /**
     * Handle empty or null response from cloud service.
     */
    fun handleEmptyResponse(): ErrorResult {
        return ErrorResult(
            type = ErrorType.INVALID_RESPONSE,
            englishMessage = "Cloud service returned an empty response.",
            chineseMessage = "云服务返回了空响应。",
            technicalDetails = "Response was null or empty",
            recoverySuggestion = "Retry the request / 重试请求"
        )
    }

    /**
     * Handle quota exceeded errors (different from rate limit).
     */
    fun handleQuotaExceeded(): ErrorResult {
        return ErrorResult(
            type = ErrorType.RATE_LIMIT,
            englishMessage = "API quota exceeded. You have used up your allocation for this service.",
            chineseMessage = "API 配额已用尽。您已用完此服务的分配额度。",
            technicalDetails = null,
            recoverySuggestion = "Switch provider or upgrade quota / 切换服务商或升级配额"
        )
    }

    /**
     * Handle model-specific errors (model not found, invalid parameters).
     */
    fun handleModelError(modelName: String, details: String): ErrorResult {
        return ErrorResult(
            type = ErrorType.INVALID_RESPONSE,
            englishMessage = "Model '$modelName' error: $details",
            chineseMessage = "模型 '$modelName' 错误: $details",
            technicalDetails = details,
            recoverySuggestion = "Try different model in Settings / 在设置中尝试不同的模型"
        )
    }

    /**
     * Format error for logging with context.
     */
    fun formatForLogging(error: ErrorResult, requestContext: String? = null): String {
        return buildString {
            appendLine("=== Cloud Inference Error ===")
            appendLine("Type: ${error.type}")
            appendLine("English: ${error.englishMessage}")
            appendLine("Chinese: ${error.chineseMessage}")
            error.technicalDetails?.let {
                appendLine("Technical: $it")
            }
            appendLine("Recovery: ${error.recoverySuggestion}")
            requestContext?.let {
                appendLine("Context: $it")
            }
            appendLine("===========================")
        }
    }
}
