package com.openautoglm.agent.inference

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.data.entities.InferenceType
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ChatRole
import com.openautoglm.agent.model.CloudModelClient
import com.openautoglm.agent.model.InferenceException
import com.openautoglm.agent.model.ModelClient
import com.openautoglm.agent.model.ModelResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Full implementation of inference routing with intelligent backend selection.
 *
 * Routing logic (from research.md):
 * 1. If battery < 20% AND not charging: route to CLOUD (preserve battery)
 * 2. Else if task contains sensitive data keywords: route to ON_DEVICE (privacy)
 * 3. Else if task complexity score > 0.7: route to CLOUD (accuracy)
 * 4. Else if network unavailable: route to ON_DEVICE (availability)
 * 5. Else: route to ON_DEVICE (default - speed + privacy)
 *
 * @param context Application context
 * @param repository Agent repository for configuration
 */
class InferenceRouterImpl(
    private val context: Context,
    private val repository: AgentRepository
) : ModelClient {
    companion object {
        private const val TAG = "InferenceRouterImpl"

        // Battery threshold
        private const val LOW_BATTERY_THRESHOLD = 20

        // Complexity score threshold for cloud routing
        private const val CLOUD_COMPLEXITY_THRESHOLD = 0.7f

        // Sensitive data keywords (should never be sent to cloud)
        private val SENSITIVE_KEYWORDS = setOf(
            // Chinese
            "密码", "银行卡", "身份证", "手机号", "验证码",
            "支付", "转账", "余额", "账户", "信用卡",
            // English
            "password", "credit card", "ssn", "social security",
            "bank account", "pin", "cvv", "verification code"
        )

        // Keywords that increase complexity score
        private val COMPLEX_TASK_KEYWORDS = mapOf(
            // Multi-app tasks
            "compare" to 0.2f, "对比" to 0.2f,
            "across apps" to 0.3f, "多个应用" to 0.3f,
            // Financial tasks
            "purchase" to 0.3f, "购买" to 0.3f,
            "payment" to 0.3f, "支付" to 0.3f,
            "transaction" to 0.3f, "交易" to 0.3f,
            // Multi-step tasks
            "then" to 0.1f, "然后" to 0.1f,
            "after that" to 0.1f, "之后" to 0.1f,
            "and also" to 0.1f, "并且" to 0.1f
        )

        // Keywords that decrease complexity (simple tasks)
        private val SIMPLE_TASK_KEYWORDS = mapOf(
            // Text extraction
            "extract text" to -0.3f, "提取文字" to -0.3f,
            "ocr" to -0.3f, "文字识别" to -0.3f,
            "read text" to -0.2f, "读取文字" to -0.2f,
            // Simple queries
            "what is" to -0.2f, "这是什么" to -0.2f,
            "show me" to -0.1f, "显示" to -0.1f
        )
    }

    // Inference clients
    // Prefer llama.cpp (AutoGLM GGUF) over MediaPipe (Gemma)
    private var llamaCppInference: LlamaCppInference? = null
    private var mediaPipeInference: OnDeviceInference? = null
    private var cloudInference: CloudInference? = null

    // Model download manager for checking available models
    private val modelDownloadManager = ModelDownloadManager(context)

    // Secure storage for reading user's configured inference mode
    private val secureStorage = SecureKeyStorage(context)

    // Client cache
    private val clientMutex = Mutex()

    /**
     * Implements ModelClient interface.
     * Routes based on user's configured inference mode from Settings.
     */
    override suspend fun request(messages: List<ChatMessage>): ModelResponse {
        val configuredMode = secureStorage.getInferenceMode()
        Log.i(TAG, "ModelClient.request() called, using configured mode: $configuredMode")
        return route(messages, configuredMode)
    }

    /**
     * Routes an inference request to the appropriate backend.
     *
     * @param messages The chat messages to send
     * @param mode The requested inference mode
     * @return The model response
     */
    suspend fun route(messages: List<ChatMessage>, mode: InferenceMode): ModelResponse {
        return when (mode) {
            InferenceMode.CLOUD -> routeToCloud(messages)
            InferenceMode.ON_DEVICE -> routeToOnDevice(messages)
            InferenceMode.AUTO -> routeAuto(messages)
        }
    }

    /**
     * Routes using automatic backend selection based on context.
     */
    private suspend fun routeAuto(messages: List<ChatMessage>): ModelResponse {
        val taskDescription = extractTaskDescription(messages)

        Log.d(TAG, "Auto-routing task: ${taskDescription.take(100)}...")

        // Rule 1: Low battery + not charging -> Cloud (preserve battery)
        if (isBatteryLow() && !isCharging()) {
            Log.i(TAG, "Low battery, routing to cloud")
            return tryCloudWithFallback(messages)
        }

        // Rule 2: Sensitive data -> On-device (privacy)
        if (containsSensitiveData(taskDescription)) {
            Log.i(TAG, "Sensitive data detected, routing to on-device")
            return tryOnDeviceWithFallback(messages, allowCloudFallback = false)
        }

        // Rule 3: High complexity -> Cloud (accuracy)
        val complexityScore = calculateComplexityScore(taskDescription)
        Log.d(TAG, "Task complexity score: $complexityScore")
        if (complexityScore > CLOUD_COMPLEXITY_THRESHOLD) {
            Log.i(TAG, "High complexity task, routing to cloud")
            return tryCloudWithFallback(messages)
        }

        // Rule 4: No network -> On-device (availability)
        if (!isNetworkAvailable()) {
            Log.i(TAG, "No network, routing to on-device")
            return tryOnDeviceWithFallback(messages, allowCloudFallback = false)
        }

        // Rule 5: Default -> On-device (speed + privacy)
        Log.i(TAG, "Default routing to on-device")
        return tryOnDeviceWithFallback(messages, allowCloudFallback = true)
    }

    /**
     * Routes explicitly to cloud inference.
     */
    private suspend fun routeToCloud(messages: List<ChatMessage>): ModelResponse {
        val client = getCloudClient()
            ?: throw InferenceException("Cloud inference not configured")
        return client.request(messages)
    }

    /**
     * Routes explicitly to on-device inference.
     */
    private suspend fun routeToOnDevice(messages: List<ChatMessage>): ModelResponse {
        val client = getOnDeviceClient()
            ?: throw InferenceException("On-device inference not available. Please download a model.")
        return client.request(messages)
    }

    /**
     * Tries cloud inference with fallback to on-device.
     */
    private suspend fun tryCloudWithFallback(messages: List<ChatMessage>): ModelResponse {
        val cloudClient = getCloudClient()
        if (cloudClient != null) {
            try {
                return cloudClient.request(messages)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud inference failed, trying on-device: ${e.message}")
            }
        }

        // Fallback to on-device
        val onDeviceClient = getOnDeviceClient()
            ?: throw InferenceException("No inference backend available")
        return onDeviceClient.request(messages)
    }

    /**
     * Tries on-device inference with optional cloud fallback.
     */
    private suspend fun tryOnDeviceWithFallback(
        messages: List<ChatMessage>,
        allowCloudFallback: Boolean
    ): ModelResponse {
        val onDeviceClient = getOnDeviceClient()
        if (onDeviceClient != null) {
            try {
                Log.i(TAG, "Attempting on-device inference...")
                return onDeviceClient.request(messages)
            } catch (e: Exception) {
                Log.w(TAG, "On-device inference failed: ${e.message}")
                if (!allowCloudFallback) {
                    throw e
                }
            }
        } else {
            Log.w(TAG, "No on-device client available")
        }

        if (!allowCloudFallback) {
            val ggufPath = modelDownloadManager.getGgufModelPath()
            val errorMsg = if (ggufPath != null && !LlamaCppInference.isNativeLibraryAvailable()) {
                "GGUF model downloaded but llama.cpp native library not available. " +
                "Please download a Gemma model (.task format) from Settings > Download Model for offline use."
            } else {
                "No on-device model available. Please download a model from Settings > Download Model."
            }
            throw InferenceException(errorMsg)
        }

        // Fallback to cloud
        Log.i(TAG, "Falling back to cloud inference...")
        val cloudClient = getCloudClient()
        if (cloudClient == null) {
            val ggufPath = modelDownloadManager.getGgufModelPath()
            val errorMsg = if (ggufPath != null && !LlamaCppInference.isNativeLibraryAvailable()) {
                "No inference available. GGUF model found but requires llama.cpp native library. " +
                "Cloud is also unavailable. Please download a Gemma model (.task) for offline use."
            } else {
                "No inference backend available. Please check network connection or download a model."
            }
            throw InferenceException(errorMsg)
        }
        return cloudClient.request(messages)
    }

    /**
     * Gets or creates the best available on-device inference client.
     * Prefers llama.cpp (AutoGLM GGUF) over MediaPipe (Gemma) for better quality.
     *
     * Priority order:
     * 1. llama.cpp with AutoGLM-Phone GGUF (same as cloud API)
     * 2. MediaPipe with Gemma (fallback for lower-end devices)
     */
    private suspend fun getOnDeviceClient(): com.openautoglm.agent.model.ModelClient? {
        return clientMutex.withLock {
            Log.i(TAG, "=== Checking for on-device models ===")

            // First try llama.cpp (GGUF models like AutoGLM-Phone)
            val ggufPath = modelDownloadManager.getGgufModelPath()
            Log.i(TAG, "GGUF model path: ${ggufPath ?: "not found"}")

            if (ggufPath != null) {
                if (llamaCppInference == null) {
                    llamaCppInference = LlamaCppInference(context)
                }
                val isLlamaAvailable = llamaCppInference?.isAvailable() == true
                Log.i(TAG, "llama.cpp native library available: ${LlamaCppInference.isNativeLibraryAvailable()}")
                Log.i(TAG, "llama.cpp inference available: $isLlamaAvailable")

                if (isLlamaAvailable) {
                    Log.i(TAG, "Using llama.cpp with GGUF model: $ggufPath")
                    return@withLock llamaCppInference
                } else if (!LlamaCppInference.isNativeLibraryAvailable()) {
                    Log.w(TAG, "GGUF model found but llama.cpp native library (libllama.so) not available!")
                    Log.w(TAG, "Please download a MediaPipe (.task) model instead, or build llama.cpp for Android")
                }
            }

            // Fallback to MediaPipe (Gemma .task models)
            val mediaPipePath = modelDownloadManager.getMediaPipeModelPath()
            Log.i(TAG, "MediaPipe model path: ${mediaPipePath ?: "not found"}")

            if (mediaPipePath != null) {
                if (mediaPipeInference == null) {
                    mediaPipeInference = OnDeviceInference(context)
                }
                val isMediaPipeAvailable = mediaPipeInference?.isAvailable() == true
                Log.i(TAG, "MediaPipe inference available: $isMediaPipeAvailable")

                if (isMediaPipeAvailable) {
                    Log.i(TAG, "Using MediaPipe with model: $mediaPipePath")
                    return@withLock mediaPipeInference
                }
            }

            Log.w(TAG, "=== No on-device model available ===")
            Log.w(TAG, "Downloaded models: ${modelDownloadManager.getDownloadedModels().map { it.id }}")
            Log.w(TAG, "To use offline: Download a Gemma model (MediaPipe .task format)")
            null
        }
    }

    /**
     * Gets the llama.cpp client specifically (for GGUF models).
     */
    private suspend fun getLlamaCppClient(): LlamaCppInference? {
        return clientMutex.withLock {
            val ggufPath = modelDownloadManager.getGgufModelPath()
            if (ggufPath == null) {
                return@withLock null
            }

            if (llamaCppInference == null) {
                llamaCppInference = LlamaCppInference(context)
            }
            if (llamaCppInference?.isAvailable() == true) {
                llamaCppInference
            } else {
                null
            }
        }
    }

    /**
     * Gets the MediaPipe client specifically (for .task models).
     */
    private suspend fun getMediaPipeClient(): OnDeviceInference? {
        return clientMutex.withLock {
            val mediaPipePath = modelDownloadManager.getMediaPipeModelPath()
            if (mediaPipePath == null) {
                return@withLock null
            }

            if (mediaPipeInference == null) {
                mediaPipeInference = OnDeviceInference(context)
            }
            if (mediaPipeInference?.isAvailable() == true) {
                mediaPipeInference
            } else {
                null
            }
        }
    }

    /**
     * Gets or creates the cloud inference client.
     */
    private suspend fun getCloudClient(): CloudInference? {
        return clientMutex.withLock {
            if (cloudInference == null) {
                cloudInference = CloudInference(context)
            }
            if (cloudInference?.isConfigured() == true) {
                cloudInference
            } else {
                null
            }
        }
    }

    /**
     * Extracts task description from messages.
     */
    private fun extractTaskDescription(messages: List<ChatMessage>): String {
        return messages
            .filter { it.role == ChatRole.USER }
            .joinToString(" ") { it.content.asText() }
    }

    /**
     * Checks if task description contains sensitive data.
     */
    private fun containsSensitiveData(text: String): Boolean {
        val lowerText = text.lowercase()
        return SENSITIVE_KEYWORDS.any { keyword ->
            lowerText.contains(keyword.lowercase())
        }
    }

    /**
     * Calculates complexity score for a task (0.0 - 1.0).
     */
    private fun calculateComplexityScore(text: String): Float {
        val lowerText = text.lowercase()
        var score = 0.5f // Base score

        // Add complexity for complex keywords
        COMPLEX_TASK_KEYWORDS.forEach { (keyword, weight) ->
            if (lowerText.contains(keyword.lowercase())) {
                score += weight
            }
        }

        // Reduce complexity for simple keywords
        SIMPLE_TASK_KEYWORDS.forEach { (keyword, weight) ->
            if (lowerText.contains(keyword.lowercase())) {
                score += weight
            }
        }

        // Count number of apps mentioned (heuristic)
        val appCount = countAppMentions(lowerText)
        score += when {
            appCount >= 3 -> 0.3f
            appCount == 2 -> 0.15f
            else -> 0f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Counts potential app mentions in text (heuristic).
     */
    private fun countAppMentions(text: String): Int {
        val appKeywords = listOf(
            "淘宝", "京东", "拼多多", "美团", "饿了么",
            "微信", "支付宝", "抖音", "快手", "12306",
            "taobao", "jd", "meituan", "wechat", "alipay",
            "amazon", "uber", "grab", "whatsapp"
        )
        return appKeywords.count { text.contains(it.lowercase()) }
    }

    /**
     * Checks if battery is below threshold.
     */
    private fun isBatteryLow(): Boolean {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        if (level == -1 || scale == -1) return false

        val batteryPercent = (level.toFloat() / scale.toFloat() * 100).toInt()
        return batteryPercent < LOW_BATTERY_THRESHOLD
    }

    /**
     * Checks if device is charging.
     */
    private fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * Checks if network is available.
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Checks if on-device inference is available.
     * Returns true if either llama.cpp (GGUF) or MediaPipe is available.
     */
    suspend fun isOnDeviceAvailable(): Boolean {
        val client = getOnDeviceClient()
        return client != null
    }

    /**
     * Checks if llama.cpp (GGUF) inference is available.
     */
    suspend fun isLlamaCppAvailable(): Boolean {
        val client = getLlamaCppClient()
        return client?.isAvailable() == true
    }

    /**
     * Checks if MediaPipe inference is available.
     */
    suspend fun isMediaPipeAvailable(): Boolean {
        val client = getMediaPipeClient()
        return client?.isAvailable() == true
    }

    /**
     * Checks if cloud inference is configured.
     */
    suspend fun isCloudConfigured(): Boolean {
        val client = getCloudClient()
        return client?.isConfigured() == true
    }

    /**
     * Gets list of available inference backends.
     */
    suspend fun getAvailableBackends(): List<String> {
        val backends = mutableListOf<String>()

        if (isLlamaCppAvailable()) {
            backends.add("LLAMA_CPP")
        }
        if (isMediaPipeAvailable()) {
            backends.add("MEDIAPIPE")
        }
        if (isCloudConfigured()) {
            backends.add("CLOUD")
        }

        return backends
    }

    /**
     * Gets info about the current on-device model.
     */
    fun getCurrentOnDeviceModelInfo(): String? {
        val ggufPath = modelDownloadManager.getGgufModelPath()
        if (ggufPath != null) {
            return "llama.cpp: ${java.io.File(ggufPath).name}"
        }

        val mediaPipePath = modelDownloadManager.getMediaPipeModelPath()
        if (mediaPipePath != null) {
            return "MediaPipe: ${java.io.File(mediaPipePath).name}"
        }

        return null
    }

    /**
     * Releases resources.
     */
    fun release() {
        llamaCppInference?.release()
        llamaCppInference = null
        mediaPipeInference?.release()
        mediaPipeInference = null
        cloudInference = null
    }
}
