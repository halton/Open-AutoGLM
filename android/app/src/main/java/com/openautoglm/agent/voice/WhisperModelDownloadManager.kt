package com.openautoglm.agent.voice

import android.content.Context
import android.util.Log
import com.openautoglm.agent.inference.DownloadMirror
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
    data class Downloading(
        val modelId: String,
        val progress: Float,
        val downloadedMB: Float,
        val totalMB: Float
    ) : WhisperModelDownloadState()
    data class Completed(val modelId: String) : WhisperModelDownloadState()
    data class Error(val modelId: String, val message: String) : WhisperModelDownloadState()
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
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L

        /**
         * GitHub fallback URLs for Whisper models (when HuggingFace is not accessible).
         */
        private val GITHUB_FALLBACK_URLS = mapOf(
            "ggml-tiny.bin" to "https://github.com/ggerganov/whisper.cpp/releases/download/v1.5.4/ggml-tiny.bin",
            "ggml-base.bin" to "https://github.com/ggerganov/whisper.cpp/releases/download/v1.5.4/ggml-base.bin",
            "ggml-small.bin" to "https://github.com/ggerganov/whisper.cpp/releases/download/v1.5.4/ggml-small.bin"
        )

        /**
         * Available models for download.
         * Primary source is Hugging Face, with GitHub as fallback.
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

        /**
         * Get the download URL for a model using the specified mirror.
         */
        fun getDownloadUrl(modelId: String, mirror: DownloadMirror): String {
            val model = getModelById(modelId) ?: return ""
            return model.url.replace("https://huggingface.co", mirror.baseUrl)
        }

        /**
         * Get the GitHub fallback URL for a model.
         */
        fun getGitHubFallbackUrl(modelId: String): String? {
            return GITHUB_FALLBACK_URLS[modelId]
        }
    }

    private val prefs = context.getSharedPreferences("whisper_download_prefs", Context.MODE_PRIVATE)

    private val _downloadState = MutableStateFlow<WhisperModelDownloadState>(WhisperModelDownloadState.Idle)
    val downloadState: StateFlow<WhisperModelDownloadState> = _downloadState.asStateFlow()

    // Use same mirror as on-device models
    private val _selectedMirror = MutableStateFlow(
        DownloadMirror.fromOrdinal(prefs.getInt("selected_mirror", 0))
    )
    val selectedMirror: StateFlow<DownloadMirror> = _selectedMirror.asStateFlow()

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
     * Sets the download mirror to use.
     */
    fun setMirror(mirror: DownloadMirror) {
        _selectedMirror.value = mirror
        prefs.edit().putInt("selected_mirror", mirror.ordinal).apply()
        Log.i(TAG, "Whisper mirror set to: ${mirror.displayName}")
    }

    /**
     * Download a model with retry and mirror fallback.
     * Uses the selected mirror first, then falls back to GitHub releases.
     */
    suspend fun downloadModel(modelInfo: WhisperModelInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _downloadState.value = WhisperModelDownloadState.Downloading(
                    modelId = modelInfo.id,
                    progress = 0f,
                    downloadedMB = 0f,
                    totalMB = modelInfo.sizeMB.toFloat()
                )

                val modelFile = File(modelsDir, modelInfo.id)
                val tempFile = File(modelsDir, "${modelInfo.id}.tmp")

                // Build list of URLs to try: selected mirror first, then other mirrors, then GitHub fallback
                val urlsToTry = mutableListOf<String>()

                // Add URL with selected mirror
                urlsToTry.add(getDownloadUrl(modelInfo.id, _selectedMirror.value))

                // Add URLs with other mirrors
                for (mirror in DownloadMirror.entries) {
                    if (mirror != _selectedMirror.value) {
                        urlsToTry.add(getDownloadUrl(modelInfo.id, mirror))
                    }
                }

                // Add GitHub fallback
                getGitHubFallbackUrl(modelInfo.id)?.let { urlsToTry.add(it) }

                var lastError: Exception? = null

                // Try each URL
                for (mirrorUrl in urlsToTry) {
                    Log.i(TAG, "Trying URL: $mirrorUrl")

                    // Retry each URL a few times
                    for (attempt in 1..MAX_RETRIES) {
                        try {
                            Log.i(TAG, "Download attempt $attempt/$MAX_RETRIES from: $mirrorUrl")
                            downloadFile(mirrorUrl, tempFile, modelInfo.sizeMB.toFloat(), modelInfo.id)

                            // Rename temp file to final name
                            if (tempFile.exists() && tempFile.length() > 10 * 1024 * 1024) {
                                if (modelFile.exists()) {
                                    modelFile.delete()
                                }
                                tempFile.renameTo(modelFile)

                                // Verify download
                                if (isModelDownloaded(modelInfo)) {
                                    Log.i(TAG, "Model downloaded successfully: ${modelInfo.id}")
                                    _downloadState.value = WhisperModelDownloadState.Completed(modelInfo.id)
                                    return@withContext true
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Attempt $attempt failed: ${e.message}")
                            lastError = e

                            // Clean up temp file
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }

                            // Wait before retry
                            if (attempt < MAX_RETRIES) {
                                Thread.sleep(RETRY_DELAY_MS * attempt)
                            }
                        }
                    }
                    Log.w(TAG, "All retries failed for URL: $mirrorUrl")
                }

                Log.e(TAG, "All mirrors failed for model: ${modelInfo.id}", lastError)
                _downloadState.value = WhisperModelDownloadState.Error(
                    modelId = modelInfo.id,
                    message = lastError?.message ?: "Download failed from all mirrors"
                )
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model", e)
                _downloadState.value = WhisperModelDownloadState.Error(
                    modelId = modelInfo.id,
                    message = e.message ?: "Unknown error"
                )
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

    private fun downloadFile(urlString: String, outputFile: File, totalSizeMB: Float, modelId: String) {
        var currentUrl = urlString
        var redirectCount = 0
        val maxRedirects = 10

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)

            // Use hostname directly - let the system handle DNS resolution
            // The previous IPv4-only approach broke HTTPS hostname verification
            Log.d(TAG, "Connecting to: $currentUrl")

            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 60000  // Increased timeout
            connection.readTimeout = 120000    // Increased for large files
            connection.instanceFollowRedirects = true  // Let Java handle redirects

            connection.setRequestProperty("User-Agent", "Open-AutoGLM/1.0 (Android)")
            connection.setRequestProperty("Accept", "*/*")

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
                                modelId = modelId,
                                progress = progress.coerceIn(0f, 1f),
                                downloadedMB = downloadedMB,
                                totalMB = totalSizeMB
                            )
                        }
                    }
                }

                Log.i(TAG, "Download complete: ${outputFile.absolutePath} (${downloadedBytes / 1024 / 1024} MB)")
                return
            } finally {
                connection.disconnect()
            }
        }

        throw Exception("Too many redirects")
    }
}
