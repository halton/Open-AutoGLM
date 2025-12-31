package com.openautoglm.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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

    companion object {
        private const val TAG = "VoiceInputManager"

        /**
         * Get locale from AgentConfig language setting.
         * Uses proper BCP 47 language tags for speech recognition.
         */
        fun getLocaleFromLanguage(language: String): Locale {
            return when (language) {
                "zh" -> Locale("zh", "CN")  // Simplified Chinese with region
                "zh-CN" -> Locale("zh", "CN")
                "zh-TW" -> Locale("zh", "TW")
                "en" -> Locale.US
                "en-US" -> Locale.US
                "en-GB" -> Locale.UK
                else -> Locale.getDefault()
            }
        }

        /**
         * Get the language tag string for SpeechRecognizer.
         * Android SpeechRecognizer works best with specific formats.
         */
        fun getLanguageTagForSpeechRecognizer(locale: Locale): String {
            // For Chinese, use the format that Android SpeechRecognizer expects
            return when {
                locale.language == "zh" && locale.country == "CN" -> "zh-CN"
                locale.language == "zh" && locale.country == "TW" -> "zh-TW"
                locale.language == "zh" -> "zh-CN"  // Default to Simplified Chinese
                locale.language == "en" && locale.country == "GB" -> "en-GB"
                locale.language == "en" -> "en-US"
                else -> locale.toLanguageTag()
            }
        }
    }

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    override val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    init {
        // SpeechRecognizer MUST be created on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            initializeSpeechRecognizer()
        } else {
            mainHandler.post { initializeSpeechRecognizer() }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (isAvailable) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceInputManagerImpl)
                }
                Log.d(TAG, "SpeechRecognizer initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            }
        } else {
            Log.w(TAG, "Speech recognition not available on this device")
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
        Log.d(TAG, "Starting speech recognition with locale: ${getLanguageTagForSpeechRecognizer(locale)}")

        // Ensure we run on main thread
        val startAction = {
            try {
                // Re-initialize if needed (may have been destroyed or not yet created)
                if (speechRecognizer == null) {
                    initializeSpeechRecognizer()
                }
                speechRecognizer?.startListening(intent)
                isListening = true
                _state.value = VoiceInputState.Listening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognition", e)
                _state.value = VoiceInputState.Error(
                    message = if (language.startsWith("zh")) "启动语音识别失败: ${e.message}" else "Failed to start speech recognition: ${e.message}",
                    errorCode = -1,
                    isRetryable = true
                )
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            startAction()
        } else {
            mainHandler.post(startAction)
        }
    }

    private fun createRecognizerIntent(locale: Locale): Intent {
        val languageTag = getLanguageTagForSpeechRecognizer(locale)
        Log.d(TAG, "Creating recognizer intent with language tag: $languageTag")

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Use the properly formatted language tag
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            // Also set EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE to ensure this language is used
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(languageTag))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.showPartialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxAlternatives)
            // Use Int instead of Long for silence timeout (Android expects Integer)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                config.silenceTimeoutMs.toInt()
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                config.silenceTimeoutMs.toInt()
            )
            if (config.preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    override fun stopListening() {
        runOnMainThread {
            if (isListening) {
                speechRecognizer?.stopListening()
                _state.value = VoiceInputState.Processing
            }
        }
    }

    override fun cancelListening() {
        runOnMainThread {
            if (isListening) {
                speechRecognizer?.cancel()
                isListening = false
                _state.value = VoiceInputState.Idle
            }
        }
    }

    override fun destroy() {
        runOnMainThread {
            cancelListeningInternal()
            speechRecognizer?.destroy()
            speechRecognizer = null
            Log.d(TAG, "SpeechRecognizer destroyed")
        }
    }

    private fun cancelListeningInternal() {
        if (isListening) {
            speechRecognizer?.cancel()
            isListening = false
            _state.value = VoiceInputState.Idle
        }
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
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
        Log.e(TAG, "Speech recognition error: $error (${errorCode.name})")
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
}
