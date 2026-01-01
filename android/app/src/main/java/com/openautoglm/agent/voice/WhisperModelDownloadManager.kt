package com.openautoglm.agent.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * State of Whisper model download.
 */
sealed class WhisperModelDownloadState {
    object Idle : WhisperModelDownloadState()
    data class Downloading(val progress: Float, val downloadedMB: Float, val totalMB: Float) : WhisperModelDownloadState()
    object Completed : WhisperModelDownloadState()
    data class Error(val message: String) : WhisperModelDownloadState()
}

/**
 * Available Whisper models for download.
 */
data class WhisperModelInfo(
    val id: String,
    val name: String,
    val url: String,
    val sizeMB: Int,
    val description: String,
    val accuracy: String
)

/**
 * Manager for downloading and managing Whisper.cpp speech recognition models.
 *
 * Whisper models are in GGML format and support multilingual speech recognition
 * with high accuracy. The tiny model is recommended for mobile devices as it
 * balances accuracy and performance.
 */
class WhisperModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperModelDownload"
        private const val BUFFER_SIZE = 8192

        /**
         * Available models for download.
         * Models are hosted on Hugging Face.
         */
        val AVAILABLE_MODELS = listOf(
            WhisperModelInfo(
                id = "ggml-tiny.bin",
                name = "Tiny",
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
                sizeMB = 75,
                description = "Fastest, suitable for mobile",
                accuracy = "~70% WER"
            ),
            WhisperModelInfo(
                id = "ggml-base.bin",
                name = "Base",
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
                sizeMB = 142,
                description = "Balanced accuracy and speed",
                accuracy = "~60% WER"
            ),
            WhisperModelInfo(
                id = "ggml-small.bin",
                name = "Small",
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
                sizeMB = 466,
                description = "High accuracy, slower",
                accuracy = "~45% WER"
            )
        )

        /**
         * Get model info by ID.
         */
        fun getModelById(id: String): WhisperModelInfo? {
            return AVAILABLE_MODELS.find { it.id == id }
        }

        /**
         * Get the default (tiny) model.
         */
        fun getDefaultModel(): WhisperModelInfo = AVAILABLE_MODELS[0]
    }

    private val _downloadState = MutableStateFlow<WhisperModelDownloadState>(WhisperModelDownloadState.Idle)
    val downloadState: StateFlow<WhisperModelDownloadState> = _downloadState.asStateFlow()

    private val modelsDir = File(context.filesDir, "whisper-models")

    init {
        modelsDir.mkdirs()
    }

    /**
     * Check if a model is downloaded.
     */
    fun isModelDownloaded(modelInfo: WhisperModelInfo): Boolean {
        val modelFile = File(modelsDir, modelInfo.id)
        // Check that file exists and has reasonable size (at least 10MB)
        return modelFile.exists() && modelFile.length() > 10 * 1024 * 1024
    }

    /**
     * Check if a model is downloaded by ID.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val modelInfo = getModelById(modelId) ?: return false
        return isModelDownloaded(modelInfo)
    }

    /**
     * Get list of downloaded models.
     */
    fun getDownloadedModels(): List<WhisperModelInfo> {
        return AVAILABLE_MODELS.filter { isModelDownloaded(it) }
    }

    /**
     * Get the model file path.
     */
    fun getModelPath(modelInfo: WhisperModelInfo): File {
        return File(modelsDir, modelInfo.id)
    }

    /**
     * Get the model file path by ID.
     */
    fun getModelPath(modelId: String): File {
        return File(modelsDir, modelId)
    }

    /**
     * Download a model.
     */
    suspend fun downloadModel(modelInfo: WhisperModelInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _downloadState.value = WhisperModelDownloadState.Downloading(0f, 0f, modelInfo.sizeMB.toFloat())

                val modelFile = File(modelsDir, modelInfo.id)
                val tempFile = File(modelsDir, "${modelInfo.id}.tmp")

                // Download the model file
                Log.i(TAG, "Downloading model from: ${modelInfo.url}")
                downloadFile(modelInfo.url, tempFile, modelInfo.sizeMB.toFloat())

                // Rename temp file to final name
                if (tempFile.exists()) {
                    if (modelFile.exists()) {
                        modelFile.delete()
                    }
                    tempFile.renameTo(modelFile)
                }

                // Verify download
                if (isModelDownloaded(modelInfo)) {
                    Log.i(TAG, "Model downloaded successfully: ${modelInfo.id}")
                    _downloadState.value = WhisperModelDownloadState.Completed
                    true
                } else {
                    Log.e(TAG, "Model download failed - file missing or too small")
                    _downloadState.value = WhisperModelDownloadState.Error("Model download failed")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model", e)
                _downloadState.value = WhisperModelDownloadState.Error(e.message ?: "Unknown error")
                false
            }
        }
    }

    /**
     * Delete a downloaded model.
     */
    suspend fun deleteModel(modelInfo: WhisperModelInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelsDir, modelInfo.id)
                if (modelFile.exists()) {
                    modelFile.delete()
                    Log.i(TAG, "Model deleted: ${modelInfo.id}")
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete model", e)
                false
            }
        }
    }

    /**
     * Reset download state to idle.
     */
    fun resetState() {
        _downloadState.value = WhisperModelDownloadState.Idle
    }

    private fun downloadFile(urlString: String, outputFile: File, totalSizeMB: Float) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000 // Longer timeout for large files
        connection.instanceFollowRedirects = true

        try {
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP $responseCode")
            }

            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress
                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        } else {
                            downloadedBytes.toFloat() / (totalSizeMB * 1024 * 1024)
                        }
                        val downloadedMB = downloadedBytes.toFloat() / (1024 * 1024)

                        _downloadState.value = WhisperModelDownloadState.Downloading(
                            progress = progress.coerceIn(0f, 1f),
                            downloadedMB = downloadedMB,
                            totalMB = totalSizeMB
                        )
                    }
                }
            }

            Log.i(TAG, "Download complete: ${outputFile.absolutePath} (${downloadedBytes / 1024 / 1024} MB)")
        } finally {
            connection.disconnect()
        }
    }
}
