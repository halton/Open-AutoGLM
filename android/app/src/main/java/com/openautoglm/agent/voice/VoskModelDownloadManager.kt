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
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * State of Vosk model download.
 */
sealed class VoskModelDownloadState {
    object Idle : VoskModelDownloadState()
    data class Downloading(val progress: Float, val downloadedMB: Float, val totalMB: Float) : VoskModelDownloadState()
    object Extracting : VoskModelDownloadState()
    object Completed : VoskModelDownloadState()
    data class Error(val message: String) : VoskModelDownloadState()
}

/**
 * Available Vosk models for download.
 */
data class VoskModelInfo(
    val id: String,
    val name: String,
    val locale: Locale,
    val url: String,
    val sizeMB: Int,
    val description: String
)

/**
 * Manager for downloading and managing Vosk speech recognition models.
 */
class VoskModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskModelDownload"
        private const val BUFFER_SIZE = 8192

        /**
         * Available models for download.
         */
        val AVAILABLE_MODELS = listOf(
            VoskModelInfo(
                id = "vosk-model-small-cn-0.22",
                name = "Chinese (Simplified)",
                locale = Locale("zh", "CN"),
                url = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
                sizeMB = 42,
                description = "Lightweight Chinese model for Android"
            ),
            VoskModelInfo(
                id = "vosk-model-small-en-us-0.15",
                name = "English (US)",
                locale = Locale.US,
                url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                sizeMB = 40,
                description = "Lightweight English model for Android"
            )
        )

        /**
         * Get model info by locale.
         */
        fun getModelForLocale(locale: Locale): VoskModelInfo? {
            return AVAILABLE_MODELS.find { it.locale.language == locale.language }
        }
    }

    private val _downloadState = MutableStateFlow<VoskModelDownloadState>(VoskModelDownloadState.Idle)
    val downloadState: StateFlow<VoskModelDownloadState> = _downloadState.asStateFlow()

    private val modelsDir = File(context.filesDir, "vosk-models")

    init {
        modelsDir.mkdirs()
    }

    /**
     * Check if a model is downloaded.
     */
    fun isModelDownloaded(modelInfo: VoskModelInfo): Boolean {
        val modelDir = File(modelsDir, modelInfo.id)
        return modelDir.exists() && File(modelDir, "am/final.mdl").exists()
    }

    /**
     * Check if a model is downloaded by locale.
     */
    fun isModelDownloaded(locale: Locale): Boolean {
        val modelInfo = getModelForLocale(locale) ?: return false
        return isModelDownloaded(modelInfo)
    }

    /**
     * Get list of downloaded models.
     */
    fun getDownloadedModels(): List<VoskModelInfo> {
        return AVAILABLE_MODELS.filter { isModelDownloaded(it) }
    }

    /**
     * Get the model directory path.
     */
    fun getModelPath(modelInfo: VoskModelInfo): File {
        return File(modelsDir, modelInfo.id)
    }

    /**
     * Download a model.
     */
    suspend fun downloadModel(modelInfo: VoskModelInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _downloadState.value = VoskModelDownloadState.Downloading(0f, 0f, modelInfo.sizeMB.toFloat())

                val zipFile = File(context.cacheDir, "${modelInfo.id}.zip")
                val modelDir = File(modelsDir, modelInfo.id)

                // Download the zip file
                Log.i(TAG, "Downloading model from: ${modelInfo.url}")
                downloadFile(modelInfo.url, zipFile, modelInfo.sizeMB.toFloat())

                // Extract the zip file
                _downloadState.value = VoskModelDownloadState.Extracting
                Log.i(TAG, "Extracting model to: ${modelDir.absolutePath}")
                extractZip(zipFile, modelsDir)

                // Clean up zip file
                zipFile.delete()

                // Verify extraction
                if (isModelDownloaded(modelInfo)) {
                    Log.i(TAG, "Model downloaded successfully: ${modelInfo.id}")
                    _downloadState.value = VoskModelDownloadState.Completed
                    true
                } else {
                    Log.e(TAG, "Model extraction failed - essential files missing")
                    _downloadState.value = VoskModelDownloadState.Error("Model extraction failed")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model", e)
                _downloadState.value = VoskModelDownloadState.Error(e.message ?: "Unknown error")
                false
            }
        }
    }

    /**
     * Delete a downloaded model.
     */
    suspend fun deleteModel(modelInfo: VoskModelInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelDir = File(modelsDir, modelInfo.id)
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
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
        _downloadState.value = VoskModelDownloadState.Idle
    }

    private fun downloadFile(urlString: String, outputFile: File, totalSizeMB: Float) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

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

                        _downloadState.value = VoskModelDownloadState.Downloading(
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

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                val destFile = File(destDir, entry.name)

                // Security check: prevent zip slip vulnerability
                val destDirCanonical = destDir.canonicalPath
                val destFileCanonical = destFile.canonicalPath
                if (!destFileCanonical.startsWith(destDirCanonical + File.separator)) {
                    throw SecurityException("Zip entry outside target directory: ${entry.name}")
                }

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            output.write(buffer, 0, len)
                        }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
