package com.openautoglm.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Interface for voice input management.
 * Abstracts SpeechRecognizer for easier testing and usage.
 */
interface VoiceInputManager {
    /**
     * Current state of voice input.
     */
    val state: StateFlow<VoiceInputState>

    /**
     * Whether speech recognition is available on this device.
     */
    val isAvailable: Boolean

    /**
     * Start listening for speech input.
     * @param locale Locale for speech recognition
     */
    fun startListening(locale: Locale = Locale.getDefault())

    /**
     * Stop listening and process final result.
     */
    fun stopListening()

    /**
     * Cancel listening without processing.
     */
    fun cancelListening()

    /**
     * Release resources. Call when done with voice input.
     */
    fun destroy()
}

/**
 * Implementation of VoiceInputManager using Android SpeechRecognizer.
 *
 * @param context Application context
 * @param config Voice input configuration
 * @param language Language code for error messages ("en" or "zh")
 */
class VoiceInputManagerImpl(
    private val context: Context,
    private val config: VoiceInputConfig = VoiceInputConfig(),
    private val language: String = "en"
) : VoiceInputManager, RecognitionListener {

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    override val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    init {
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (isAvailable) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceInputManagerImpl)
            }
        }
    }

    override fun startListening(locale: Locale) {
        if (!isAvailable) {
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh")) "语音识别不可用" else "Speech recognition not available",
                errorCode = -1,
                isRetryable = false
            )
            return
        }

        if (isListening) {
            cancelListening()
        }

        val intent = createRecognizerIntent(locale)

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            _state.value = VoiceInputState.Listening()
        } catch (e: Exception) {
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh")) "启动语音识别失败" else "Failed to start speech recognition",
                errorCode = -1,
                isRetryable = true
            )
        }
    }

    private fun createRecognizerIntent(locale: Locale): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.showPartialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxAlternatives)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                config.silenceTimeoutMs
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                config.silenceTimeoutMs
            )
            if (config.preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    override fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            _state.value = VoiceInputState.Processing
        }
    }

    override fun cancelListening() {
        if (isListening) {
            speechRecognizer?.cancel()
            isListening = false
            _state.value = VoiceInputState.Idle
        }
    }

    override fun destroy() {
        cancelListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // RecognitionListener callbacks

    override fun onReadyForSpeech(params: Bundle?) {
        _state.value = VoiceInputState.Listening()
    }

    override fun onBeginningOfSpeech() {
        // User started speaking - could update UI to show active speech
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Normalize RMS dB to 0-1 range for visualization
        // RMS typically ranges from -2 to 10 dB
        val normalizedLevel = ((rmsdB + 2) / 12).coerceIn(0f, 1f)

        val currentState = _state.value
        if (currentState is VoiceInputState.Listening) {
            _state.value = currentState.copy(soundLevel = normalizedLevel)
        }
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Raw audio buffer - not typically used
    }

    override fun onEndOfSpeech() {
        isListening = false
        _state.value = VoiceInputState.Processing
    }

    override fun onError(error: Int) {
        isListening = false
        val errorCode = VoiceErrorCode.fromAndroidCode(error)
        _state.value = VoiceInputState.Error(
            message = errorCode.getMessage(language),
            errorCode = error,
            isRetryable = errorCode.isRetryable
        )
    }

    override fun onResults(results: Bundle?) {
        isListening = false

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        if (!matches.isNullOrEmpty()) {
            val transcription = matches[0]
            val confidence = confidenceScores?.getOrNull(0)

            _state.value = VoiceInputState.Result(
                transcription = transcription,
                confidence = confidence
            )
        } else {
            _state.value = VoiceInputState.Error(
                message = VoiceErrorCode.NO_MATCH.getMessage(language),
                errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                isRetryable = true
            )
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        if (!matches.isNullOrEmpty()) {
            val currentState = _state.value
            if (currentState is VoiceInputState.Listening) {
                _state.value = currentState.copy(partialText = matches[0])
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Reserved for future events
    }

    companion object {
        /**
         * Get locale from AgentConfig language setting.
         */
        fun getLocaleFromLanguage(language: String): Locale {
            return when (language) {
                "zh" -> Locale.CHINESE
                "zh-CN" -> Locale.SIMPLIFIED_CHINESE
                "zh-TW" -> Locale.TRADITIONAL_CHINESE
                "en" -> Locale.US
                "en-US" -> Locale.US
                "en-GB" -> Locale.UK
                else -> Locale.getDefault()
            }
        }
    }
}
