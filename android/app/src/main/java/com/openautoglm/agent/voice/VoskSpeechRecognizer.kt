package com.openautoglm.agent.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.util.Locale

/**
 * Speech recognizer type to select between Android's built-in
 * SpeechRecognizer and Vosk offline recognition.
 */
enum class SpeechRecognizerType {
    /** Android's built-in SpeechRecognizer (uses Google Speech Services online) */
    ANDROID_BUILTIN,

    /** Vosk offline speech recognition (fully on-device) */
    VOSK_OFFLINE
}

/**
 * Vosk-based speech recognizer for offline speech recognition.
 *
 * Features:
 * - Fully offline recognition (no network required)
 * - Supports Chinese (small model ~42MB)
 * - Low latency, real-time recognition
 * - Privacy-preserving (all processing on device)
 *
 * @param context Application context
 * @param config Voice input configuration
 * @param language Language code for error messages ("en" or "zh")
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val config: VoiceInputConfig = VoiceInputConfig(),
    private val language: String = "zh"
) : VoiceInputManager, RecognitionListener {

    companion object {
        private const val TAG = "VoskSpeechRecognizer"
        private const val SAMPLE_RATE = 16000.0f

        // Model information
        const val MODEL_NAME_CN = "vosk-model-small-cn-0.22"
        const val MODEL_NAME_EN = "vosk-model-small-en-us-0.15"
        const val MODEL_URL_CN = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        const val MODEL_URL_EN = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        const val MODEL_SIZE_CN_MB = 42
        const val MODEL_SIZE_EN_MB = 40

        /**
         * Get model directory for a specific language.
         */
        fun getModelDir(context: Context, locale: Locale): File {
            val modelName = if (locale.language == "zh") MODEL_NAME_CN else MODEL_NAME_EN
            return File(context.filesDir, "vosk-models/$modelName")
        }

        /**
         * Check if model for the given locale is downloaded.
         */
        fun isModelDownloaded(context: Context, locale: Locale): Boolean {
            val modelDir = getModelDir(context, locale)
            // Check for essential model files
            return modelDir.exists() &&
                File(modelDir, "am/final.mdl").exists()
        }

        /**
         * Get the download URL for a locale's model.
         */
        fun getModelUrl(locale: Locale): String {
            return if (locale.language == "zh") MODEL_URL_CN else MODEL_URL_EN
        }

        /**
         * Get the model size in MB.
         */
        fun getModelSizeMB(locale: Locale): Int {
            return if (locale.language == "zh") MODEL_SIZE_CN_MB else MODEL_SIZE_EN_MB
        }
    }

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    override val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.Main)

    // Track last partial for display
    private var lastPartialText: String = ""

    override val isAvailable: Boolean
        get() = isModelDownloaded(context, config.locale)

    /**
     * Initialize the Vosk model asynchronously.
     * Call this before starting recognition.
     *
     * @param onComplete Callback when initialization completes (success: Boolean)
     */
    fun initializeModel(locale: Locale = config.locale, onComplete: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val modelDir = getModelDir(context, locale)

                if (!modelDir.exists()) {
                    Log.e(TAG, "Model not found at: ${modelDir.absolutePath}")
                    _state.value = VoiceInputState.Error(
                        message = if (language.startsWith("zh"))
                            "语音模型未下载，请先下载模型"
                        else
                            "Voice model not downloaded. Please download first.",
                        errorCode = -1,
                        isRetryable = false
                    )
                    onComplete(false)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    model = Model(modelDir.absolutePath)
                }
                Log.i(TAG, "Vosk model loaded successfully from: ${modelDir.absolutePath}")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Vosk model", e)
                _state.value = VoiceInputState.Error(
                    message = if (language.startsWith("zh"))
                        "加载语音模型失败: ${e.message}"
                    else
                        "Failed to load voice model: ${e.message}",
                    errorCode = -1,
                    isRetryable = false
                )
                onComplete(false)
            }
        }
    }

    override fun startListening(locale: Locale) {
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring start request")
            return
        }

        // Check if model is loaded
        if (model == null) {
            Log.d(TAG, "Model not loaded, initializing...")
            _state.value = VoiceInputState.Processing
            initializeModel(locale) { success ->
                if (success) {
                    startListeningInternal()
                }
            }
            return
        }

        startListeningInternal()
    }

    private fun startListeningInternal() {
        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)

            speechService = SpeechService(recognizer, SAMPLE_RATE).apply {
                startListening(this@VoskSpeechRecognizer)
            }

            isListening = true
            lastPartialText = ""
            _state.value = VoiceInputState.Listening()
            Log.d(TAG, "Started Vosk speech recognition")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Vosk recognition", e)
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh"))
                    "启动语音识别失败: ${e.message}"
                else
                    "Failed to start speech recognition: ${e.message}",
                errorCode = -1,
                isRetryable = true
            )
        }
    }

    override fun stopListening() {
        if (isListening) {
            speechService?.stop()
            isListening = false
            _state.value = VoiceInputState.Processing
        }
    }

    override fun cancelListening() {
        if (isListening) {
            speechService?.cancel()
            speechService = null
            isListening = false
            _state.value = VoiceInputState.Idle
        }
    }

    override fun destroy() {
        cancelListening()
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
        Log.d(TAG, "VoskSpeechRecognizer destroyed")
    }

    // RecognitionListener callbacks

    override fun onPartialResult(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) return

        try {
            val json = JSONObject(hypothesis)
            val partial = json.optString("partial", "")

            if (partial.isNotEmpty() && partial != lastPartialText) {
                lastPartialText = partial
                Log.d(TAG, "Partial result: $partial")

                val currentState = _state.value
                if (currentState is VoiceInputState.Listening) {
                    _state.value = currentState.copy(partialText = partial)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse partial result: $hypothesis", e)
        }
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) return

        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "")

            if (text.isNotEmpty()) {
                Log.i(TAG, "Final result: $text")
                isListening = false

                _state.value = VoiceInputState.Result(
                    transcription = text,
                    confidence = null // Vosk doesn't provide confidence in simple mode
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse result: $hypothesis", e)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        isListening = false

        if (hypothesis.isNullOrBlank()) {
            // Use last partial if no final result
            if (lastPartialText.isNotEmpty()) {
                Log.i(TAG, "Using last partial as final: $lastPartialText")
                _state.value = VoiceInputState.Result(
                    transcription = lastPartialText,
                    confidence = null
                )
            } else {
                _state.value = VoiceInputState.Error(
                    message = if (language.startsWith("zh"))
                        "未检测到语音"
                    else
                        "No speech detected",
                    errorCode = -1,
                    isRetryable = true
                )
            }
            return
        }

        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "")

            if (text.isNotEmpty()) {
                Log.i(TAG, "Final result: $text")
                _state.value = VoiceInputState.Result(
                    transcription = text,
                    confidence = null
                )
            } else if (lastPartialText.isNotEmpty()) {
                // Fallback to last partial
                Log.i(TAG, "Empty final, using last partial: $lastPartialText")
                _state.value = VoiceInputState.Result(
                    transcription = lastPartialText,
                    confidence = null
                )
            } else {
                _state.value = VoiceInputState.Error(
                    message = if (language.startsWith("zh"))
                        "未检测到语音"
                    else
                        "No speech detected",
                    errorCode = -1,
                    isRetryable = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse final result: $hypothesis", e)
            // Use last partial as fallback
            if (lastPartialText.isNotEmpty()) {
                _state.value = VoiceInputState.Result(
                    transcription = lastPartialText,
                    confidence = null
                )
            }
        }
    }

    override fun onError(exception: Exception?) {
        isListening = false
        Log.e(TAG, "Vosk recognition error", exception)

        _state.value = VoiceInputState.Error(
            message = if (language.startsWith("zh"))
                "语音识别错误: ${exception?.message ?: "未知错误"}"
            else
                "Speech recognition error: ${exception?.message ?: "Unknown error"}",
            errorCode = -1,
            isRetryable = true
        )
    }

    override fun onTimeout() {
        isListening = false
        Log.d(TAG, "Vosk recognition timeout")

        // If we have partial results, use them
        if (lastPartialText.isNotEmpty()) {
            Log.i(TAG, "Timeout with partial: $lastPartialText")
            _state.value = VoiceInputState.Result(
                transcription = lastPartialText,
                confidence = null
            )
        } else {
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh"))
                    "语音识别超时"
                else
                    "Speech recognition timeout",
                errorCode = -1,
                isRetryable = true
            )
        }
    }
}
