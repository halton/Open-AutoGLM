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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages downloading and storage of on-device ML models.
 *
 * Supports MediaPipe LLM Inference API compatible models in .task format.
 * Models are downloaded from Hugging Face and stored locally.
 *
 * Supports:
 * - Background downloads using WorkManager
 * - Progress tracking
 * - Resume capability for interrupted downloads
 * - Storage space checks
 *
 * @param context Application context
 */
class ModelDownloadManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODELS_DIR = "models"
        private const val WORK_NAME_PREFIX = "model_download_"

        // MediaPipe-compatible models (.task format)
        // These models work with com.google.mediapipe:tasks-genai
        val AVAILABLE_MODELS = mapOf(
            // Gemma 3 1B - Best balance of size and capability
            "gemma3-1b-int4" to ModelInfo(
                id = "gemma3-1b-int4",
                displayName = "Gemma 3 1B (INT4)",
                fileName = "gemma3-1b-it-int4.task",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                sizeBytes = 700_000_000L, // ~700MB
                minRamMB = 2048,
                description = "Recommended - Compact model with good performance",
                descriptionZh = "推荐 - 紧凑模型，性能良好",
                supportsVision = false
            ),
            // Gemma 3 1B INT8 - Higher quality
            "gemma3-1b-int8" to ModelInfo(
                id = "gemma3-1b-int8",
                displayName = "Gemma 3 1B (INT8)",
                fileName = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task",
                sizeBytes = 1_200_000_000L, // ~1.2GB
                minRamMB = 3072,
                description = "Higher quality with INT8 quantization",
                descriptionZh = "INT8量化，质量更高",
                supportsVision = false
            ),
            // Gemma 3 1B with larger context
            "gemma3-1b-int4-4k" to ModelInfo(
                id = "gemma3-1b-int4-4k",
                displayName = "Gemma 3 1B (INT4, 4K context)",
                fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task",
                sizeBytes = 800_000_000L, // ~800MB
                minRamMB = 3072,
                description = "INT4 with extended 4096 token context",
                descriptionZh = "INT4量化，支持4096 token上下文",
                supportsVision = false
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
     */
    fun getAnyModelPath(): String? {
        // First check for known models
        for (model in AVAILABLE_MODELS.values) {
            val modelFile = File(modelsDir, model.fileName)
            if (modelFile.exists() && modelFile.length() > 0) {
                return modelFile.absolutePath
            }
        }

        // Then check for any .task file
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
     * @return true if download was started, false if already downloading or downloaded
     */
    fun startDownload(modelId: String, requireWifi: Boolean = true): Boolean {
        val modelInfo = AVAILABLE_MODELS[modelId]
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        if (isModelDownloaded(modelId)) {
            Log.i(TAG, "Model $modelId already downloaded")
            return false
        }

        // Check if already downloading
        val currentState = _downloadStates.value[modelId]
        if (currentState is DownloadState.Downloading) {
            Log.i(TAG, "Model $modelId already downloading")
            return false
        }

        // Check available storage
        val availableSpace = modelsDir.usableSpace
        if (availableSpace < modelInfo.sizeBytes * 1.1) { // 10% buffer
            updateDownloadState(modelId, DownloadState.Failed("Insufficient storage space"))
            return false
        }

        Log.i(TAG, "Starting download for model: $modelId")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString("model_id", modelId)
            .putString("download_url", modelInfo.downloadUrl)
            .putString("file_name", modelInfo.fileName)
            .putLong("file_size", modelInfo.sizeBytes)
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

        return true
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
     */
    fun getRecommendedModel(): ModelInfo {
        val availableRam = Runtime.getRuntime().maxMemory() / (1024 * 1024)

        return when {
            availableRam >= 3072 -> AVAILABLE_MODELS["gemma3-1b-int8"]!!
            else -> AVAILABLE_MODELS["gemma3-1b-int4"]!!
        }
    }

    private fun updateDownloadState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }

    private fun observeDownloadProgress(modelId: String, workId: String) {
        // WorkManager observation would be set up here
        // For now, the worker updates state directly via a shared mechanism
    }

    /**
     * Gets total disk space used by models in MB.
     */
    fun getTotalModelSizeMB(): Long {
        var total = 0L
        modelsDir.listFiles()?.forEach { file ->
            if (file.extension == "task") {
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
    val supportsVision: Boolean = false
) {
    val sizeMB: Long get() = sizeBytes / (1024 * 1024)
}

/**
 * Represents the download state of a model.
 */
sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
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
                return@withContext Result.failure()
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

        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            return@withContext Result.retry()
        }
    }
}
