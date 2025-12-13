package com.openautoglm.agent.core

import android.util.Log
import com.openautoglm.agent.accessibility.AutoGLMAccessibilityService
import com.openautoglm.agent.accessibility.ScreenCaptureManager
import com.openautoglm.agent.accessibility.UIElementParser
import com.openautoglm.agent.data.entities.ScreenState
import com.openautoglm.agent.data.entities.UIElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * ScreenStateManager manages screen state capture and processing for the VLM Android agent.
 *
 * This class coordinates between the ScreenCaptureManager (for screenshots) and the
 * AutoGLMAccessibilityService (for UI element parsing) to provide a unified view of
 * the current screen state.
 *
 * Usage:
 * 1. Ensure ScreenCaptureManager is initialized with screen capture permission
 * 2. Ensure AutoGLMAccessibilityService is connected
 * 3. Use captureCurrentState() to get a complete ScreenState
 * 4. Use individual methods for specific capture needs
 *
 * @property screenCaptureManager The ScreenCaptureManager instance for screenshot capture
 */
class ScreenStateManager(
    private val screenCaptureManager: ScreenCaptureManager
) {
    companion object {
        private const val TAG = "ScreenStateManager"
        private const val DEFAULT_SCREEN_CHANGE_POLL_INTERVAL_MS = 100L
        private const val UNKNOWN_APP_NAME = "Unknown"
    }

    private val uiElementParser = UIElementParser()

    /**
     * The current accessibility service instance, or null if not connected.
     */
    private val accessibilityService: AutoGLMAccessibilityService?
        get() = AutoGLMAccessibilityService.instance

    /**
     * Captures the complete current screen state including screenshot and UI elements.
     *
     * This method combines:
     * - Screenshot capture via ScreenCaptureManager (base64 encoded)
     * - UI element parsing via accessibility service
     * - Current app package information
     *
     * @param taskId The UUID of the task this capture is associated with
     * @return ScreenState containing all captured information
     */
    suspend fun captureCurrentState(taskId: UUID = UUID.randomUUID()): ScreenState = withContext(Dispatchers.IO) {
        Log.d(TAG, "Capturing current screen state for task: $taskId")

        // Capture screenshot as base64
        val screenshotBase64 = captureScreenshot()

        // Parse UI elements from accessibility tree
        val uiElements = parseUIElements()

        // Get screen dimensions
        val (width, height) = screenCaptureManager.getScreenDimensions()

        // Get current package and app name
        val currentPackage = getCurrentPackage() ?: ""
        val currentApp = extractAppName(currentPackage)

        val screenState = ScreenState(
            taskId = taskId,
            width = width,
            height = height,
            screenshotBase64 = screenshotBase64,
            currentApp = currentApp,
            currentPackage = currentPackage,
            uiElements = uiElements
        )

        Log.d(TAG, "Screen state captured: ${uiElements.size} elements, " +
                "screenshot=${screenshotBase64 != null}, package=$currentPackage")

        screenState
    }

    /**
     * Captures a screenshot of the current screen and returns it as a base64 encoded string.
     *
     * Uses the ScreenCaptureManager to capture the screen via MediaProjection API.
     * The screenshot is encoded as PNG format for optimal quality.
     *
     * @return Base64 encoded screenshot string, or null if capture failed
     */
    suspend fun captureScreenshot(): String? = withContext(Dispatchers.IO) {
        if (!screenCaptureManager.isCapturing()) {
            Log.w(TAG, "Screen capture not active, cannot capture screenshot")
            return@withContext null
        }

        try {
            val bitmap = screenCaptureManager.captureScreen()
            if (bitmap == null) {
                Log.w(TAG, "Failed to capture screen bitmap")
                return@withContext null
            }

            val base64 = screenCaptureManager.toBase64(bitmap)
            bitmap.recycle()

            Log.d(TAG, "Screenshot captured, base64 length: ${base64.length}")
            base64
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screenshot", e)
            null
        }
    }

    /**
     * Parses the current UI accessibility tree into a list of UIElement objects.
     *
     * Uses the AutoGLMAccessibilityService to get the root accessibility node
     * and UIElementParser to convert it to a flat list of UIElements.
     *
     * @return List of UIElement objects representing the current screen's UI hierarchy
     */
    fun parseUIElements(): List<UIElement> {
        val service = accessibilityService
        if (service == null) {
            Log.w(TAG, "Accessibility service not connected, cannot parse UI elements")
            return emptyList()
        }

        return try {
            val rootNode = service.getRootNode()
            if (rootNode == null) {
                Log.w(TAG, "Root accessibility node is null")
                return emptyList()
            }

            val elements = uiElementParser.parse(rootNode)
            rootNode.recycle()

            Log.d(TAG, "Parsed ${elements.size} UI elements")
            elements
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing UI elements", e)
            emptyList()
        }
    }

    /**
     * Gets the package name of the current foreground application.
     *
     * @return The package name string, or null if not available
     */
    fun getCurrentPackage(): String? {
        return accessibilityService?.getCurrentPackage()
    }

    /**
     * Gets the class name of the current foreground activity.
     *
     * @return The class name string, or null if not available
     */
    fun getCurrentClassName(): String? {
        return accessibilityService?.getCurrentClassName()
    }

    /**
     * Checks if the screen capture system is ready and available.
     *
     * Both the ScreenCaptureManager must be actively capturing and the
     * AutoGLMAccessibilityService must be connected for full functionality.
     *
     * @return true if screen capture is available, false otherwise
     */
    fun isScreenReady(): Boolean {
        val captureReady = screenCaptureManager.isCapturing()
        val accessibilityReady = AutoGLMAccessibilityService.isServiceConnected

        Log.d(TAG, "Screen ready check: capture=$captureReady, accessibility=$accessibilityReady")

        return captureReady && accessibilityReady
    }

    /**
     * Checks if only the accessibility service is available.
     * Useful for partial functionality when screenshot is not available.
     *
     * @return true if accessibility service is connected
     */
    fun isAccessibilityReady(): Boolean {
        return AutoGLMAccessibilityService.isServiceConnected
    }

    /**
     * Checks if only the screen capture is available.
     *
     * @return true if screen capture manager is actively capturing
     */
    fun isCaptureReady(): Boolean {
        return screenCaptureManager.isCapturing()
    }

    /**
     * Waits for the screen content to change within the specified timeout.
     *
     * This method captures the initial UI element state and polls for changes.
     * A screen change is detected when:
     * - The number of UI elements changes significantly
     * - The package name changes
     * - Key element properties change
     *
     * @param timeoutMs Maximum time to wait for screen change in milliseconds
     * @return true if screen changed within timeout, false if timeout expired
     */
    suspend fun waitForScreenChange(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isAccessibilityReady()) {
            Log.w(TAG, "Accessibility service not ready, cannot wait for screen change")
            return@withContext false
        }

        Log.d(TAG, "Waiting for screen change, timeout: ${timeoutMs}ms")

        // Capture initial state
        val initialPackage = getCurrentPackage()
        val initialElements = parseUIElements()
        val initialHash = computeScreenHash(initialPackage, initialElements)

        val result = withTimeoutOrNull(timeoutMs) {
            while (true) {
                delay(DEFAULT_SCREEN_CHANGE_POLL_INTERVAL_MS)

                val currentPackage = getCurrentPackage()
                val currentElements = parseUIElements()
                val currentHash = computeScreenHash(currentPackage, currentElements)

                if (currentHash != initialHash) {
                    Log.d(TAG, "Screen change detected")
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }

        val changed = result ?: false
        if (!changed) {
            Log.d(TAG, "Screen change timeout expired")
        }
        changed
    }

    /**
     * Waits for a specific package to become active.
     *
     * @param packageName The package name to wait for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if the package became active within timeout
     */
    suspend fun waitForPackage(packageName: String, timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isAccessibilityReady()) {
            Log.w(TAG, "Accessibility service not ready")
            return@withContext false
        }

        Log.d(TAG, "Waiting for package: $packageName, timeout: ${timeoutMs}ms")

        val result = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val currentPackage = getCurrentPackage()
                if (currentPackage == packageName) {
                    Log.d(TAG, "Package $packageName is now active")
                    return@withTimeoutOrNull true
                }
                delay(DEFAULT_SCREEN_CHANGE_POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }

        result ?: false
    }

    /**
     * Waits for the screen to become stable (no changes for a specified duration).
     *
     * @param stabilityDurationMs Duration with no changes to consider stable
     * @param timeoutMs Maximum time to wait for stability
     * @return true if screen became stable within timeout
     */
    suspend fun waitForStability(
        stabilityDurationMs: Long = 500L,
        timeoutMs: Long = 5000L
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isAccessibilityReady()) {
            Log.w(TAG, "Accessibility service not ready")
            return@withContext false
        }

        Log.d(TAG, "Waiting for screen stability, stabilityDuration: ${stabilityDurationMs}ms, " +
                "timeout: ${timeoutMs}ms")

        var lastHash = computeScreenHash(getCurrentPackage(), parseUIElements())
        var stableStartTime = System.currentTimeMillis()

        val result = withTimeoutOrNull(timeoutMs) {
            while (true) {
                delay(DEFAULT_SCREEN_CHANGE_POLL_INTERVAL_MS)

                val currentHash = computeScreenHash(getCurrentPackage(), parseUIElements())

                if (currentHash != lastHash) {
                    // Screen changed, reset stability timer
                    lastHash = currentHash
                    stableStartTime = System.currentTimeMillis()
                } else {
                    // Screen stable, check if stable long enough
                    val stableDuration = System.currentTimeMillis() - stableStartTime
                    if (stableDuration >= stabilityDurationMs) {
                        Log.d(TAG, "Screen is stable")
                        return@withTimeoutOrNull true
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }

        val stable = result ?: false
        if (!stable) {
            Log.d(TAG, "Screen stability timeout expired")
        }
        stable
    }

    /**
     * Computes a hash representing the current screen state.
     * Used for detecting screen changes.
     *
     * @param packageName Current package name
     * @param elements Current UI elements
     * @return Hash code representing the screen state
     */
    private fun computeScreenHash(packageName: String?, elements: List<UIElement>): Int {
        var hash = packageName?.hashCode() ?: 0
        hash = 31 * hash + elements.size

        // Include key element properties in hash for more accurate change detection
        for (element in elements.take(20)) { // Limit to first 20 elements for performance
            hash = 31 * hash + (element.text?.hashCode() ?: 0)
            hash = 31 * hash + element.bounds.hashCode()
            hash = 31 * hash + element.type.hashCode()
        }

        return hash
    }

    /**
     * Extracts a human-readable app name from a package name.
     * This is a simple extraction; for proper app names, use PackageManager.
     *
     * @param packageName The package name to extract from
     * @return A simplified app name or "Unknown" if extraction fails
     */
    private fun extractAppName(packageName: String): String {
        if (packageName.isBlank()) {
            return UNKNOWN_APP_NAME
        }

        // Extract the last segment of the package name as a fallback app name
        // e.g., "com.example.myapp" -> "myapp"
        return packageName.substringAfterLast('.').ifBlank { UNKNOWN_APP_NAME }
    }
}
