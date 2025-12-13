package com.openautoglm.agent.model

import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.data.entities.InferenceType
import com.openautoglm.agent.data.entities.ModelConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Routes inference requests to the appropriate model client based on the inference mode.
 *
 * The router manages client instances, caching them by configuration ID for efficiency,
 * and handles routing logic between cloud and on-device inference modes.
 *
 * @property repository The agent repository for accessing model configurations.
 */
class InferenceRouter(
    private val repository: AgentRepository
) {
    /**
     * Cache of CloudModelClient instances keyed by config ID.
     * Clients are lazily created and reused for efficiency.
     */
    private val clientCache = mutableMapOf<String, CloudModelClient>()

    /**
     * Mutex for thread-safe access to the client cache.
     */
    private val cacheMutex = Mutex()

    /**
     * Tracks the last known configuration state for change detection.
     * Maps config ID to a hash of relevant config properties.
     */
    private val configHashes = mutableMapOf<String, Int>()

    /**
     * Routes an inference request to the appropriate model client based on the specified mode.
     *
     * @param messages The list of chat messages to send to the model.
     * @param mode The inference mode determining where computation runs.
     * @return The model's response including thinking, action, and metadata.
     * @throws InferenceException if the request fails or no suitable client is available.
     * @throws NotImplementedError if ON_DEVICE mode is requested (not yet supported).
     */
    suspend fun route(messages: List<ChatMessage>, mode: InferenceMode): ModelResponse {
        return when (mode) {
            InferenceMode.CLOUD -> routeToCloud(messages)
            InferenceMode.ON_DEVICE -> routeToOnDevice(messages)
            InferenceMode.AUTO -> routeAuto(messages)
        }
    }

    /**
     * Returns a list of available client types based on current configuration.
     *
     * @return List of available inference type names (e.g., "CLOUD", "ON_DEVICE").
     */
    suspend fun getAvailableClients(): List<String> {
        val availableClients = mutableListOf<String>()

        // Check for active cloud configurations
        val cloudConfigs = repository.getModelConfigsByType(InferenceType.CLOUD).first()
        if (cloudConfigs.any { it.isActive }) {
            availableClients.add(InferenceType.CLOUD.name)
        }

        // On-device is not yet available
        if (isOnDeviceAvailable()) {
            availableClients.add(InferenceType.ON_DEVICE.name)
        }

        return availableClients
    }

    /**
     * Checks if on-device inference is available.
     *
     * Currently returns false as MLC-LLM integration is not yet implemented.
     *
     * @return true if on-device inference is available, false otherwise.
     */
    fun isOnDeviceAvailable(): Boolean {
        // Placeholder for future MLC-LLM integration
        // Will check for:
        // - Downloaded model files
        // - Sufficient device resources (RAM, storage)
        // - Compatible device architecture
        return false
    }

    /**
     * Invalidates all cached clients, forcing recreation on next use.
     *
     * Call this method when model configurations have been updated
     * to ensure clients use the latest settings.
     */
    suspend fun invalidateCache() {
        cacheMutex.withLock {
            clientCache.clear()
            configHashes.clear()
        }
    }

    /**
     * Invalidates a specific cached client by configuration ID.
     *
     * @param configId The ID of the configuration whose client should be invalidated.
     */
    suspend fun invalidateClient(configId: String) {
        cacheMutex.withLock {
            clientCache.remove(configId)
            configHashes.remove(configId)
        }
    }

    /**
     * Routes the request to a cloud-based model client.
     */
    private suspend fun routeToCloud(messages: List<ChatMessage>): ModelResponse {
        val config = getActiveCloudConfig()
            ?: throw InferenceException("No active cloud model configuration found")

        val client = getOrCreateCloudClient(config)
        return try {
            client.request(messages)
        } catch (e: Exception) {
            throw InferenceException("Cloud inference failed: ${e.message}", e)
        }
    }

    /**
     * Routes the request to an on-device model client.
     *
     * Currently not implemented - throws NotImplementedError.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun routeToOnDevice(messages: List<ChatMessage>): ModelResponse {
        throw NotImplementedError(
            "On-device inference is not yet implemented. " +
            "MLC-LLM integration is planned for a future release."
        )
    }

    /**
     * Automatically routes the request based on heuristics.
     *
     * Current implementation defaults to cloud inference.
     * Future versions may consider:
     * - Task complexity (simple tasks -> on-device)
     * - Network connectivity (offline -> on-device)
     * - Battery level (low battery -> cloud for faster completion)
     * - Message content size (large images -> cloud)
     */
    private suspend fun routeAuto(messages: List<ChatMessage>): ModelResponse {
        // Current implementation: default to cloud if available
        val cloudConfigs = repository.getModelConfigsByType(InferenceType.CLOUD).first()
        if (cloudConfigs.any { it.isActive }) {
            return routeToCloud(messages)
        }

        // Fall back to on-device if cloud is not available
        if (isOnDeviceAvailable()) {
            return routeToOnDevice(messages)
        }

        throw InferenceException(
            "No inference backend available. " +
            "Please configure a cloud API or enable on-device inference."
        )
    }

    /**
     * Gets the first active cloud configuration.
     */
    private suspend fun getActiveCloudConfig(): ModelConfig? {
        val configs = repository.getModelConfigsByType(InferenceType.CLOUD).first()
        return configs.firstOrNull { it.isActive }
    }

    /**
     * Gets or creates a CloudModelClient for the given configuration.
     *
     * If the configuration has changed since the client was created,
     * the cached client is invalidated and a new one is created.
     */
    private suspend fun getOrCreateCloudClient(config: ModelConfig): CloudModelClient {
        return cacheMutex.withLock {
            val currentHash = computeConfigHash(config)
            val cachedHash = configHashes[config.id]

            // Check if config has changed
            if (cachedHash != null && cachedHash != currentHash) {
                clientCache.remove(config.id)
            }

            clientCache.getOrPut(config.id) {
                configHashes[config.id] = currentHash
                CloudModelClient(config)
            }
        }
    }

    /**
     * Computes a hash of the configuration properties that affect client behavior.
     *
     * Used for detecting configuration changes to invalidate cached clients.
     */
    private fun computeConfigHash(config: ModelConfig): Int {
        return listOf(
            config.baseUrl,
            config.apiKey,
            config.modelName,
            config.maxTokens,
            config.temperature,
            config.topP,
            config.frequencyPenalty
        ).hashCode()
    }
}

/**
 * Exception thrown when inference routing or execution fails.
 *
 * @param message Description of the error.
 * @param cause The underlying exception, if any.
 */
class InferenceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
