package com.openautoglm.agent.inference

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openautoglm.agent.data.entities.InferenceMode
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Secure storage for API keys and sensitive credentials using Android Keystore.
 *
 * This class provides encrypted storage for model API keys (BigModel, DashScope, OpenAI, etc.)
 * using Android's security best practices:
 * - EncryptedSharedPreferences for automatic encryption/decryption
 * - MasterKey backed by Android Keystore
 * - Keys never stored in plain text
 *
 * @param context Application context
 */
class SecureKeyStorage(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val PREFS_FILENAME = "autoglm_secure_keys"

        // Key names for different API providers
        private const val KEY_BIGMODEL_API_KEY = "bigmodel_api_key"
        private const val KEY_DASHSCOPE_API_KEY = "dashscope_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_CUSTOM_API_BASE_URL = "custom_api_base_url"

        // Model configuration keys
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_BIGMODEL_MODEL_ID = "bigmodel_model_id"
        private const val KEY_DASHSCOPE_MODEL_ID = "dashscope_model_id"

        // Inference mode and on-device model selection
        private const val KEY_INFERENCE_MODE = "inference_mode"
        private const val KEY_SELECTED_ON_DEVICE_MODEL = "selected_on_device_model"
        private const val KEY_HUGGINGFACE_TOKEN = "huggingface_token"

        // Voice recognition settings
        private const val KEY_SPEECH_RECOGNIZER_TYPE = "speech_recognizer_type"

        // Default model IDs
        const val DEFAULT_BIGMODEL_MODEL = "AutoGLM-Phone"
        const val DEFAULT_DASHSCOPE_MODEL = "qwen2.5-vl-72b-instruct"
    }

    /**
     * Stores the BigModel API key securely.
     *
     * @param apiKey The API key to store
     */
    fun setBigModelApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_BIGMODEL_API_KEY, apiKey)
            .apply()
    }

    /**
     * Retrieves the BigModel API key.
     *
     * @return The API key, or null if not set
     */
    fun getBigModelApiKey(): String? {
        return sharedPreferences.getString(KEY_BIGMODEL_API_KEY, null)
    }

    /**
     * Stores the DashScope API key securely.
     *
     * @param apiKey The API key to store
     */
    fun setDashScopeApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_DASHSCOPE_API_KEY, apiKey)
            .apply()
    }

    /**
     * Retrieves the DashScope API key.
     *
     * @return The API key, or null if not set
     */
    fun getDashScopeApiKey(): String? {
        return sharedPreferences.getString(KEY_DASHSCOPE_API_KEY, null)
    }

    /**
     * Stores the OpenAI API key securely.
     *
     * @param apiKey The API key to store
     */
    fun setOpenAIApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_OPENAI_API_KEY, apiKey)
            .apply()
    }

    /**
     * Retrieves the OpenAI API key.
     *
     * @return The API key, or null if not set
     */
    fun getOpenAIApiKey(): String? {
        return sharedPreferences.getString(KEY_OPENAI_API_KEY, null)
    }

    /**
     * Stores a custom API key and base URL for self-hosted or alternative providers.
     *
     * @param apiKey The API key to store
     * @param baseUrl The base URL for the API endpoint
     */
    fun setCustomApiConfig(apiKey: String, baseUrl: String) {
        sharedPreferences.edit()
            .putString(KEY_CUSTOM_API_KEY, apiKey)
            .putString(KEY_CUSTOM_API_BASE_URL, baseUrl)
            .apply()
    }

    /**
     * Retrieves the custom API key.
     *
     * @return The API key, or null if not set
     */
    fun getCustomApiKey(): String? {
        return sharedPreferences.getString(KEY_CUSTOM_API_KEY, null)
    }

    /**
     * Retrieves the custom API base URL.
     *
     * @return The base URL, or null if not set
     */
    fun getCustomApiBaseUrl(): String? {
        return sharedPreferences.getString(KEY_CUSTOM_API_BASE_URL, null)
    }

    /**
     * Sets the selected inference provider.
     *
     * @param provider The provider to use (bigmodel, dashscope, openai, custom)
     */
    fun setSelectedProvider(provider: InferenceProvider) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_PROVIDER, provider.name)
            .apply()
    }

    /**
     * Gets the selected inference provider.
     *
     * @return The selected provider, or BIGMODEL as default
     */
    fun getSelectedProvider(): InferenceProvider {
        val providerName = sharedPreferences.getString(KEY_SELECTED_PROVIDER, InferenceProvider.BIGMODEL.name)
        return InferenceProvider.valueOf(providerName ?: InferenceProvider.BIGMODEL.name)
    }

    /**
     * Sets the BigModel model ID.
     *
     * @param modelId The model ID to use
     */
    fun setBigModelModelId(modelId: String) {
        sharedPreferences.edit()
            .putString(KEY_BIGMODEL_MODEL_ID, modelId)
            .apply()
    }

    /**
     * Gets the BigModel model ID.
     *
     * @return The model ID, or default if not set
     */
    fun getBigModelModelId(): String {
        return sharedPreferences.getString(KEY_BIGMODEL_MODEL_ID, DEFAULT_BIGMODEL_MODEL)
            ?: DEFAULT_BIGMODEL_MODEL
    }

    /**
     * Sets the DashScope model ID.
     *
     * @param modelId The model ID to use
     */
    fun setDashScopeModelId(modelId: String) {
        sharedPreferences.edit()
            .putString(KEY_DASHSCOPE_MODEL_ID, modelId)
            .apply()
    }

    /**
     * Gets the DashScope model ID.
     *
     * @return The model ID, or default if not set
     */
    fun getDashScopeModelId(): String {
        return sharedPreferences.getString(KEY_DASHSCOPE_MODEL_ID, DEFAULT_DASHSCOPE_MODEL)
            ?: DEFAULT_DASHSCOPE_MODEL
    }

    /**
     * Checks if any API key is configured.
     *
     * @return true if at least one API key is configured
     */
    fun hasAnyApiKey(): Boolean {
        return !getBigModelApiKey().isNullOrBlank() ||
               !getDashScopeApiKey().isNullOrBlank() ||
               !getOpenAIApiKey().isNullOrBlank() ||
               !getCustomApiKey().isNullOrBlank()
    }

    /**
     * Checks if the selected provider has an API key configured.
     *
     * @return true if the selected provider has an API key
     */
    fun hasSelectedProviderApiKey(): Boolean {
        return when (getSelectedProvider()) {
            InferenceProvider.BIGMODEL -> !getBigModelApiKey().isNullOrBlank()
            InferenceProvider.DASHSCOPE -> !getDashScopeApiKey().isNullOrBlank()
            InferenceProvider.OPENAI -> !getOpenAIApiKey().isNullOrBlank()
            InferenceProvider.CUSTOM -> !getCustomApiKey().isNullOrBlank()
        }
    }

    /**
     * Clears all stored API keys.
     * Use with caution - this will remove all credentials.
     */
    fun clearAllKeys() {
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Clears the API key for a specific provider.
     *
     * @param provider The provider whose key should be cleared
     */
    fun clearProviderKey(provider: InferenceProvider) {
        sharedPreferences.edit().apply {
            when (provider) {
                InferenceProvider.BIGMODEL -> remove(KEY_BIGMODEL_API_KEY)
                InferenceProvider.DASHSCOPE -> remove(KEY_DASHSCOPE_API_KEY)
                InferenceProvider.OPENAI -> remove(KEY_OPENAI_API_KEY)
                InferenceProvider.CUSTOM -> {
                    remove(KEY_CUSTOM_API_KEY)
                    remove(KEY_CUSTOM_API_BASE_URL)
                }
            }
        }.apply()
    }

    /**
     * Gets configuration summary (without exposing actual keys).
     *
     * @return A map of provider names to whether they're configured
     */
    fun getConfigurationSummary(): Map<String, Boolean> {
        return mapOf(
            "BigModel" to !getBigModelApiKey().isNullOrBlank(),
            "DashScope" to !getDashScopeApiKey().isNullOrBlank(),
            "OpenAI" to !getOpenAIApiKey().isNullOrBlank(),
            "Custom" to !getCustomApiKey().isNullOrBlank()
        )
    }

    // ========== Inference Mode Settings ==========

    /**
     * Sets the inference mode (Cloud, On-Device, or Auto).
     *
     * @param mode The inference mode to use
     */
    fun setInferenceMode(mode: InferenceMode) {
        sharedPreferences.edit()
            .putString(KEY_INFERENCE_MODE, mode.name)
            .apply()
    }

    /**
     * Gets the selected inference mode.
     *
     * @return The selected mode, or CLOUD as default
     */
    fun getInferenceMode(): InferenceMode {
        val modeName = sharedPreferences.getString(KEY_INFERENCE_MODE, InferenceMode.CLOUD.name)
        return try {
            InferenceMode.valueOf(modeName ?: InferenceMode.CLOUD.name)
        } catch (e: IllegalArgumentException) {
            InferenceMode.CLOUD
        }
    }

    /**
     * Sets the selected on-device model ID.
     *
     * @param modelId The model ID to use for on-device inference
     */
    fun setSelectedOnDeviceModel(modelId: String) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_ON_DEVICE_MODEL, modelId)
            .apply()
    }

    /**
     * Gets the selected on-device model ID.
     *
     * @return The model ID, or null if not set (will use first available)
     */
    fun getSelectedOnDeviceModel(): String? {
        return sharedPreferences.getString(KEY_SELECTED_ON_DEVICE_MODEL, null)
    }

    // ========== HuggingFace Token (for gated models like Gemma) ==========

    /**
     * Sets the HuggingFace access token for downloading gated models.
     *
     * @param token The HuggingFace access token
     */
    fun setHuggingFaceToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_HUGGINGFACE_TOKEN, token)
            .apply()
    }

    /**
     * Gets the HuggingFace access token.
     *
     * @return The token, or null if not set
     */
    fun getHuggingFaceToken(): String? {
        return sharedPreferences.getString(KEY_HUGGINGFACE_TOKEN, null)
    }

    /**
     * Checks if a HuggingFace token is configured.
     *
     * @return true if a token is set
     */
    fun hasHuggingFaceToken(): Boolean {
        return !getHuggingFaceToken().isNullOrBlank()
    }

    /**
     * Clears the HuggingFace token.
     */
    fun clearHuggingFaceToken() {
        sharedPreferences.edit()
            .remove(KEY_HUGGINGFACE_TOKEN)
            .apply()
    }

    // ========== Voice Recognition Settings ==========

    /**
     * Sets the speech recognizer type (Android built-in or Vosk offline).
     *
     * @param type The recognizer type name ("ANDROID_BUILTIN" or "VOSK_OFFLINE")
     */
    fun setSpeechRecognizerType(type: String) {
        sharedPreferences.edit()
            .putString(KEY_SPEECH_RECOGNIZER_TYPE, type)
            .apply()
    }

    /**
     * Gets the speech recognizer type.
     *
     * @return The recognizer type name, or "ANDROID_BUILTIN" as default
     */
    fun getSpeechRecognizerType(): String {
        return sharedPreferences.getString(KEY_SPEECH_RECOGNIZER_TYPE, "ANDROID_BUILTIN")
            ?: "ANDROID_BUILTIN"
    }
}

/**
 * Enum representing supported cloud inference providers.
 */
enum class InferenceProvider {
    /** BigModel/ZhipuAI provider (AutoGLM-Phone) */
    BIGMODEL,

    /** DashScope/Alibaba Cloud provider (Qwen2.5-VL-72B) */
    DASHSCOPE,

    /** OpenAI-compatible provider */
    OPENAI,

    /** Custom self-hosted provider */
    CUSTOM
}
