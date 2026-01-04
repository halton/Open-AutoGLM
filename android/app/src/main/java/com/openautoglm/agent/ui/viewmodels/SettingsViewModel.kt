package com.openautoglm.agent.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.inference.CloudInference
import com.openautoglm.agent.inference.InferenceProvider
import com.openautoglm.agent.inference.ModelDownloadManager
import com.openautoglm.agent.inference.ModelInfo
import com.openautoglm.agent.inference.SecureKeyStorage
import com.openautoglm.agent.voice.SpeechRecognizerType
import com.openautoglm.agent.voice.VoskModelDownloadManager
import com.openautoglm.agent.voice.VoskModelDownloadState
import com.openautoglm.agent.voice.VoskModelInfo
import com.openautoglm.agent.voice.WhisperModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ViewModel for the Settings screen.
 *
 * Manages API key configuration, provider selection, and model settings.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStorage = SecureKeyStorage(application)
    private val cloudInference = CloudInference(application)
    private val downloadManager = ModelDownloadManager(application)
    private val voskModelManager = VoskModelDownloadManager(application)

    // UI State
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Vosk download state
    val voskDownloadState: StateFlow<VoskModelDownloadState> = voskModelManager.downloadState

    init {
        loadCurrentSettings()
        loadDownloadedModels()
        loadVoskModels()
        loadWhisperModels()
    }

    /**
     * Loads current settings from secure storage.
     */
    private fun loadCurrentSettings() {
        val selectedProvider = secureStorage.getSelectedProvider()
        val inferenceMode = secureStorage.getInferenceMode()

        // Configuration is complete if:
        // 1. ON_DEVICE mode with downloaded models, OR
        // 2. CLOUD/AUTO mode with API key configured
        val hasDownloadedModels = downloadManager.getDownloadedModels().isNotEmpty()
        val hasCloudApiKey = secureStorage.hasSelectedProviderApiKey()
        val isConfigured = when (inferenceMode) {
            InferenceMode.ON_DEVICE -> hasDownloadedModels
            InferenceMode.CLOUD -> hasCloudApiKey
            InferenceMode.AUTO -> hasCloudApiKey || hasDownloadedModels
        }

        // Load speech recognizer type
        val speechRecognizerTypeName = secureStorage.getSpeechRecognizerType()
        val speechRecognizerType = try {
            SpeechRecognizerType.valueOf(speechRecognizerTypeName)
        } catch (e: IllegalArgumentException) {
            SpeechRecognizerType.ANDROID_BUILTIN
        }

        _uiState.value = SettingsUiState(
            selectedProvider = selectedProvider,
            bigModelApiKey = secureStorage.getBigModelApiKey() ?: "",
            bigModelModelId = secureStorage.getBigModelModelId(),
            dashScopeApiKey = secureStorage.getDashScopeApiKey() ?: "",
            dashScopeModelId = secureStorage.getDashScopeModelId(),
            openAiApiKey = secureStorage.getOpenAIApiKey() ?: "",
            customApiKey = secureStorage.getCustomApiKey() ?: "",
            customBaseUrl = secureStorage.getCustomApiBaseUrl() ?: "",
            isConfigured = isConfigured,
            inferenceMode = inferenceMode,
            selectedOnDeviceModel = secureStorage.getSelectedOnDeviceModel(),
            huggingFaceToken = secureStorage.getHuggingFaceToken() ?: "",
            speechRecognizerType = speechRecognizerType
        )
    }

    /**
     * Loads downloaded models for selection.
     */
    private fun loadDownloadedModels() {
        val downloadedModels = downloadManager.getDownloadedModels()
        _uiState.value = _uiState.value.copy(
            downloadedModels = downloadedModels
        )
    }

    /**
     * Loads Vosk model information.
     */
    private fun loadVoskModels() {
        val availableVoskModels = VoskModelDownloadManager.AVAILABLE_MODELS
        val downloadedVoskModels = voskModelManager.getDownloadedModels()

        _uiState.value = _uiState.value.copy(
            availableVoskModels = availableVoskModels,
            downloadedVoskModels = downloadedVoskModels
        )
    }

    /**
     * Loads Whisper model information.
     */
    private fun loadWhisperModels() {
        // Get downloaded Whisper models from the main ModelDownloadManager
        // (Whisper models are downloaded via ModelDownloadScreen, not separately)
        val downloadedWhisperModels = downloadManager.getWhisperModels()
            .filter { downloadManager.isModelDownloaded(it.id) }
            .map { modelInfo ->
                // Convert ModelInfo to WhisperModelInfo for the UI
                WhisperModelInfo(
                    id = modelInfo.fileName, // Use filename as ID (e.g., "ggml-tiny.bin")
                    name = modelInfo.displayName,
                    url = modelInfo.downloadUrl,
                    sizeMB = modelInfo.sizeMB.toInt(),
                    description = modelInfo.description,
                    accuracy = when {
                        modelInfo.id.contains("tiny") -> "~70% WER"
                        modelInfo.id.contains("base") -> "~60% WER"
                        modelInfo.id.contains("small") -> "~45% WER"
                        else -> ""
                    }
                )
            }

        _uiState.value = _uiState.value.copy(
            downloadedWhisperModels = downloadedWhisperModels
        )
    }

    /**
     * Updates the selected provider.
     */
    fun selectProvider(provider: InferenceProvider) {
        _uiState.value = _uiState.value.copy(selectedProvider = provider)
    }

    /**
     * Updates BigModel API key.
     */
    fun updateBigModelApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(bigModelApiKey = apiKey)
    }

    /**
     * Updates BigModel model ID.
     */
    fun updateBigModelModelId(modelId: String) {
        _uiState.value = _uiState.value.copy(bigModelModelId = modelId)
    }

    /**
     * Updates DashScope API key.
     */
    fun updateDashScopeApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(dashScopeApiKey = apiKey)
    }

    /**
     * Updates DashScope model ID.
     */
    fun updateDashScopeModelId(modelId: String) {
        _uiState.value = _uiState.value.copy(dashScopeModelId = modelId)
    }

    /**
     * Updates OpenAI API key.
     */
    fun updateOpenAiApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(openAiApiKey = apiKey)
    }

    /**
     * Updates custom API key.
     */
    fun updateCustomApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(customApiKey = apiKey)
    }

    /**
     * Updates custom base URL.
     */
    fun updateCustomBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(customBaseUrl = url)
    }

    /**
     * Updates the inference mode (Cloud, On-Device, or Auto).
     */
    fun updateInferenceMode(mode: InferenceMode) {
        val state = _uiState.value
        val hasDownloadedModels = state.downloadedModels.isNotEmpty()
        val hasCloudApiKey = secureStorage.hasSelectedProviderApiKey()

        // Recalculate isConfigured based on new mode
        val isConfigured = when (mode) {
            InferenceMode.ON_DEVICE -> hasDownloadedModels
            InferenceMode.CLOUD -> hasCloudApiKey
            InferenceMode.AUTO -> hasCloudApiKey || hasDownloadedModels
        }

        _uiState.value = state.copy(inferenceMode = mode, isConfigured = isConfigured)
    }

    /**
     * Updates the selected on-device model.
     */
    fun updateSelectedOnDeviceModel(modelId: String?) {
        _uiState.value = _uiState.value.copy(selectedOnDeviceModel = modelId)
    }

    /**
     * Updates the HuggingFace token.
     */
    fun updateHuggingFaceToken(token: String) {
        _uiState.value = _uiState.value.copy(huggingFaceToken = token)
    }

    /**
     * Refreshes the downloaded models list.
     */
    fun refreshDownloadedModels() {
        loadDownloadedModels()
    }

    /**
     * Saves the current settings to secure storage.
     */
    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                saveError = null,
                saveSuccess = false
            )

            try {
                val state = _uiState.value

                // Save inference mode and on-device model selection
                secureStorage.setInferenceMode(state.inferenceMode)
                state.selectedOnDeviceModel?.let {
                    secureStorage.setSelectedOnDeviceModel(it)
                }

                // Save HuggingFace token if provided
                if (state.huggingFaceToken.isNotBlank()) {
                    secureStorage.setHuggingFaceToken(state.huggingFaceToken)
                } else {
                    secureStorage.clearHuggingFaceToken()
                }

                // Save selected provider
                secureStorage.setSelectedProvider(state.selectedProvider)

                // Save API keys and models
                // Skip API key validation if inference mode is ON_DEVICE (doesn't need cloud)
                val requireCloudApi = state.inferenceMode != InferenceMode.ON_DEVICE

                when (state.selectedProvider) {
                    InferenceProvider.BIGMODEL -> {
                        if (state.bigModelApiKey.isNotBlank()) {
                            secureStorage.setBigModelApiKey(state.bigModelApiKey)
                            secureStorage.setBigModelModelId(state.bigModelModelId)
                        } else if (requireCloudApi) {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "BigModel API key is required for Cloud/Auto mode"
                            )
                            return@launch
                        }
                    }
                    InferenceProvider.DASHSCOPE -> {
                        if (state.dashScopeApiKey.isNotBlank()) {
                            secureStorage.setDashScopeApiKey(state.dashScopeApiKey)
                            secureStorage.setDashScopeModelId(state.dashScopeModelId)
                        } else if (requireCloudApi) {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "DashScope API key is required for Cloud/Auto mode"
                            )
                            return@launch
                        }
                    }
                    InferenceProvider.OPENAI -> {
                        if (state.openAiApiKey.isNotBlank()) {
                            secureStorage.setOpenAIApiKey(state.openAiApiKey)
                        } else if (requireCloudApi) {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "OpenAI API key is required for Cloud/Auto mode"
                            )
                            return@launch
                        }
                    }
                    InferenceProvider.CUSTOM -> {
                        if (state.customApiKey.isNotBlank() && state.customBaseUrl.isNotBlank()) {
                            secureStorage.setCustomApiConfig(state.customApiKey, state.customBaseUrl)
                        } else if (requireCloudApi) {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "Custom API key and base URL are required for Cloud/Auto mode"
                            )
                            return@launch
                        }
                    }
                }

                // Recalculate isConfigured based on mode
                val hasDownloadedModels = downloadManager.getDownloadedModels().isNotEmpty()
                val hasCloudApiKey = secureStorage.hasSelectedProviderApiKey()
                val isConfigured = when (state.inferenceMode) {
                    InferenceMode.ON_DEVICE -> hasDownloadedModels
                    InferenceMode.CLOUD -> hasCloudApiKey
                    InferenceMode.AUTO -> hasCloudApiKey || hasDownloadedModels
                }

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                    isConfigured = isConfigured
                )

                // Clear success message after 3 seconds
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(saveSuccess = false)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = "Failed to save settings: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears all stored API keys.
     */
    fun clearAllKeys() {
        secureStorage.clearAllKeys()
        loadCurrentSettings()
    }

    /**
     * Clears the error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(saveError = null)
    }

    // ========== Voice Recognition Settings ==========

    /**
     * Updates the speech recognizer type.
     */
    fun updateSpeechRecognizerType(type: SpeechRecognizerType) {
        _uiState.value = _uiState.value.copy(speechRecognizerType = type)
        secureStorage.setSpeechRecognizerType(type.name)
    }

    /**
     * Downloads a Vosk model.
     */
    fun downloadVoskModel(modelInfo: VoskModelInfo) {
        viewModelScope.launch {
            val success = voskModelManager.downloadModel(modelInfo)
            if (success) {
                loadVoskModels()
            }
        }
    }

    /**
     * Deletes a Vosk model.
     */
    fun deleteVoskModel(modelInfo: VoskModelInfo) {
        viewModelScope.launch {
            val success = voskModelManager.deleteModel(modelInfo)
            if (success) {
                loadVoskModels()
                // If current type is Vosk and no models left, switch to Android
                if (_uiState.value.speechRecognizerType == SpeechRecognizerType.VOSK_OFFLINE &&
                    voskModelManager.getDownloadedModels().isEmpty()
                ) {
                    updateSpeechRecognizerType(SpeechRecognizerType.ANDROID_BUILTIN)
                }
            }
        }
    }

    /**
     * Resets Vosk download state to idle.
     */
    fun resetVoskDownloadState() {
        voskModelManager.resetState()
    }

    /**
     * Checks if Vosk is available for a locale.
     */
    fun isVoskAvailable(locale: Locale): Boolean {
        return voskModelManager.isModelDownloaded(locale)
    }

    // ========== Whisper Recognition Settings ==========
    // Note: Whisper models are now downloaded through ModelDownloadScreen (ModelDownloadManager)
    // instead of a separate WhisperModelDownloadManager. The functions below are kept for
    // compatibility but delegate to the main download manager.

    /**
     * Refreshes the Whisper models list.
     * Call this when returning from the model download screen.
     */
    fun refreshWhisperModels() {
        loadWhisperModels()
    }

    /**
     * Checks if Whisper is available (any model downloaded).
     */
    fun isWhisperAvailable(): Boolean {
        return downloadManager.getWhisperModels().any { downloadManager.isModelDownloaded(it.id) }
    }
}

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    val selectedProvider: InferenceProvider = InferenceProvider.BIGMODEL,
    val bigModelApiKey: String = "",
    val bigModelModelId: String = "AutoGLM-Phone",
    val dashScopeApiKey: String = "",
    val dashScopeModelId: String = "qwen2.5-vl-72b-instruct",
    val openAiApiKey: String = "",
    val customApiKey: String = "",
    val customBaseUrl: String = "",
    val isConfigured: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val saveError: String? = null,
    // Inference mode settings
    val inferenceMode: InferenceMode = InferenceMode.CLOUD,
    val selectedOnDeviceModel: String? = null,
    val downloadedModels: List<ModelInfo> = emptyList(),
    // HuggingFace token for gated model downloads
    val huggingFaceToken: String = "",
    // Voice recognition settings
    val speechRecognizerType: SpeechRecognizerType = SpeechRecognizerType.ANDROID_BUILTIN,
    val availableVoskModels: List<VoskModelInfo> = emptyList(),
    val downloadedVoskModels: List<VoskModelInfo> = emptyList(),
    // Whisper models (downloaded via ModelDownloadScreen)
    val downloadedWhisperModels: List<WhisperModelInfo> = emptyList()
)
