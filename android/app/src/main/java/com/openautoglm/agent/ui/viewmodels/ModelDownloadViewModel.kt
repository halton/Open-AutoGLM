package com.openautoglm.agent.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautoglm.agent.inference.DownloadState
import com.openautoglm.agent.inference.ModelDownloadManager
import com.openautoglm.agent.inference.ModelFormat
import com.openautoglm.agent.inference.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Model Download screen.
 *
 * Manages on-device model downloads, progress tracking, and storage information.
 */
class ModelDownloadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ModelDownloadViewModel"
    }

    private val downloadManager = ModelDownloadManager(application)

    // UI State
    private val _uiState = MutableStateFlow(ModelDownloadUiState())
    val uiState: StateFlow<ModelDownloadUiState> = _uiState.asStateFlow()

    init {
        loadModels()
        observeDownloadStates()
    }

    /**
     * Loads available and downloaded models.
     */
    private fun loadModels() {
        val availableModels = downloadManager.getAvailableModels()
        val downloadedModels = downloadManager.getDownloadedModels()
        val recommendedModel = downloadManager.getRecommendedModel()

        _uiState.value = _uiState.value.copy(
            availableModels = availableModels,
            downloadedModels = downloadedModels.map { it.id }.toSet(),
            recommendedModelId = recommendedModel.id,
            totalModelSizeMB = downloadManager.getTotalModelSizeMB(),
            availableSpaceMB = downloadManager.getAvailableSpaceMB()
        )
    }

    /**
     * Observes download states from the download manager.
     */
    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadManager.downloadStates.collect { states ->
                val downloadProgress = mutableMapOf<String, Float>()
                val downloadErrors = mutableMapOf<String, String>()

                states.forEach { (modelId, state) ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            downloadProgress[modelId] = state.progress
                        }
                        is DownloadState.Failed -> {
                            downloadErrors[modelId] = state.error
                        }
                        is DownloadState.Downloaded -> {
                            // Refresh downloaded models list
                            val downloadedModels = downloadManager.getDownloadedModels()
                            _uiState.value = _uiState.value.copy(
                                downloadedModels = downloadedModels.map { it.id }.toSet(),
                                totalModelSizeMB = downloadManager.getTotalModelSizeMB()
                            )
                        }
                        else -> { /* No action needed */ }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    downloadProgress = downloadProgress,
                    downloadErrors = downloadErrors
                )
            }
        }
    }

    /**
     * Starts downloading a model.
     *
     * @param modelId The model identifier to download
     * @param requireWifi If true, only download on WiFi (default true)
     */
    fun startDownload(modelId: String, requireWifi: Boolean = true) {
        Log.i(TAG, "Starting download for model: $modelId")

        viewModelScope.launch {
            try {
                val started = downloadManager.startDownload(modelId, requireWifi)
                if (!started) {
                    Log.w(TAG, "Download not started for $modelId (already downloaded or downloading)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    downloadErrors = _uiState.value.downloadErrors + (modelId to (e.message ?: "Unknown error"))
                )
            }
        }
    }

    /**
     * Cancels an ongoing download.
     */
    fun cancelDownload(modelId: String) {
        Log.i(TAG, "Cancelling download for model: $modelId")
        downloadManager.cancelDownload(modelId)

        _uiState.value = _uiState.value.copy(
            downloadProgress = _uiState.value.downloadProgress - modelId
        )
    }

    /**
     * Deletes a downloaded model.
     */
    fun deleteModel(modelId: String) {
        Log.i(TAG, "Deleting model: $modelId")

        viewModelScope.launch {
            val deleted = downloadManager.deleteModel(modelId)
            if (deleted) {
                _uiState.value = _uiState.value.copy(
                    downloadedModels = _uiState.value.downloadedModels - modelId,
                    totalModelSizeMB = downloadManager.getTotalModelSizeMB()
                )
            }
        }
    }

    /**
     * Clears an error message for a model.
     */
    fun clearError(modelId: String) {
        _uiState.value = _uiState.value.copy(
            downloadErrors = _uiState.value.downloadErrors - modelId
        )
    }

    /**
     * Refreshes the model list and storage info.
     */
    fun refresh() {
        loadModels()
    }

    /**
     * Checks if a model is currently downloading.
     */
    fun isDownloading(modelId: String): Boolean {
        return _uiState.value.downloadProgress.containsKey(modelId)
    }

    /**
     * Checks if a model is downloaded.
     */
    fun isDownloaded(modelId: String): Boolean {
        return _uiState.value.downloadedModels.contains(modelId)
    }

    /**
     * Gets the download state for a specific model.
     */
    fun getDownloadState(modelId: String): DownloadState {
        return downloadManager.getDownloadState(modelId)
    }

    /**
     * Gets models filtered by format.
     */
    fun getModelsByFormat(format: ModelFormat): List<ModelInfo> {
        return _uiState.value.availableModels.filter { it.format == format }
    }
}

/**
 * UI state for the Model Download screen.
 */
data class ModelDownloadUiState(
    val availableModels: List<ModelInfo> = emptyList(),
    val downloadedModels: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadErrors: Map<String, String> = emptyMap(),
    val recommendedModelId: String? = null,
    val totalModelSizeMB: Long = 0,
    val availableSpaceMB: Long = 0,
    val selectedFilter: ModelFilter = ModelFilter.ALL
)

/**
 * Filter options for model list.
 */
enum class ModelFilter {
    ALL,
    GGUF,      // AutoGLM-Phone models for llama.cpp
    MEDIAPIPE, // Gemma models for MediaPipe
    DOWNLOADED
}
