package com.openautoglm.agent.voice

import android.speech.SpeechRecognizer

/**
 * Voice recognition error codes with associated metadata and bilingual messages.
 * Wraps Android SpeechRecognizer error codes with additional context.
 */
enum class VoiceErrorCode(
    val androidCode: Int,
    val isRetryable: Boolean,
    val messageEn: String,
    val messageZh: String
) {
    NETWORK_TIMEOUT(
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        true,
        "Network timeout. Please try again.",
        "网络超时，请重试。"
    ),
    NETWORK_ERROR(
        SpeechRecognizer.ERROR_NETWORK,
        true,
        "Network error. Check your connection.",
        "网络错误，请检查网络连接。"
    ),
    AUDIO_ERROR(
        SpeechRecognizer.ERROR_AUDIO,
        false,
        "Microphone error. Please check your device.",
        "麦克风错误，请检查设备。"
    ),
    SERVER_ERROR(
        SpeechRecognizer.ERROR_SERVER,
        true,
        "Server error. Please try again.",
        "服务器错误，请重试。"
    ),
    CLIENT_ERROR(
        SpeechRecognizer.ERROR_CLIENT,
        true,
        "Recognition error. Please try again.",
        "识别错误，请重试。"
    ),
    SPEECH_TIMEOUT(
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        true,
        "No speech detected. Please speak into the microphone.",
        "未检测到语音，请对着麦克风说话。"
    ),
    NO_MATCH(
        SpeechRecognizer.ERROR_NO_MATCH,
        true,
        "Could not recognize speech. Please try again.",
        "无法识别语音，请重试。"
    ),
    RECOGNIZER_BUSY(
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        true,
        "Voice recognition is busy. Please wait.",
        "语音识别繁忙，请稍候。"
    ),
    INSUFFICIENT_PERMISSIONS(
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        false,
        "Microphone permission required.",
        "需要麦克风权限。"
    ),
    TOO_MANY_REQUESTS(
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
        true,
        "Too many requests. Please wait.",
        "请求过于频繁，请稍候。"
    ),
    LANGUAGE_NOT_SUPPORTED(
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        false,
        "Language not supported for voice input.",
        "不支持当前语言的语音输入。"
    ),
    LANGUAGE_UNAVAILABLE(
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        false,
        "Language pack unavailable. Please download offline language.",
        "语言包不可用，请下载离线语言包。"
    ),
    UNKNOWN(
        -1,
        true,
        "Unknown error. Please try again.",
        "未知错误，请重试。"
    );

    companion object {
        /**
         * Convert Android SpeechRecognizer error code to VoiceErrorCode.
         */
        fun fromAndroidCode(code: Int): VoiceErrorCode {
            return values().find { it.androidCode == code } ?: UNKNOWN
        }
    }

    /**
     * Get localized error message.
     * @param language Language code ("zh" for Chinese, anything else for English)
     */
    fun getMessage(language: String): String {
        return if (language.startsWith("zh")) messageZh else messageEn
    }
}
