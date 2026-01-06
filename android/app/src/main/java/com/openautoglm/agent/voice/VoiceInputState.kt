package com.openautoglm.agent.voice

/**
 * Sealed class representing the possible states of voice input.
 * Used by UI to render appropriate feedback and controls.
 */
sealed class VoiceInputState {

    /**
     * Initial state - voice input not active.
     * UI shows mic button in normal state.
     */
    object Idle : VoiceInputState()

    /**
     * Recognizer is ready and listening for speech.
     * UI shows listening animation and partial results.
     *
     * @param partialText Current partial transcription (may be empty)
     * @param soundLevel Audio input level (0.0 to 1.0) for visualization
     */
    data class Listening(
        val partialText: String = "",
        val soundLevel: Float = 0f
    ) : VoiceInputState()

    /**
     * Speech detected, processing recognition.
     * UI shows processing indicator.
     */
    object Processing : VoiceInputState()

    /**
     * Recognition completed successfully.
     * UI shows result with edit/confirm options.
     *
     * @param transcription Final transcribed text
     * @param confidence Recognition confidence (0.0 to 1.0), if available
     */
    data class Result(
        val transcription: String,
        val confidence: Float? = null
    ) : VoiceInputState()

    /**
     * Recognition failed with error.
     * UI shows error message with retry option.
     *
     * @param message User-facing error message (localized)
     * @param errorCode Android SpeechRecognizer error code
     * @param isRetryable Whether user can retry recognition
     */
    data class Error(
        val message: String,
        val errorCode: Int,
        val isRetryable: Boolean = true
    ) : VoiceInputState()
}
