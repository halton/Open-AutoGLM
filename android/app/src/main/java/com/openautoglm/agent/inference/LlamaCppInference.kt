package com.openautoglm.agent.inference

import android.content.Context
import android.util.Log
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
 * On-device inference client using llama.cpp via JNI.
 *
 * This implementation provides local LLM inference using GGUF quantized models,
 * enabling offline operation with the same model as cloud API (AutoGLM-Phone-9B).
 *
 * Supported models:
 * - AutoGLM-Phone-9B Q4_K_M (6.17GB) - Same as cloud API
 * - AutoGLM-Phone-9B Q2_K (4.04GB) - Smaller, faster
 * - Other GGUF models compatible with llama.cpp
 *
 * IMPORTANT: This requires llama.cpp native library to be built and included.
 * See: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
 *
 * To use this:
 * 1. Build llama.cpp for Android: cmake -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake ...
 * 2. Copy libllama.so to app/src/main/jniLibs/arm64-v8a/
 * 3. Download a GGUF model to device storage
 *
 * @param context Application context
 */
class LlamaCppInference(
    private val context: Context
) : ModelClient {

    companion object {
        private const val TAG = "LlamaCppInference"

        // Model file names (GGUF format)
        const val AUTOGLM_Q4_K_M = "AutoGLM-Phone-9B-Multilingual.Q4_K_M.gguf"
        const val AUTOGLM_Q2_K = "AutoGLM-Phone-9B-Multilingual.Q2_K.gguf"
        const val AUTOGLM_Q6_K = "AutoGLM-Phone-9B-Multilingual.Q6_K.gguf"

        // Model directories
        private const val MODELS_DIR = "models"

        // Inference parameters
        private const val DEFAULT_CONTEXT_LENGTH = 4096
        private const val DEFAULT_MAX_TOKENS = 1024

        // Native library availability flag
        private var nativeLibraryLoaded = false
        private var nativeLibraryError: String? = null

        init {
            try {
                System.loadLibrary("llama")
                nativeLibraryLoaded = true
                Log.i(TAG, "llama.cpp native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                nativeLibraryError = e.message
                Log.w(TAG, "llama.cpp native library not available: ${e.message}")
            }
        }

        /**
         * Checks if the llama.cpp native library is available.
         */
        fun isNativeLibraryAvailable(): Boolean = nativeLibraryLoaded
    }

    // Current model state
    private var currentModelPath: String? = null
    private var isModelLoaded = false

    // Native context pointer (managed by JNI)
    private var nativeContextPtr: Long = 0L

    /**
     * Checks if on-device inference is available.
     *
     * Returns true if native library is loaded AND at least one GGUF model is downloaded.
     */
    fun isAvailable(): Boolean {
        if (!nativeLibraryLoaded) {
            Log.d(TAG, "Native library not available")
            return false
        }

        val modelFile = getAvailableModel()
        if (modelFile == null) {
            Log.d(TAG, "No GGUF model available")
            return false
        }

        return true
    }

    /**
     * Gets the path of the best available GGUF model.
     *
     * @return Model file if available, null otherwise
     */
    fun getAvailableModel(): File? {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) return null

        // Check for models in order of preference (Q4_K_M is best balance)
        val modelOrder = listOf(AUTOGLM_Q4_K_M, AUTOGLM_Q6_K, AUTOGLM_Q2_K)

        for (modelName in modelOrder) {
            val modelFile = File(modelsDir, modelName)
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Found model: ${modelFile.absolutePath}")
                return modelFile
            }
        }

        // Also check for any .gguf file
        val ggufFiles = modelsDir.listFiles { file ->
            file.extension.equals("gguf", ignoreCase = true)
        }

        return ggufFiles?.firstOrNull()
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

            // Check if native library is available
            if (!nativeLibraryLoaded) {
                throw LlamaCppInferenceException(
                    "llama.cpp native library not available. " +
                    "Please build and include libllama.so. " +
                    "Error: $nativeLibraryError"
                )
            }

            try {
                val modelFile = getAvailableModel()
                    ?: throw LlamaCppInferenceException("No GGUF model available. Please download AutoGLM-Phone-9B first.")

                // Initialize or reinitialize if model changed
                if (currentModelPath != modelFile.absolutePath || !isModelLoaded) {
                    initializeModel(modelFile.absolutePath)
                    currentModelPath = modelFile.absolutePath
                }

                // Build prompt from messages
                val prompt = buildPrompt(messages)

                Log.d(TAG, "Running inference, prompt length: ${prompt.length}")

                // Run inference
                val rawOutput = nativeGenerate(
                    contextPtr = nativeContextPtr,
                    prompt = prompt,
                    maxTokens = DEFAULT_MAX_TOKENS
                )

                val inferenceTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "Inference completed in ${inferenceTime}ms")

                // Parse response
                parseModelResponse(rawOutput, inferenceTime)

            } catch (e: Exception) {
                Log.e(TAG, "llama.cpp inference failed: ${e.message}", e)
                throw LlamaCppInferenceException("Inference failed: ${e.message}", e)
            }
        }
    }

    /**
     * Initializes the llama.cpp engine with a model.
     */
    private fun initializeModel(modelPath: String) {
        Log.i(TAG, "Initializing model: $modelPath")

        // Release previous context if exists
        if (nativeContextPtr != 0L) {
            nativeFree(nativeContextPtr)
            nativeContextPtr = 0L
        }
        isModelLoaded = false

        try {
            nativeContextPtr = nativeLoadModel(
                modelPath = modelPath,
                contextLength = DEFAULT_CONTEXT_LENGTH,
                gpuLayers = 0 // CPU only for now, GPU layers can be enabled if device supports
            )

            if (nativeContextPtr == 0L) {
                throw LlamaCppInferenceException("Failed to load model: nativeLoadModel returned null")
            }

            isModelLoaded = true
            Log.i(TAG, "Model initialized successfully, context: $nativeContextPtr")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model: ${e.message}", e)
            throw LlamaCppInferenceException("Failed to initialize model: ${e.message}", e)
        }
    }

    /**
     * Builds a prompt string from chat messages.
     * Uses ChatML format which AutoGLM-Phone expects.
     */
    private fun buildPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()

        for (message in messages) {
            when (message.role) {
                ChatRole.SYSTEM -> {
                    sb.append("<|im_start|>system\n")
                    sb.append(getTextContent(message))
                    sb.append("<|im_end|>\n")
                }
                ChatRole.USER -> {
                    sb.append("<|im_start|>user\n")
                    sb.append(getTextContent(message))
                    sb.append("<|im_end|>\n")
                }
                ChatRole.ASSISTANT -> {
                    sb.append("<|im_start|>assistant\n")
                    sb.append(getTextContent(message))
                    sb.append("<|im_end|>\n")
                }
            }
        }

        // Add assistant turn to start generation
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    /**
     * Gets text content from a message, filtering out image parts.
     * Note: Vision support requires separate handling with llama.cpp's clip model.
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
     * Parses the raw model output into a ModelResponse.
     */
    private fun parseModelResponse(rawOutput: String, inferenceTimeMs: Long): ModelResponse {
        val (thinking, action) = parseThinkingAndAction(rawOutput)

        return ModelResponse(
            thinking = thinking,
            action = action,
            rawContent = rawOutput,
            inferenceTimeMs = inferenceTimeMs,
            modelUsed = getCurrentModelName() ?: "llama.cpp",
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
        if (nativeContextPtr != 0L) {
            nativeFree(nativeContextPtr)
            nativeContextPtr = 0L
        }
        currentModelPath = null
        isModelLoaded = false
        Log.i(TAG, "Model released")
    }

    // ==================== Native JNI Methods ====================
    // These must be implemented in libllama.so

    /**
     * Loads a GGUF model and returns a context pointer.
     *
     * @param modelPath Path to the GGUF model file
     * @param contextLength Maximum context length in tokens
     * @param gpuLayers Number of layers to offload to GPU (0 for CPU only)
     * @return Native context pointer, or 0 on failure
     */
    private external fun nativeLoadModel(
        modelPath: String,
        contextLength: Int,
        gpuLayers: Int
    ): Long

    /**
     * Generates text completion from a prompt.
     *
     * @param contextPtr Native context pointer from nativeLoadModel
     * @param prompt The input prompt
     * @param maxTokens Maximum tokens to generate
     * @return Generated text
     */
    private external fun nativeGenerate(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int
    ): String

    /**
     * Frees the native context and unloads the model.
     *
     * @param contextPtr Native context pointer to free
     */
    private external fun nativeFree(contextPtr: Long)
}

/**
 * Exception thrown when llama.cpp inference fails.
 */
class LlamaCppInferenceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
