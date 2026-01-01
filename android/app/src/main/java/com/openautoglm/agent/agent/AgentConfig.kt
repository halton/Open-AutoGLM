package com.openautoglm.agent.agent

import com.openautoglm.agent.data.entities.InferenceMode

/**
 * Configuration for the VLM Android Agent runtime behavior.
 *
 * This data class encapsulates all configurable parameters for agent execution,
 * including inference settings, retry behavior, timeouts, and language preferences.
 *
 * @property inferenceMode The inference mode (ON_DEVICE, CLOUD, or AUTO)
 * @property language Language code for prompts ("en" or "zh")
 * @property maxRetries Maximum number of retries for failed actions
 * @property actionTimeout Timeout in milliseconds for each action execution
 * @property maxStepsPerTask Maximum number of steps allowed per task
 * @property enableConfirmations Whether to prompt user for confirmations on sensitive actions
 * @property autoCompleteSimpleTasks Whether to auto-execute simple tasks without confirmation
 * @property defaultWaitDuration Default wait duration in milliseconds
 * @property screenshotQuality JPEG quality for screenshots (0-100)
 * @property logVerbosity Logging verbosity level (0=errors only, 1=info, 2=debug)
 */
data class AgentConfig(
    val inferenceMode: InferenceMode = InferenceMode.AUTO,
    val language: String = "zh",  // Default to Chinese for voice recognition
    val maxRetries: Int = 3,
    val actionTimeout: Long = 30_000L,  // 30 seconds
    val maxStepsPerTask: Int = 50,
    val enableConfirmations: Boolean = true,
    val autoCompleteSimpleTasks: Boolean = false,
    val defaultWaitDuration: Long = 2_000L,  // 2 seconds
    val screenshotQuality: Int = 85,
    val logVerbosity: Int = 1
) {
    companion object {
        /**
         * Default configuration for production use.
         */
        val DEFAULT = AgentConfig()

        /**
         * Configuration optimized for development and debugging.
         */
        val DEBUG = AgentConfig(
            inferenceMode = InferenceMode.CLOUD,
            maxRetries = 5,
            actionTimeout = 60_000L,  // 60 seconds for debugging
            maxStepsPerTask = 100,
            enableConfirmations = true,
            logVerbosity = 2
        )

        /**
         * Configuration optimized for on-device inference.
         */
        val ON_DEVICE = AgentConfig(
            inferenceMode = InferenceMode.ON_DEVICE,
            maxRetries = 2,
            actionTimeout = 20_000L,
            maxStepsPerTask = 30,
            enableConfirmations = true,
            screenshotQuality = 70,  // Lower quality for faster processing
            logVerbosity = 1
        )

        /**
         * Configuration optimized for cloud inference.
         */
        val CLOUD = AgentConfig(
            inferenceMode = InferenceMode.CLOUD,
            maxRetries = 3,
            actionTimeout = 30_000L,
            maxStepsPerTask = 50,
            enableConfirmations = true,
            screenshotQuality = 90,  // Higher quality for better VLM understanding
            logVerbosity = 1
        )

        /**
         * Configuration for privacy-focused users (all on-device, no confirmations).
         */
        val PRIVACY_FOCUSED = AgentConfig(
            inferenceMode = InferenceMode.ON_DEVICE,
            maxRetries = 2,
            actionTimeout = 20_000L,
            maxStepsPerTask = 30,
            enableConfirmations = false,  // Trust the agent
            autoCompleteSimpleTasks = true,
            screenshotQuality = 70,
            logVerbosity = 0  // Minimal logging
        )

        /**
         * Configuration for performance-optimized execution.
         */
        val FAST = AgentConfig(
            inferenceMode = InferenceMode.AUTO,
            maxRetries = 2,
            actionTimeout = 15_000L,
            maxStepsPerTask = 30,
            enableConfirmations = false,
            autoCompleteSimpleTasks = true,
            defaultWaitDuration = 1_000L,  // Shorter waits
            screenshotQuality = 75,
            logVerbosity = 0
        )
    }

    /**
     * Validates the configuration values.
     *
     * @throws IllegalArgumentException if any configuration value is invalid
     */
    fun validate() {
        require(language in setOf("en", "zh")) {
            "Language must be 'en' or 'zh', got: $language"
        }
        require(maxRetries in 0..10) {
            "maxRetries must be between 0 and 10, got: $maxRetries"
        }
        require(actionTimeout > 0) {
            "actionTimeout must be positive, got: $actionTimeout"
        }
        require(maxStepsPerTask in 1..200) {
            "maxStepsPerTask must be between 1 and 200, got: $maxStepsPerTask"
        }
        require(defaultWaitDuration > 0) {
            "defaultWaitDuration must be positive, got: $defaultWaitDuration"
        }
        require(screenshotQuality in 1..100) {
            "screenshotQuality must be between 1 and 100, got: $screenshotQuality"
        }
        require(logVerbosity in 0..2) {
            "logVerbosity must be 0 (errors), 1 (info), or 2 (debug), got: $logVerbosity"
        }
    }

    /**
     * Creates a copy of this config with modifications.
     */
    fun withLanguage(newLanguage: String): AgentConfig =
        copy(language = newLanguage)

    fun withInferenceMode(newMode: InferenceMode): AgentConfig =
        copy(inferenceMode = newMode)

    fun withConfirmations(enabled: Boolean): AgentConfig =
        copy(enableConfirmations = enabled)

    /**
     * Returns a user-friendly description of this configuration.
     */
    fun describe(): String = buildString {
        appendLine("Agent Configuration:")
        appendLine("  Inference Mode: $inferenceMode")
        appendLine("  Language: $language")
        appendLine("  Max Retries: $maxRetries")
        appendLine("  Action Timeout: ${actionTimeout}ms")
        appendLine("  Max Steps: $maxStepsPerTask")
        appendLine("  Confirmations: ${if (enableConfirmations) "Enabled" else "Disabled"}")
        appendLine("  Auto-complete: ${if (autoCompleteSimpleTasks) "Yes" else "No"}")
        appendLine("  Screenshot Quality: $screenshotQuality%")
        appendLine("  Log Verbosity: $logVerbosity")
    }
}
