package com.openautoglm.agent.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ChatRole
import com.openautoglm.agent.model.ContentPart
import com.openautoglm.agent.model.MessageContent
import com.openautoglm.agent.model.ModelClient
import com.openautoglm.agent.model.ModelResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device inference client using Google MediaPipe LLM Inference API.
 *
 * This implementation provides local VLM inference using quantized models,
 * enabling offline operation and enhanced privacy. Supports multimodal
 * input with text and images.
 *
 * Supported models:
 * - Gemma-3 1B (4-bit quantized, ~700MB) - Primary for most devices
 * - Gemma-3n E2B (~1GB) - For higher capability
 * - Gemma-2 2B (~1.5GB) - Alternative option
 *
 * @param context Application context
 */
class OnDeviceInference(
    private val context: Context
) : ModelClient {

    companion object {
        private const val TAG = "OnDeviceInference"

        // Model file names (MediaPipe .task format)
        const val GEMMA3_1B_MODEL = "gemma3-1b-it-int4.task"
        const val GEMMA3N_E2B_MODEL = "gemma3n-e2b-it-int4.task"
        const val GEMMA2_2B_MODEL = "gemma2-2b-it-int4.task"

        // Model directories
        private const val MODELS_DIR = "models"

        // Inference parameters
        private const val DEFAULT_MAX_TOKENS = 1024
        private const val DEFAULT_TOP_K = 40
    }

    // MediaPipe LLM Inference engine
    private var llmInference: LlmInference? = null
    private var currentModelPath: String? = null

    /**
     * Checks if on-device inference is available.
     *
     * Returns true if at least one model is downloaded.
     */
    fun isAvailable(): Boolean {
        return try {
            val modelFile = getAvailableModel()
            modelFile != null
        } catch (e: Exception) {
            Log.w(TAG, "On-device inference not available: ${e.message}")
            false
        }
    }

    /**
     * Gets the path of the best available model for the current device.
     *
     * @return Model file if available, null otherwise
     */
    fun getAvailableModel(): File? {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) return null

        // Check for models in order of preference
        val modelOrder = listOf(GEMMA3_1B_MODEL, GEMMA3N_E2B_MODEL, GEMMA2_2B_MODEL)

        for (modelName in modelOrder) {
            val modelFile = File(modelsDir, modelName)
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Found model: ${modelFile.absolutePath}")
                return modelFile
            }
        }

        // Also check for any .task file
        val taskFiles = modelsDir.listFiles { file ->
            file.extension.equals("task", ignoreCase = true)
        }

        return taskFiles?.firstOrNull()
    }

    /**
     * Gets the currently loaded model name.
     */
    fun getCurrentModelName(): String? {
        return currentModelPath?.let { File(it).name }
    }

    /**
     * Sends messages to the on-device model and returns the response.
     */
    override suspend fun request(messages: List<ChatMessage>): ModelResponse {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()

            try {
                val modelFile = getAvailableModel()
                    ?: throw OnDeviceInferenceException("No model available. Please download a model first.")

                // Initialize or reinitialize if model changed
                if (currentModelPath != modelFile.absolutePath || llmInference == null) {
                    initializeModel(modelFile.absolutePath)
                    currentModelPath = modelFile.absolutePath
                }

                val inference = llmInference
                    ?: throw OnDeviceInferenceException("Failed to initialize LLM inference engine")

                // Extract image if present
                val imageBitmap = extractImageFromMessages(messages)

                // Build prompt from messages
                val prompt = buildPrompt(messages)

                Log.d(TAG, "Running inference, prompt length: ${prompt.length}, has image: ${imageBitmap != null}")

                // Run inference
                val rawOutput = if (imageBitmap != null) {
                    runVisionInference(inference, prompt, imageBitmap)
                } else {
                    runTextInference(inference, prompt)
                }

                val inferenceTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "Inference completed in ${inferenceTime}ms")

                // Parse response
                parseModelResponse(rawOutput, inferenceTime)

            } catch (e: Exception) {
                Log.e(TAG, "On-device inference failed: ${e.message}", e)
                throw OnDeviceInferenceException("Inference failed: ${e.message}", e)
            }
        }
    }

    /**
     * Initializes the MediaPipe LLM Inference engine.
     */
    private fun initializeModel(modelPath: String) {
        Log.i(TAG, "Initializing model: $modelPath")

        // Release previous instance if exists
        llmInference?.close()

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(DEFAULT_MAX_TOKENS)
                .setMaxTopK(DEFAULT_TOP_K)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "Model initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model: ${e.message}", e)
            throw OnDeviceInferenceException("Failed to initialize model: ${e.message}", e)
        }
    }

    /**
     * Runs text-only inference.
     */
    private fun runTextInference(inference: LlmInference, prompt: String): String {
        return inference.generateResponse(prompt)
    }

    /**
     * Runs vision inference with image using MediaPipe session API.
     */
    private fun runVisionInference(
        inference: LlmInference,
        prompt: String,
        image: Bitmap
    ): String {
        // Convert Bitmap to MPImage
        val mpImage: MPImage = BitmapImageBuilder(image).build()

        // Create session with vision modality enabled
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(true)
                    .build()
            )
            .build()

        val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)

        return try {
            // Add the image and text query
            session.addImage(mpImage)
            session.addQueryChunk(prompt)

            // Generate response
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    /**
     * Builds a prompt string from chat messages.
     */
    private fun buildPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()

        for (message in messages) {
            when (message.role) {
                ChatRole.SYSTEM -> {
                    sb.append("<start_of_turn>system\n")
                    sb.append(getTextContent(message))
                    sb.append("<end_of_turn>\n")
                }
                ChatRole.USER -> {
                    sb.append("<start_of_turn>user\n")
                    sb.append(getTextContent(message))
                    sb.append("<end_of_turn>\n")
                }
                ChatRole.ASSISTANT -> {
                    sb.append("<start_of_turn>model\n")
                    sb.append(getTextContent(message))
                    sb.append("<end_of_turn>\n")
                }
            }
        }

        // Add model turn to start generation
        sb.append("<start_of_turn>model\n")

        return sb.toString()
    }

    /**
     * Gets text content from a message, filtering out image parts.
     */
    private fun getTextContent(message: ChatMessage): String {
        return when (val content = message.content) {
            is MessageContent.Text -> content.text
            is MessageContent.Parts -> {
                content.parts
                    .filterIsInstance<ContentPart.TextPart>()
                    .joinToString("\n") { it.text }
            }
        }
    }

    /**
     * Extracts bitmap image from messages if present.
     */
    private fun extractImageFromMessages(messages: List<ChatMessage>): Bitmap? {
        for (message in messages) {
            val content = message.content
            if (content is MessageContent.Parts) {
                for (part in content.parts) {
                    if (part is ContentPart.ImageUrlPart) {
                        val url = part.imageUrl.url
                        // Check if it's a base64 data URL
                        if (url.startsWith("data:image/")) {
                            val base64Start = url.indexOf(",")
                            if (base64Start != -1) {
                                val base64Data = url.substring(base64Start + 1)
                                return decodeBase64ToBitmap(base64Data)
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Decodes base64 string to Bitmap.
     */
    private fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 image: ${e.message}")
            null
        }
    }

    /**
     * Parses the raw model output into a ModelResponse.
     */
    private fun parseModelResponse(rawOutput: String, inferenceTimeMs: Long): ModelResponse {
        val (thinking, action) = parseThinkingAndAction(rawOutput)

        return ModelResponse(
            thinking = thinking,
            action = action,
            rawContent = rawOutput,
            inferenceTimeMs = inferenceTimeMs,
            modelUsed = getCurrentModelName() ?: "on-device",
            tokenCount = estimateTokenCount(rawOutput)
        )
    }

    /**
     * Parses thinking and action from model output.
     */
    private fun parseThinkingAndAction(content: String): Pair<String, String> {
        // Rule 1: Check for finish(message=
        if (content.contains("finish(message=")) {
            val parts = content.split("finish(message=", limit = 2)
            val thinking = parts[0].trim()
            val action = "finish(message=" + parts[1]
            return Pair(thinking, action)
        }

        // Rule 2: Check for do(action=
        if (content.contains("do(action=")) {
            val parts = content.split("do(action=", limit = 2)
            val thinking = parts[0].trim()
            val action = "do(action=" + parts[1]
            return Pair(thinking, action)
        }

        // Rule 3: Fallback to legacy XML tag parsing
        if (content.contains("<answer>")) {
            val thinkRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
            val answerRegex = Regex("<answer>(.*?)</answer>", RegexOption.DOT_MATCHES_ALL)

            val thinkMatch = thinkRegex.find(content)
            val answerMatch = answerRegex.find(content)

            val thinking = thinkMatch?.groupValues?.get(1)?.trim() ?: ""
            val action = answerMatch?.groupValues?.get(1)?.trim() ?: content
            return Pair(thinking, action)
        }

        // Rule 4: No markers found, return content as action
        return Pair("", content)
    }

    /**
     * Estimates token count from text (rough approximation).
     */
    private fun estimateTokenCount(text: String): Int {
        // Rough estimate: ~4 characters per token for English/Chinese mix
        return text.length / 4
    }

    /**
     * Releases resources and unloads the model.
     */
    fun release() {
        llmInference?.close()
        llmInference = null
        currentModelPath = null
        Log.i(TAG, "Model released")
    }
}

/**
 * Exception thrown when on-device inference fails.
 */
class OnDeviceInferenceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
