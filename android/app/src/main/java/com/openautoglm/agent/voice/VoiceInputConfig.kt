package com.openautoglm.agent.voice

import java.util.Locale

/**
 * Configuration for voice input behavior.
 * Can be derived from AgentConfig or customized per session.
 */
data class VoiceInputConfig(
    /**
     * Whether voice input is enabled in the app.
     * If false, mic button is hidden.
     */
    val enabled: Boolean = true,

    /**
     * Locale for speech recognition.
     * Determines the language model used by recognizer.
     * Default: derived from AgentConfig.language
     */
    val locale: Locale = Locale.getDefault(),

    /**
     * Whether to show partial results during recognition.
     * Provides real-time feedback but may be distracting.
     */
    val showPartialResults: Boolean = true,

    /**
     * Maximum duration for a single recognition session (ms).
     * Recognition stops automatically after this duration.
     * Default: 60 seconds (typical task description length)
     */
    val maxDurationMs: Long = 60_000,

    /**
     * Silence duration before recognition stops (ms).
     * Shorter = faster response, longer = handles pauses.
     */
    val silenceTimeoutMs: Long = 1500,

    /**
     * Prefer offline recognition if available.
     * Improves privacy but may reduce accuracy.
     */
    val preferOffline: Boolean = false,

    /**
     * Maximum number of alternative results to request.
     * Usually 1 is sufficient; more may slow recognition.
     */
    val maxAlternatives: Int = 1
) {
    companion object {
        /**
         * Default configuration for Chinese language.
         */
        fun chinese() = VoiceInputConfig(
            locale = Locale.CHINESE,
            silenceTimeoutMs = 2000  // Slightly longer for tonal language
        )

        /**
         * Default configuration for English language.
         */
        fun english() = VoiceInputConfig(
            locale = Locale.US
        )

        /**
         * Create config from AgentConfig language setting.
         */
        fun fromAgentLanguage(language: String): VoiceInputConfig {
            return when (language) {
                "zh" -> chinese()
                "zh-CN" -> chinese()
                "zh-TW" -> VoiceInputConfig(locale = Locale.TRADITIONAL_CHINESE, silenceTimeoutMs = 2000)
                "en" -> english()
                "en-US" -> english()
                "en-GB" -> VoiceInputConfig(locale = Locale.UK)
                else -> VoiceInputConfig()
            }
        }
    }
}
