package com.openautoglm.agent.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautoglm.agent.inference.DownloadMirror
import com.openautoglm.agent.inference.DownloadState
import com.openautoglm.agent.inference.ModelDownloadManager
import com.openautoglm.agent.inference.ModelFormat
import com.openautoglm.agent.inference.ModelInfo
import com.openautoglm.agent.inference.NetworkTestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
        observeMirrorState()
        // Auto-test network on init
        testNetworkConnectivity()
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
                val downloadProgressInfo = mutableMapOf<String, DownloadProgressInfo>()
                val downloadErrors = mutableMapOf<String, String>()

                states.forEach { (modelId, state) ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            downloadProgress[modelId] = state.progress
                            downloadProgressInfo[modelId] = DownloadProgressInfo(
                                progress = state.progress,
                                downloadedBytes = state.downloadedBytes,
                                totalBytes = state.totalBytes
                            )
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
                    downloadProgressInfo = downloadProgressInfo,
                    downloadErrors = downloadErrors
                )
            }
        }
    }

    /**
     * Observes mirror selection and network test states.
     */
    private fun observeMirrorState() {
        viewModelScope.launch {
            combine(
                downloadManager.selectedMirror,
                downloadManager.networkTestResults
            ) { selectedMirror, testResults ->
                Pair(selectedMirror, testResults)
            }.collect { (selectedMirror, testResults) ->
                _uiState.value = _uiState.value.copy(
                    selectedMirror = selectedMirror,
                    networkTestResults = testResults
                )
            }
        }
    }

    /**
     * Tests network connectivity to all mirrors.
     */
    fun testNetworkConnectivity() {
        _uiState.value = _uiState.value.copy(isTestingNetwork = true)
        viewModelScope.launch {
            try {
                downloadManager.testAllMirrors()
            } finally {
                _uiState.value = _uiState.value.copy(isTestingNetwork = false)
            }
        }
    }

    /**
     * Sets the download mirror.
     */
    fun setMirror(mirror: DownloadMirror) {
        downloadManager.setMirror(mirror)
    }

    /**
     * Auto-selects the best available mirror based on connectivity.
     */
    fun autoSelectBestMirror() {
        _uiState.value = _uiState.value.copy(isTestingNetwork = true)
        viewModelScope.launch {
            try {
                downloadManager.autoSelectBestMirror()
            } finally {
                _uiState.value = _uiState.value.copy(isTestingNetwork = false)
            }
        }
    }

    /**
     * Gets all available mirrors.
     */
    fun getAvailableMirrors(): List<DownloadMirror> = DownloadMirror.entries

    /**
     * Starts downloading a model.
     * Tests network connectivity first if not already tested.
     *
     * @param modelId The model identifier to download
     * @param requireWifi If true, only download on WiFi (default true)
     */
    fun startDownload(modelId: String, requireWifi: Boolean = true) {
        Log.i(TAG, "Starting download for model: $modelId")

        viewModelScope.launch {
            try {
                // Test network first if not tested
                val selectedMirror = _uiState.value.selectedMirror
                val testResult = _uiState.value.networkTestResults[selectedMirror]

                if (testResult == null || testResult is NetworkTestResult.NotTested) {
                    // Need to test network first
                    Log.i(TAG, "Testing network connectivity before download...")
                    _uiState.value = _uiState.value.copy(isTestingNetwork = true)
                    val result = downloadManager.testMirrorConnectivity(selectedMirror)

                    if (result is NetworkTestResult.Failed) {
                        Log.e(TAG, "Network test failed: ${result.error}")
                        _uiState.value = _uiState.value.copy(
                            isTestingNetwork = false,
                            downloadErrors = _uiState.value.downloadErrors + (modelId to
                                "Cannot connect to ${selectedMirror.displayName}. Please check your network or select a different mirror.\n" +
                                "无法连接到 ${selectedMirror.displayNameZh}。请检查网络或选择其他镜像源。")
                        )
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(isTestingNetwork = false)
                } else if (testResult is NetworkTestResult.Failed) {
                    // Already tested and failed
                    _uiState.value = _uiState.value.copy(
                        downloadErrors = _uiState.value.downloadErrors + (modelId to
                            "Cannot connect to ${selectedMirror.displayName}. Please check your network or select a different mirror.\n" +
                            "无法连接到 ${selectedMirror.displayNameZh}。请检查网络或选择其他镜像源。")
                    )
                    return@launch
                }

                // Start the download
                val result = downloadManager.startDownload(modelId, requireWifi, testNetworkFirst = false)
                when (result) {
                    is ModelDownloadManager.StartDownloadResult.Started -> {
                        Log.i(TAG, "Download started for $modelId")
                    }
                    is ModelDownloadManager.StartDownloadResult.AlreadyDownloaded -> {
                        Log.w(TAG, "Model $modelId already downloaded")
                    }
                    is ModelDownloadManager.StartDownloadResult.AlreadyDownloading -> {
                        Log.w(TAG, "Model $modelId already downloading")
                    }
                    is ModelDownloadManager.StartDownloadResult.NetworkUnavailable -> {
                        Log.e(TAG, "Network unavailable for mirror: ${result.mirror.displayName}")
                    }
                    is ModelDownloadManager.StartDownloadResult.Failed -> {
                        Log.e(TAG, "Download failed: ${result.error}")
                        _uiState.value = _uiState.value.copy(
                            downloadErrors = _uiState.value.downloadErrors + (modelId to result.error)
                        )
                    }
                    is ModelDownloadManager.StartDownloadResult.NeedNetworkTest -> {
                        Log.w(TAG, "Need to test network first")
                    }
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
 * Download progress info including bytes downloaded.
 */
data class DownloadProgressInfo(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long
)

/**
 * UI state for the Model Download screen.
 */
data class ModelDownloadUiState(
    val availableModels: List<ModelInfo> = emptyList(),
    val downloadedModels: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadProgressInfo: Map<String, DownloadProgressInfo> = emptyMap(),
    val downloadErrors: Map<String, String> = emptyMap(),
    val recommendedModelId: String? = null,
    val totalModelSizeMB: Long = 0,
    val availableSpaceMB: Long = 0,
    val selectedFilter: ModelFilter = ModelFilter.ALL,
    // Mirror-related state
    val selectedMirror: DownloadMirror = DownloadMirror.HUGGINGFACE,
    val networkTestResults: Map<DownloadMirror, NetworkTestResult> = emptyMap(),
    val isTestingNetwork: Boolean = false
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
