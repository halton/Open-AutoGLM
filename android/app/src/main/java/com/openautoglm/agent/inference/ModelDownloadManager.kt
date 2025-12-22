package com.openautoglm.agent.inference

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Manages downloading and storage of on-device ML models.
 *
 * Supports:
 * - MediaPipe LLM Inference API compatible models (.task format)
 * - llama.cpp compatible models (.gguf format) including AutoGLM-Phone-9B
 * - Background downloads using WorkManager
 * - Progress tracking
 * - Resume capability for interrupted downloads
 * - Storage space checks
 *
 * @param context Application context
 */
/**
 * Available download mirror hosts.
 */
enum class DownloadMirror(
    val displayName: String,
    val displayNameZh: String,
    val baseUrl: String,
    val testUrl: String
) {
    /** Official Hugging Face - best for international users */
    HUGGINGFACE(
        displayName = "Hugging Face (International)",
        displayNameZh = "Hugging Face（国际）",
        baseUrl = "https://huggingface.co",
        testUrl = "https://huggingface.co/api/models"
    ),
    /** hf-mirror.com - Hugging Face mirror for China users */
    HF_MIRROR(
        displayName = "HF Mirror (China)",
        displayNameZh = "HF 镜像（中国）",
        baseUrl = "https://hf-mirror.com",
        testUrl = "https://hf-mirror.com/api/models"
    );

    companion object {
        fun fromOrdinal(ordinal: Int): DownloadMirror = entries.getOrElse(ordinal) { HUGGINGFACE }
    }
}

/**
 * Result of network connectivity test.
 */
sealed class NetworkTestResult {
    data object Testing : NetworkTestResult()
    data class Success(val mirror: DownloadMirror, val latencyMs: Long) : NetworkTestResult()
    data class Failed(val mirror: DownloadMirror, val error: String) : NetworkTestResult()
    data object NotTested : NetworkTestResult()
}

class ModelDownloadManager(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("model_download_prefs", Context.MODE_PRIVATE)

    // Current mirror selection
    private val _selectedMirror = MutableStateFlow(
        DownloadMirror.fromOrdinal(prefs.getInt("selected_mirror", 0))
    )
    val selectedMirror: StateFlow<DownloadMirror> = _selectedMirror.asStateFlow()

    // Network test results
    private val _networkTestResults = MutableStateFlow<Map<DownloadMirror, NetworkTestResult>>(emptyMap())
    val networkTestResults: StateFlow<Map<DownloadMirror, NetworkTestResult>> = _networkTestResults.asStateFlow()

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODELS_DIR = "models"
        private const val WORK_NAME_PREFIX = "model_download_"
        private const val NETWORK_TEST_TIMEOUT_MS = 10000L

        // All available models (both MediaPipe .task and llama.cpp .gguf)
        val AVAILABLE_MODELS = mapOf(
            // ========== AutoGLM-Phone GGUF Models (llama.cpp) ==========
            // These are the SAME model as the cloud API, just quantized for on-device

            // AutoGLM-Phone-9B Q4_K_M - RECOMMENDED for Pixel 9 Pro Fold (16GB RAM)
            "autoglm-9b-q4km" to ModelInfo(
                id = "autoglm-9b-q4km",
                displayName = "AutoGLM-Phone 9B (Q4_K_M)",
                fileName = "AutoGLM-Phone-9B-Multilingual.Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/mradermacher/AutoGLM-Phone-9B-Multilingual-GGUF/resolve/main/AutoGLM-Phone-9B-Multilingual.Q4_K_M.gguf",
                sizeBytes = 6_170_000_000L, // ~6.17GB
                minRamMB = 8192, // 8GB RAM minimum
                description = "RECOMMENDED - Same as cloud API, best quality/size balance",
                descriptionZh = "推荐 - 与云端API相同的模型，质量/大小最佳平衡",
                supportsVision = true,
                format = ModelFormat.GGUF
            ),

            // AutoGLM-Phone-9B Q2_K - Smaller, faster, lower quality
            "autoglm-9b-q2k" to ModelInfo(
                id = "autoglm-9b-q2k",
                displayName = "AutoGLM-Phone 9B (Q2_K)",
                fileName = "AutoGLM-Phone-9B-Multilingual.Q2_K.gguf",
                downloadUrl = "https://huggingface.co/mradermacher/AutoGLM-Phone-9B-Multilingual-GGUF/resolve/main/AutoGLM-Phone-9B-Multilingual.Q2_K.gguf",
                sizeBytes = 4_040_000_000L, // ~4.04GB
                minRamMB = 6144, // 6GB RAM minimum
                description = "Smaller model, faster inference, lower quality",
                descriptionZh = "更小的模型，推理更快，质量较低",
                supportsVision = true,
                format = ModelFormat.GGUF
            ),

            // AutoGLM-Phone-9B Q6_K - Higher quality, larger
            "autoglm-9b-q6k" to ModelInfo(
                id = "autoglm-9b-q6k",
                displayName = "AutoGLM-Phone 9B (Q6_K)",
                fileName = "AutoGLM-Phone-9B-Multilingual.Q6_K.gguf",
                downloadUrl = "https://huggingface.co/mradermacher/AutoGLM-Phone-9B-Multilingual-GGUF/resolve/main/AutoGLM-Phone-9B-Multilingual.Q6_K.gguf",
                sizeBytes = 7_750_000_000L, // ~7.75GB
                minRamMB = 10240, // 10GB RAM minimum
                description = "Higher quality, requires more RAM",
                descriptionZh = "质量更高，需要更多内存",
                supportsVision = true,
                format = ModelFormat.GGUF
            ),

            // ========== Gemma MediaPipe Models (.task format) ==========
            // These are fallback options for devices that can't run AutoGLM

            // Gemma 3 1B - Best balance of size and capability
            "gemma3-1b-int4" to ModelInfo(
                id = "gemma3-1b-int4",
                displayName = "Gemma 3 1B (INT4)",
                fileName = "gemma3-1b-it-int4.task",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                sizeBytes = 700_000_000L, // ~700MB
                minRamMB = 2048,
                description = "Fallback - Compact model for low-end devices",
                descriptionZh = "备选 - 适用于低端设备的紧凑模型",
                supportsVision = false,
                format = ModelFormat.MEDIAPIPE
            ),
            // Gemma 3 1B INT8 - Higher quality
            "gemma3-1b-int8" to ModelInfo(
                id = "gemma3-1b-int8",
                displayName = "Gemma 3 1B (INT8)",
                fileName = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task",
                sizeBytes = 1_200_000_000L, // ~1.2GB
                minRamMB = 3072,
                description = "Fallback - Higher quality Gemma for mid-range devices",
                descriptionZh = "备选 - 适用于中端设备的高质量Gemma",
                supportsVision = false,
                format = ModelFormat.MEDIAPIPE
            ),
            // Gemma 3 1B with larger context
            "gemma3-1b-int4-4k" to ModelInfo(
                id = "gemma3-1b-int4-4k",
                displayName = "Gemma 3 1B (INT4, 4K context)",
                fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task",
                sizeBytes = 800_000_000L, // ~800MB
                minRamMB = 3072,
                description = "Fallback - INT4 with extended 4096 token context",
                descriptionZh = "备选 - INT4量化，支持4096 token上下文",
                supportsVision = false,
                format = ModelFormat.MEDIAPIPE
            )
        )
    }

    private val workManager = WorkManager.getInstance(context)
    private val modelsDir = File(context.filesDir, MODELS_DIR)

    // Download state tracking
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    init {
        // Ensure models directory exists
        modelsDir.mkdirs()
    }

    /**
     * Gets the list of available models for download.
     */
    fun getAvailableModels(): List<ModelInfo> {
        return AVAILABLE_MODELS.values.toList()
    }

    /**
     * Gets the list of downloaded models.
     */
    fun getDownloadedModels(): List<ModelInfo> {
        return AVAILABLE_MODELS.values.filter { isModelDownloaded(it.id) }
    }

    /**
     * Checks if a model is downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val modelInfo = AVAILABLE_MODELS[modelId] ?: return false
        val modelFile = File(modelsDir, modelInfo.fileName)
        // Check if file exists and is at least 90% of expected size (in case of slight variations)
        return modelFile.exists() && modelFile.length() >= modelInfo.sizeBytes * 0.9
    }

    /**
     * Gets the file path for a downloaded model.
     */
    fun getModelPath(modelId: String): String? {
        val modelInfo = AVAILABLE_MODELS[modelId] ?: return null
        val modelFile = File(modelsDir, modelInfo.fileName)
        return if (modelFile.exists()) modelFile.absolutePath else null
    }

    /**
     * Gets any available model file path (first downloaded model found).
     * Prefers GGUF models (AutoGLM) over MediaPipe models.
     */
    fun getAnyModelPath(): String? {
        // First check for GGUF models (preferred)
        for (model in AVAILABLE_MODELS.values.filter { it.isGguf }) {
            val modelFile = File(modelsDir, model.fileName)
            if (modelFile.exists() && modelFile.length() > 0) {
                return modelFile.absolutePath
            }
        }

        // Then check for MediaPipe models
        for (model in AVAILABLE_MODELS.values.filter { it.isMediaPipe }) {
            val modelFile = File(modelsDir, model.fileName)
            if (modelFile.exists() && modelFile.length() > 0) {
                return modelFile.absolutePath
            }
        }

        // Then check for any .gguf file
        val ggufFiles = modelsDir.listFiles { file ->
            file.extension.equals("gguf", ignoreCase = true) && file.length() > 0
        }
        if (!ggufFiles.isNullOrEmpty()) {
            return ggufFiles.first().absolutePath
        }

        // Then check for any .task file
        val taskFiles = modelsDir.listFiles { file ->
            file.extension.equals("task", ignoreCase = true) && file.length() > 0
        }
        return taskFiles?.firstOrNull()?.absolutePath
    }

    /**
     * Gets available GGUF model for llama.cpp inference.
     */
    fun getGgufModelPath(): String? {
        // Check for GGUF models in order of preference
        val ggufOrder = listOf("autoglm-9b-q4km", "autoglm-9b-q6k", "autoglm-9b-q2k")
        for (modelId in ggufOrder) {
            val model = AVAILABLE_MODELS[modelId] ?: continue
            val modelFile = File(modelsDir, model.fileName)
            if (modelFile.exists() && modelFile.length() > 0) {
                return modelFile.absolutePath
            }
        }

        // Check for any .gguf file
        val ggufFiles = modelsDir.listFiles { file ->
            file.extension.equals("gguf", ignoreCase = true) && file.length() > 0
        }
        return ggufFiles?.firstOrNull()?.absolutePath
    }

    /**
     * Gets available MediaPipe model for MediaPipe inference.
     */
    fun getMediaPipeModelPath(): String? {
        // Check for MediaPipe models
        for (model in AVAILABLE_MODELS.values.filter { it.isMediaPipe }) {
            val modelFile = File(modelsDir, model.fileName)
            if (modelFile.exists() && modelFile.length() > 0) {
                return modelFile.absolutePath
            }
        }

        // Check for any .task file
        val taskFiles = modelsDir.listFiles { file ->
            file.extension.equals("task", ignoreCase = true) && file.length() > 0
        }
        return taskFiles?.firstOrNull()?.absolutePath
    }

    /**
     * Starts downloading a model in the background.
     *
     * @param modelId The model identifier
     * @param requireWifi If true, only download on WiFi
     * @param testNetworkFirst If true, test network connectivity before starting download
     * @return StartDownloadResult indicating success, failure reason, or need to test network
     */
    fun startDownload(
        modelId: String,
        requireWifi: Boolean = true,
        testNetworkFirst: Boolean = false
    ): StartDownloadResult {
        val modelInfo = AVAILABLE_MODELS[modelId]
            ?: return StartDownloadResult.Failed("Unknown model: $modelId")

        if (isModelDownloaded(modelId)) {
            Log.i(TAG, "Model $modelId already downloaded")
            return StartDownloadResult.AlreadyDownloaded
        }

        // Check if already downloading
        val currentState = _downloadStates.value[modelId]
        if (currentState is DownloadState.Downloading) {
            Log.i(TAG, "Model $modelId already downloading")
            return StartDownloadResult.AlreadyDownloading
        }

        // Check available storage
        val availableSpace = modelsDir.usableSpace
        if (availableSpace < modelInfo.sizeBytes * 1.1) { // 10% buffer
            updateDownloadState(modelId, DownloadState.Failed("Insufficient storage space\n存储空间不足"))
            return StartDownloadResult.Failed("Insufficient storage space")
        }

        // Check if we need to test network first
        if (testNetworkFirst) {
            val testResult = _networkTestResults.value[_selectedMirror.value]
            if (testResult == null || testResult is NetworkTestResult.NotTested) {
                return StartDownloadResult.NeedNetworkTest
            }
            if (testResult is NetworkTestResult.Failed) {
                val mirror = _selectedMirror.value
                updateDownloadState(modelId, DownloadState.Failed(
                    "Cannot connect to ${mirror.displayName}. Please check your network or try a different mirror.\n" +
                    "无法连接到 ${mirror.displayNameZh}。请检查网络或尝试其他镜像源。"
                ))
                return StartDownloadResult.NetworkUnavailable(mirror)
            }
        }

        // Get the download URL with the selected mirror
        val downloadUrl = getDownloadUrlWithMirror(modelInfo.downloadUrl, _selectedMirror.value)

        Log.i(TAG, "Starting download for model: $modelId from ${_selectedMirror.value.displayName}")
        Log.i(TAG, "Download URL: $downloadUrl")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString("model_id", modelId)
            .putString("download_url", downloadUrl)
            .putString("file_name", modelInfo.fileName)
            .putLong("file_size", modelInfo.sizeBytes)
            .putString("mirror_name", _selectedMirror.value.displayName)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME_PREFIX + modelId,
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )

        updateDownloadState(modelId, DownloadState.Downloading(0f))

        // Observe work progress
        observeDownloadProgress(modelId, downloadRequest.id.toString())

        return StartDownloadResult.Started
    }

    /**
     * Result of starting a download.
     */
    sealed class StartDownloadResult {
        data object Started : StartDownloadResult()
        data object AlreadyDownloaded : StartDownloadResult()
        data object AlreadyDownloading : StartDownloadResult()
        data object NeedNetworkTest : StartDownloadResult()
        data class NetworkUnavailable(val mirror: DownloadMirror) : StartDownloadResult()
        data class Failed(val error: String) : StartDownloadResult()
    }

    /**
     * Cancels an ongoing download.
     */
    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork(WORK_NAME_PREFIX + modelId)
        updateDownloadState(modelId, DownloadState.Cancelled)

        // Delete partial file
        val modelInfo = AVAILABLE_MODELS[modelId] ?: return
        val partialFile = File(modelsDir, modelInfo.fileName + ".partial")
        partialFile.delete()
    }

    /**
     * Deletes a downloaded model.
     */
    fun deleteModel(modelId: String): Boolean {
        val modelInfo = AVAILABLE_MODELS[modelId] ?: return false
        val modelFile = File(modelsDir, modelInfo.fileName)
        val deleted = modelFile.delete()
        if (deleted) {
            updateDownloadState(modelId, DownloadState.NotDownloaded)
        }
        return deleted
    }

    /**
     * Gets the download state for a model.
     */
    fun getDownloadState(modelId: String): DownloadState {
        return _downloadStates.value[modelId] ?: run {
            if (isModelDownloaded(modelId)) {
                DownloadState.Downloaded
            } else {
                DownloadState.NotDownloaded
            }
        }
    }

    /**
     * Observes download progress as a Flow.
     */
    fun observeDownloadProgress(modelId: String): Flow<DownloadState> {
        return downloadStates.map { states ->
            states[modelId] ?: if (isModelDownloaded(modelId)) {
                DownloadState.Downloaded
            } else {
                DownloadState.NotDownloaded
            }
        }
    }

    /**
     * Gets the recommended model for the current device.
     * Prefers AutoGLM-Phone for high-end devices (8GB+ RAM).
     */
    fun getRecommendedModel(): ModelInfo {
        val availableRam = Runtime.getRuntime().maxMemory() / (1024 * 1024)

        return when {
            // High-end device (8GB+ RAM) - use AutoGLM-Phone Q4_K_M (same as cloud API)
            availableRam >= 8192 -> AVAILABLE_MODELS["autoglm-9b-q4km"]!!
            // Mid-high device (6GB+ RAM) - use AutoGLM-Phone Q2_K (smaller)
            availableRam >= 6144 -> AVAILABLE_MODELS["autoglm-9b-q2k"]!!
            // Mid-range device (3GB+ RAM) - use Gemma INT8
            availableRam >= 3072 -> AVAILABLE_MODELS["gemma3-1b-int8"]!!
            // Low-end device - use Gemma INT4
            else -> AVAILABLE_MODELS["gemma3-1b-int4"]!!
        }
    }

    /**
     * Gets GGUF models only (for llama.cpp).
     */
    fun getGgufModels(): List<ModelInfo> {
        return AVAILABLE_MODELS.values.filter { it.isGguf }
    }

    /**
     * Gets MediaPipe models only.
     */
    fun getMediaPipeModels(): List<ModelInfo> {
        return AVAILABLE_MODELS.values.filter { it.isMediaPipe }
    }

    private fun updateDownloadState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }

    private fun observeDownloadProgress(modelId: String, workId: String) {
        // Observe WorkManager work info for progress and completion
        val workInfoFlow = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_PREFIX + modelId)

        scope.launch {
            workInfoFlow.collect { workInfoList ->
                workInfoList.firstOrNull()?.let { workInfo ->
                    when (workInfo.state) {
                        androidx.work.WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getFloat("progress", 0f)
                            val downloadedBytes = workInfo.progress.getLong("downloaded_bytes", 0L)
                            val totalBytes = workInfo.progress.getLong("total_bytes", 0L)
                            updateDownloadState(modelId, DownloadState.Downloading(
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            ))
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            updateDownloadState(modelId, DownloadState.Downloaded)
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val errorMessage = workInfo.outputData.getString(ModelDownloadWorker.KEY_ERROR_MESSAGE)
                                ?: "Download failed. Please try again."
                            updateDownloadState(modelId, DownloadState.Failed(errorMessage))
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            updateDownloadState(modelId, DownloadState.Cancelled)
                        }
                        else -> { /* ENQUEUED, BLOCKED - no action needed */ }
                    }
                }
            }
        }
    }

    /**
     * Gets total disk space used by models in MB.
     */
    fun getTotalModelSizeMB(): Long {
        var total = 0L
        modelsDir.listFiles()?.forEach { file ->
            if (file.extension == "task" || file.extension == "gguf") {
                total += file.length()
            }
        }
        return total / (1024 * 1024)
    }

    /**
     * Gets available disk space in MB.
     */
    fun getAvailableSpaceMB(): Long {
        return modelsDir.usableSpace / (1024 * 1024)
    }

    /**
     * Returns the models directory path.
     */
    fun getModelsDir(): File = modelsDir

    /**
     * Sets the download mirror to use.
     */
    fun setMirror(mirror: DownloadMirror) {
        _selectedMirror.value = mirror
        prefs.edit().putInt("selected_mirror", mirror.ordinal).apply()
        Log.i(TAG, "Mirror set to: ${mirror.displayName}")
    }

    /**
     * Gets the download URL for a model using the selected mirror.
     */
    fun getDownloadUrl(modelId: String): String {
        val modelInfo = AVAILABLE_MODELS[modelId] ?: return ""
        return getDownloadUrlWithMirror(modelInfo.downloadUrl, _selectedMirror.value)
    }

    /**
     * Converts a Hugging Face URL to use the specified mirror.
     */
    private fun getDownloadUrlWithMirror(originalUrl: String, mirror: DownloadMirror): String {
        return originalUrl.replace("https://huggingface.co", mirror.baseUrl)
    }

    /**
     * Tests network connectivity to all mirrors and returns results.
     * Call this before starting a download to check which mirrors are accessible.
     */
    suspend fun testAllMirrors(): Map<DownloadMirror, NetworkTestResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<DownloadMirror, NetworkTestResult>()

        for (mirror in DownloadMirror.entries) {
            _networkTestResults.value = _networkTestResults.value + (mirror to NetworkTestResult.Testing)
            val result = testMirrorConnectivity(mirror)
            results[mirror] = result
            _networkTestResults.value = _networkTestResults.value + (mirror to result)
        }

        results
    }

    /**
     * Tests network connectivity to a specific mirror.
     *
     * @param mirror The mirror to test
     * @return NetworkTestResult indicating success or failure
     */
    suspend fun testMirrorConnectivity(mirror: DownloadMirror): NetworkTestResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Testing connectivity to ${mirror.displayName}...")

        val client = OkHttpClient.Builder()
            .connectTimeout(NETWORK_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(NETWORK_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(mirror.testUrl)
            .head() // Use HEAD request to minimize data transfer
            .build()

        try {
            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            response.close()

            if (response.isSuccessful || response.code == 302 || response.code == 301) {
                Log.i(TAG, "${mirror.displayName} is reachable (latency: ${latency}ms)")
                NetworkTestResult.Success(mirror, latency)
            } else {
                Log.w(TAG, "${mirror.displayName} returned error: ${response.code}")
                NetworkTestResult.Failed(mirror, "Server returned ${response.code}")
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "${mirror.displayName} timeout: ${e.message}")
            NetworkTestResult.Failed(mirror, "Connection timed out")
        } catch (e: UnknownHostException) {
            Log.e(TAG, "${mirror.displayName} unreachable: ${e.message}")
            NetworkTestResult.Failed(mirror, "Cannot resolve host")
        } catch (e: Exception) {
            Log.e(TAG, "${mirror.displayName} error: ${e.message}")
            NetworkTestResult.Failed(mirror, e.message ?: "Connection failed")
        }
    }

    /**
     * Tests connectivity to the currently selected mirror.
     *
     * @return NetworkTestResult for the selected mirror
     */
    suspend fun testSelectedMirror(): NetworkTestResult {
        val mirror = _selectedMirror.value
        _networkTestResults.value = _networkTestResults.value + (mirror to NetworkTestResult.Testing)
        val result = testMirrorConnectivity(mirror)
        _networkTestResults.value = _networkTestResults.value + (mirror to result)
        return result
    }

    /**
     * Auto-selects the best available mirror based on connectivity test results.
     * Tests all mirrors and selects the one with lowest latency.
     *
     * @return The selected mirror, or null if no mirrors are reachable
     */
    suspend fun autoSelectBestMirror(): DownloadMirror? = withContext(Dispatchers.IO) {
        val results = testAllMirrors()

        val bestMirror = results.entries
            .filter { it.value is NetworkTestResult.Success }
            .minByOrNull { (it.value as NetworkTestResult.Success).latencyMs }
            ?.key

        if (bestMirror != null) {
            setMirror(bestMirror)
            Log.i(TAG, "Auto-selected mirror: ${bestMirror.displayName}")
        } else {
            Log.w(TAG, "No mirrors are reachable")
        }

        bestMirror
    }

    /**
     * Checks if the selected mirror is currently reachable.
     * Returns cached result if available, otherwise performs a new test.
     */
    fun isSelectedMirrorReachable(): Boolean {
        val result = _networkTestResults.value[_selectedMirror.value]
        return result is NetworkTestResult.Success
    }

    /**
     * Clears network test results to force re-testing.
     */
    fun clearNetworkTestResults() {
        _networkTestResults.value = emptyMap()
    }
}

/**
 * Model format for inference engine selection.
 */
enum class ModelFormat {
    /** MediaPipe LLM Inference API (.task files) */
    MEDIAPIPE,
    /** llama.cpp GGUF format (.gguf files) */
    GGUF
}

/**
 * Information about a downloadable model.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val minRamMB: Int,
    val description: String,
    val descriptionZh: String,
    val supportsVision: Boolean = false,
    val format: ModelFormat = ModelFormat.MEDIAPIPE
) {
    val sizeMB: Long get() = sizeBytes / (1024 * 1024)

    /** Returns true if this is a GGUF model for llama.cpp */
    val isGguf: Boolean get() = format == ModelFormat.GGUF

    /** Returns true if this is a MediaPipe model */
    val isMediaPipe: Boolean get() = format == ModelFormat.MEDIAPIPE
}

/**
 * Represents the download state of a model.
 */
sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : DownloadState()
    data object Downloaded : DownloadState()
    data class Failed(val error: String) : DownloadState()
    data object Cancelled : DownloadState()
}

/**
 * WorkManager worker for downloading models in the background.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val BUFFER_SIZE = 8192
        private const val MODELS_DIR = "models"

        // Error output keys
        const val KEY_ERROR_TYPE = "error_type"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_MODEL_ID = "model_id"

        // Error types
        const val ERROR_NETWORK_CONNECTION = "network_connection"
        const val ERROR_NETWORK_TIMEOUT = "network_timeout"
        const val ERROR_DOWNLOAD_FAILED = "download_failed"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString("model_id") ?: return@withContext Result.failure()
        val downloadUrl = inputData.getString("download_url") ?: return@withContext Result.failure()
        val fileName = inputData.getString("file_name") ?: return@withContext Result.failure()
        val fileSize = inputData.getLong("file_size", 0L)

        Log.i(TAG, "Starting download: $modelId from $downloadUrl")

        val modelsDir = File(applicationContext.filesDir, MODELS_DIR)
        modelsDir.mkdirs()

        val partialFile = File(modelsDir, "$fileName.partial")
        val finalFile = File(modelsDir, fileName)

        try {
            // Check if we can resume
            val startByte = if (partialFile.exists()) partialFile.length() else 0L

            val requestBuilder = Request.Builder().url(downloadUrl)
            if (startByte > 0) {
                requestBuilder.addHeader("Range", "bytes=$startByte-")
                Log.i(TAG, "Resuming download from byte $startByte")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                val errorMessage = when (response.code) {
                    401 -> "Model access unauthorized (401). The model may require authentication or the URL is invalid.\n模型访问未授权 (401)。模型可能需要认证或URL无效。"
                    403 -> "Model access forbidden (403). You may need to accept the model's terms on Hugging Face.\n模型访问被禁止 (403)。您可能需要在 Hugging Face 上接受模型条款。"
                    404 -> "Model not found (404). The model file may have been moved or deleted.\n未找到模型 (404)。模型文件可能已被移动或删除。"
                    else -> "Download failed with HTTP error ${response.code}.\n下载失败，HTTP 错误 ${response.code}。"
                }
                val errorData = Data.Builder()
                    .putString(KEY_MODEL_ID, modelId)
                    .putString(KEY_ERROR_TYPE, ERROR_DOWNLOAD_FAILED)
                    .putString(KEY_ERROR_MESSAGE, errorMessage)
                    .build()
                return@withContext Result.failure(errorData)
            }

            val body = response.body ?: return@withContext Result.failure()
            val contentLength = body.contentLength()
            val totalSize = if (startByte > 0) startByte + contentLength else contentLength

            FileOutputStream(partialFile, startByte > 0).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var downloaded = startByte

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        // Update progress
                        val progress = if (totalSize > 0) {
                            (downloaded.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        setProgress(Data.Builder()
                            .putFloat("progress", progress)
                            .putString("model_id", modelId)
                            .putLong("downloaded_bytes", downloaded)
                            .putLong("total_bytes", totalSize)
                            .build())

                        // Check for cancellation
                        if (isStopped) {
                            Log.i(TAG, "Download cancelled: $modelId")
                            return@withContext Result.failure()
                        }
                    }
                }
            }

            // Rename partial file to final file
            if (partialFile.renameTo(finalFile)) {
                Log.i(TAG, "Download complete: $modelId (${finalFile.length()} bytes)")
                return@withContext Result.success()
            } else {
                Log.e(TAG, "Failed to rename downloaded file")
                return@withContext Result.failure()
            }

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Download timeout: ${e.message}", e)
            val errorData = Data.Builder()
                .putString(KEY_MODEL_ID, modelId)
                .putString(KEY_ERROR_TYPE, ERROR_NETWORK_TIMEOUT)
                .putString(KEY_ERROR_MESSAGE, "Connection timed out. Please ensure you can access huggingface.co (VPN may be required in some regions).\n连接超时。请确保可以访问 huggingface.co（部分地区可能需要 VPN）。")
                .build()
            return@withContext Result.failure(errorData)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host: ${e.message}", e)
            val errorData = Data.Builder()
                .putString(KEY_MODEL_ID, modelId)
                .putString(KEY_ERROR_TYPE, ERROR_NETWORK_CONNECTION)
                .putString(KEY_ERROR_MESSAGE, "Cannot reach huggingface.co. Please check your network connection or try using a VPN.\n无法访问 huggingface.co。请检查网络连接或尝试使用 VPN。")
                .build()
            return@withContext Result.failure(errorData)
        } catch (e: SSLException) {
            Log.e(TAG, "SSL error: ${e.message}", e)
            val errorData = Data.Builder()
                .putString(KEY_MODEL_ID, modelId)
                .putString(KEY_ERROR_TYPE, ERROR_NETWORK_CONNECTION)
                .putString(KEY_ERROR_MESSAGE, "Secure connection failed. Please ensure you can access huggingface.co (VPN may be required).\n安全连接失败。请确保可以访问 huggingface.co（可能需要 VPN）。")
                .build()
            return@withContext Result.failure(errorData)
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            // Check if the exception message indicates a connection issue
            val message = e.message ?: ""
            if (message.contains("huggingface", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("connect", ignoreCase = true)) {
                val errorData = Data.Builder()
                    .putString(KEY_MODEL_ID, modelId)
                    .putString(KEY_ERROR_TYPE, ERROR_NETWORK_CONNECTION)
                    .putString(KEY_ERROR_MESSAGE, "Network connection error. Please ensure you can access huggingface.co (VPN may be required in some regions).\n网络连接错误。请确保可以访问 huggingface.co（部分地区可能需要 VPN）。")
                    .build()
                return@withContext Result.failure(errorData)
            }
            return@withContext Result.retry()
        }
    }
}
