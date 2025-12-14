package com.openautoglm.agent.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautoglm.agent.inference.CloudInference
import com.openautoglm.agent.inference.InferenceProvider
import com.openautoglm.agent.inference.SecureKeyStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Manages API key configuration, provider selection, and model settings.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStorage = SecureKeyStorage(application)
    private val cloudInference = CloudInference(application)

    // UI State
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentSettings()
    }

    /**
     * Loads current settings from secure storage.
     */
    private fun loadCurrentSettings() {
        val selectedProvider = secureStorage.getSelectedProvider()

        _uiState.value = SettingsUiState(
            selectedProvider = selectedProvider,
            bigModelApiKey = secureStorage.getBigModelApiKey() ?: "",
            bigModelModelId = secureStorage.getBigModelModelId(),
            dashScopeApiKey = secureStorage.getDashScopeApiKey() ?: "",
            dashScopeModelId = secureStorage.getDashScopeModelId(),
            openAiApiKey = secureStorage.getOpenAIApiKey() ?: "",
            customApiKey = secureStorage.getCustomApiKey() ?: "",
            customBaseUrl = secureStorage.getCustomApiBaseUrl() ?: "",
            isConfigured = secureStorage.hasSelectedProviderApiKey()
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

                // Save selected provider
                secureStorage.setSelectedProvider(state.selectedProvider)

                // Save API keys and models
                when (state.selectedProvider) {
                    InferenceProvider.BIGMODEL -> {
                        if (state.bigModelApiKey.isNotBlank()) {
                            secureStorage.setBigModelApiKey(state.bigModelApiKey)
                            secureStorage.setBigModelModelId(state.bigModelModelId)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "BigModel API key is required"
                            )
                            return@launch
                        }
                    }
                    InferenceProvider.DASHSCOPE -> {
                        if (state.dashScopeApiKey.isNotBlank()) {
                            secureStorage.setDashScopeApiKey(state.dashScopeApiKey)
                            secureStorage.setDashScopeModelId(state.dashScopeModelId)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "DashScope API key is required"
                            )
                            return@launch
                        }
                    }
                    InferenceProvider.OPENAI -> {
                        if (state.openAiApiKey.isNotBlank()) {
                            secureStorage.setOpenAIApiKey(state.openAiApiKey)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "OpenAI API key is required"
                            )
                            return@launch
                        }
                    }
                    InferenceProvider.CUSTOM -> {
                        if (state.customApiKey.isNotBlank() && state.customBaseUrl.isNotBlank()) {
                            secureStorage.setCustomApiConfig(state.customApiKey, state.customBaseUrl)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "Custom API key and base URL are required"
                            )
                            return@launch
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                    isConfigured = true
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
    val saveError: String? = null
)
