package com.openautoglm.agent.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Whisper.cpp-based speech recognizer for high-accuracy offline speech recognition.
 *
 * Features:
 * - High-accuracy multilingual recognition (especially good for Chinese)
 * - Fully offline recognition (no network required)
 * - Based on OpenAI's Whisper model (ggml format)
 * - Supports tiny (~75MB), base (~142MB), small (~466MB) models
 *
 * Note: Unlike streaming recognizers (Android/Vosk), Whisper processes complete
 * audio after recording stops, which may result in slightly longer processing time.
 *
 * @param context Application context
 * @param config Voice input configuration
 * @param language Language code for error messages ("en" or "zh")
 */
class WhisperSpeechRecognizer(
    private val context: Context,
    private val config: VoiceInputConfig = VoiceInputConfig(),
    private val language: String = "zh"
) : VoiceInputManager {

    companion object {
        private const val TAG = "WhisperRecognizer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Whisper model information (using ggml-tiny for mobile)
        const val MODEL_NAME_TINY = "ggml-tiny.bin"
        const val MODEL_NAME_BASE = "ggml-base.bin"
        const val MODEL_NAME_SMALL = "ggml-small.bin"

        // Model download URLs (from Hugging Face)
        const val MODEL_URL_TINY = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
        const val MODEL_URL_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
        const val MODEL_URL_SMALL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"

        // Model sizes in MB
        const val MODEL_SIZE_TINY_MB = 75
        const val MODEL_SIZE_BASE_MB = 142
        const val MODEL_SIZE_SMALL_MB = 466

        /**
         * Get model directory for Whisper models.
         */
        fun getModelDir(context: Context): File {
            return File(context.filesDir, "whisper-models")
        }

        /**
         * Get path to a specific model file.
         */
        fun getModelPath(context: Context, modelName: String): File {
            return File(getModelDir(context), modelName)
        }

        /**
         * Check if a model is downloaded.
         */
        fun isModelDownloaded(context: Context, modelName: String = MODEL_NAME_TINY): Boolean {
            val modelFile = getModelPath(context, modelName)
            return modelFile.exists() && modelFile.length() > 0
        }

        /**
         * Get default model name based on device capabilities.
         * Uses tiny model for most devices to balance accuracy and speed.
         */
        fun getDefaultModelName(): String = MODEL_NAME_TINY

        /**
         * Get the download URL for a model.
         */
        fun getModelUrl(modelName: String): String {
            return when (modelName) {
                MODEL_NAME_TINY -> MODEL_URL_TINY
                MODEL_NAME_BASE -> MODEL_URL_BASE
                MODEL_NAME_SMALL -> MODEL_URL_SMALL
                else -> MODEL_URL_TINY
            }
        }

        /**
         * Get the model size in MB.
         */
        fun getModelSizeMB(modelName: String): Int {
            return when (modelName) {
                MODEL_NAME_TINY -> MODEL_SIZE_TINY_MB
                MODEL_NAME_BASE -> MODEL_SIZE_BASE_MB
                MODEL_NAME_SMALL -> MODEL_SIZE_SMALL_MB
                else -> MODEL_SIZE_TINY_MB
            }
        }
    }

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    override val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var whisperContext: WhisperContext? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.Main)

    // Audio buffer for recording
    private val audioBuffer = mutableListOf<Float>()
    private var lastPartialText: String = ""

    // Model name to use
    private var modelName: String = getDefaultModelName()

    override val isAvailable: Boolean
        get() = isModelDownloaded(context, modelName)

    /**
     * Set the model to use for recognition.
     * Must be called before startListening if you want to use a different model.
     */
    fun setModel(name: String) {
        if (name != modelName) {
            modelName = name
            // Reset context to force reload with new model
            val oldContext = whisperContext
            whisperContext = null
            if (oldContext != null) {
                scope.launch {
                    oldContext.release()
                }
            }
        }
    }

    /**
     * Initialize the Whisper model asynchronously.
     * Call this before starting recognition.
     *
     * @param onComplete Callback when initialization completes (success: Boolean)
     */
    fun initializeModel(onComplete: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val modelFile = getModelPath(context, modelName)

                if (!modelFile.exists()) {
                    Log.e(TAG, "Model not found at: ${modelFile.absolutePath}")
                    _state.value = VoiceInputState.Error(
                        message = if (language.startsWith("zh"))
                            "语音模型未下载，请先下载Whisper模型"
                        else
                            "Voice model not downloaded. Please download Whisper model first.",
                        errorCode = -1,
                        isRetryable = false
                    )
                    onComplete(false)
                    return@launch
                }

                _state.value = VoiceInputState.Processing

                withContext(Dispatchers.IO) {
                    whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
                }

                Log.i(TAG, "Whisper model loaded successfully: ${modelFile.absolutePath}")
                _state.value = VoiceInputState.Idle
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Whisper model", e)
                _state.value = VoiceInputState.Error(
                    message = if (language.startsWith("zh"))
                        "加载Whisper模型失败: ${e.message}"
                    else
                        "Failed to load Whisper model: ${e.message}",
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

        // Check audio permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh"))
                    "需要录音权限"
                else
                    "Recording permission required",
                errorCode = -1,
                isRetryable = false
            )
            return
        }

        // Check if model is loaded
        if (whisperContext == null) {
            Log.d(TAG, "Model not loaded, initializing...")
            _state.value = VoiceInputState.Processing
            initializeModel { success ->
                if (success) {
                    startRecording()
                }
            }
            return
        }

        startRecording()
    }

    private fun startRecording() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(SAMPLE_RATE * 2) // At least 1 second buffer

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                _state.value = VoiceInputState.Error(
                    message = if (language.startsWith("zh"))
                        "录音初始化失败"
                    else
                        "Failed to initialize audio recording",
                    errorCode = -1,
                    isRetryable = true
                )
                return
            }

            audioBuffer.clear()
            lastPartialText = ""
            audioRecord?.startRecording()
            isListening = true
            _state.value = VoiceInputState.Listening()

            Log.d(TAG, "Started audio recording for Whisper")

            // Start recording in background
            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2)
                var silentFrames = 0
                val silenceThreshold = 500 // Amplitude threshold for silence
                val maxSilentFrames = (SAMPLE_RATE * config.silenceTimeoutMs / 1000 / buffer.size).toInt()

                while (isActive && isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                    if (read > 0) {
                        // Convert to float array (normalized -1 to 1)
                        val floatBuffer = FloatArray(read)
                        var maxAmplitude = 0
                        for (i in 0 until read) {
                            floatBuffer[i] = buffer[i] / 32768.0f
                            maxAmplitude = maxOf(maxAmplitude, abs(buffer[i].toInt()))
                        }

                        synchronized(audioBuffer) {
                            audioBuffer.addAll(floatBuffer.toList())
                        }

                        // Calculate normalized sound level for UI
                        val normalizedLevel = (maxAmplitude / 32768.0f).coerceIn(0f, 1f)

                        withContext(Dispatchers.Main) {
                            val currentState = _state.value
                            if (currentState is VoiceInputState.Listening) {
                                _state.value = currentState.copy(soundLevel = normalizedLevel)
                            }
                        }

                        // Check for silence timeout
                        if (maxAmplitude < silenceThreshold) {
                            silentFrames++
                            if (silentFrames >= maxSilentFrames && audioBuffer.size > SAMPLE_RATE) {
                                Log.d(TAG, "Silence detected, stopping recording")
                                withContext(Dispatchers.Main) {
                                    stopListening()
                                }
                                break
                            }
                        } else {
                            silentFrames = 0
                        }

                        // Enforce max duration
                        val recordedSeconds = audioBuffer.size / SAMPLE_RATE
                        if (recordedSeconds >= config.maxDurationMs / 1000) {
                            Log.d(TAG, "Max duration reached, stopping recording")
                            withContext(Dispatchers.Main) {
                                stopListening()
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh"))
                    "启动录音失败: ${e.message}"
                else
                    "Failed to start recording: ${e.message}",
                errorCode = -1,
                isRetryable = true
            )
        }
    }

    override fun stopListening() {
        if (!isListening) return

        isListening = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        _state.value = VoiceInputState.Processing

        // Process the recorded audio
        scope.launch {
            transcribeAudio()
        }
    }

    private suspend fun transcribeAudio() {
        val context = whisperContext
        if (context == null) {
            Log.e(TAG, "Whisper context is null")
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh"))
                    "语音模型未加载"
                else
                    "Voice model not loaded",
                errorCode = -1,
                isRetryable = true
            )
            return
        }

        val audioData: FloatArray
        synchronized(audioBuffer) {
            if (audioBuffer.isEmpty()) {
                Log.w(TAG, "No audio data recorded")
                _state.value = VoiceInputState.Error(
                    message = if (language.startsWith("zh"))
                        "未检测到语音"
                    else
                        "No speech detected",
                    errorCode = -1,
                    isRetryable = true
                )
                return
            }
            audioData = audioBuffer.toFloatArray()
            audioBuffer.clear()
        }

        Log.d(TAG, "Transcribing ${audioData.size} samples (${audioData.size / SAMPLE_RATE.toFloat()}s)")

        try {
            val result = withContext(Dispatchers.IO) {
                context.transcribeData(audioData)
            }

            val text = result.trim()
            Log.i(TAG, "Transcription result: '$text'")

            if (text.isNotEmpty()) {
                _state.value = VoiceInputState.Result(
                    transcription = text,
                    confidence = null // Whisper doesn't provide per-word confidence
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
            Log.e(TAG, "Transcription failed", e)
            _state.value = VoiceInputState.Error(
                message = if (language.startsWith("zh"))
                    "语音识别失败: ${e.message}"
                else
                    "Transcription failed: ${e.message}",
                errorCode = -1,
                isRetryable = true
            )
        }
    }

    override fun cancelListening() {
        if (isListening) {
            isListening = false
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioBuffer.clear()
            _state.value = VoiceInputState.Idle
        }
    }

    override fun destroy() {
        cancelListening()
        val oldContext = whisperContext
        whisperContext = null
        if (oldContext != null) {
            scope.launch {
                oldContext.release()
            }
        }
        Log.d(TAG, "WhisperSpeechRecognizer destroyed")
    }
}
