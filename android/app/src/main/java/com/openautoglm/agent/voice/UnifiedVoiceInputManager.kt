package com.openautoglm.agent.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Factory for creating VoiceInputManager instances based on the selected recognizer type.
 *
 * This allows switching between:
 * - Android's built-in SpeechRecognizer (online, uses Google)
 * - Vosk offline speech recognition (fully on-device)
 */
object VoiceInputManagerFactory {

    private const val TAG = "VoiceInputFactory"

    /**
     * Create a VoiceInputManager based on the specified type.
     *
     * @param context Application context
     * @param type The type of speech recognizer to use
     * @param config Voice input configuration
     * @param language Language code for error messages
     * @return VoiceInputManager instance
     */
    fun create(
        context: Context,
        type: SpeechRecognizerType,
        config: VoiceInputConfig = VoiceInputConfig(),
        language: String = "zh"
    ): VoiceInputManager {
        return when (type) {
            SpeechRecognizerType.ANDROID_BUILTIN -> {
                Log.d(TAG, "Creating Android built-in SpeechRecognizer")
                VoiceInputManagerImpl(context, config, language)
            }
            SpeechRecognizerType.VOSK_OFFLINE -> {
                Log.d(TAG, "Creating Vosk offline SpeechRecognizer")
                VoskSpeechRecognizer(context, config, language)
            }
            SpeechRecognizerType.WHISPER_OFFLINE -> {
                Log.d(TAG, "Creating Whisper.cpp offline SpeechRecognizer")
                WhisperSpeechRecognizer(context, config, language)
            }
        }
    }

    /**
     * Check if Vosk is available (model downloaded) for the given locale.
     */
    fun isVoskAvailable(context: Context, locale: Locale): Boolean {
        return VoskSpeechRecognizer.isModelDownloaded(context, locale)
    }

    /**
     * Check if Whisper is available (any model downloaded).
     */
    fun isWhisperAvailable(context: Context, modelName: String = WhisperSpeechRecognizer.MODEL_NAME_TINY): Boolean {
        // Check if any Whisper model is available, not just a specific one
        return WhisperSpeechRecognizer.isAnyModelAvailable(context)
    }

    /**
     * Get the recommended recognizer type based on availability.
     * Prefers Vosk if model is downloaded, otherwise falls back to Android.
     */
    fun getRecommendedType(context: Context, locale: Locale): SpeechRecognizerType {
        return if (isVoskAvailable(context, locale)) {
            SpeechRecognizerType.VOSK_OFFLINE
        } else {
            SpeechRecognizerType.ANDROID_BUILTIN
        }
    }
}

/**
 * Unified voice input manager that can dynamically switch between recognizer types.
 *
 * @param context Application context
 * @param initialType Initial recognizer type
 * @param config Voice input configuration
 * @param language Language code for error messages
 */
class UnifiedVoiceInputManager(
    private val context: Context,
    initialType: SpeechRecognizerType = SpeechRecognizerType.ANDROID_BUILTIN,
    private var config: VoiceInputConfig = VoiceInputConfig(),
    private var language: String = "zh"
) : VoiceInputManager {

    private companion object {
        const val TAG = "UnifiedVoiceInput"
    }

    private var currentType: SpeechRecognizerType = initialType
    private var delegate: VoiceInputManager = createDelegate(initialType)

    override val state: StateFlow<VoiceInputState>
        get() = delegate.state

    override val isAvailable: Boolean
        get() = delegate.isAvailable

    /**
     * Get the current recognizer type.
     */
    fun getCurrentType(): SpeechRecognizerType = currentType

    /**
     * Switch to a different recognizer type.
     * The current recognizer will be destroyed and a new one created.
     *
     * @param type The new recognizer type
     * @return true if switch was successful
     */
    fun switchRecognizer(type: SpeechRecognizerType): Boolean {
        if (type == currentType) {
            Log.d(TAG, "Already using $type, no switch needed")
            return true
        }

        // Check if Vosk is available when switching to it
        if (type == SpeechRecognizerType.VOSK_OFFLINE && !VoiceInputManagerFactory.isVoskAvailable(context, config.locale)) {
            Log.w(TAG, "Cannot switch to Vosk - model not downloaded for ${config.locale}")
            return false
        }

        // Check if Whisper is available when switching to it
        if (type == SpeechRecognizerType.WHISPER_OFFLINE && !VoiceInputManagerFactory.isWhisperAvailable(context)) {
            Log.w(TAG, "Cannot switch to Whisper - model not downloaded")
            return false
        }

        Log.i(TAG, "Switching from $currentType to $type")

        // Destroy current delegate
        delegate.destroy()

        // Create new delegate
        currentType = type
        delegate = createDelegate(type)

        return true
    }

    /**
     * Update the voice input configuration.
     */
    fun updateConfig(newConfig: VoiceInputConfig) {
        config = newConfig
        // Recreate delegate with new config
        delegate.destroy()
        delegate = createDelegate(currentType)
    }

    override fun startListening(locale: Locale) {
        delegate.startListening(locale)
    }

    override fun stopListening() {
        delegate.stopListening()
    }

    override fun cancelListening() {
        delegate.cancelListening()
    }

    override fun destroy() {
        delegate.destroy()
    }

    private fun createDelegate(type: SpeechRecognizerType): VoiceInputManager {
        return VoiceInputManagerFactory.create(context, type, config, language)
    }
}
