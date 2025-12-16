package com.openautoglm.agent.core

import android.content.Context
import android.content.Intent
import android.util.DisplayMetrics
import android.view.WindowManager
import com.openautoglm.agent.accessibility.AutoGLMAccessibilityService
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionResult
import com.openautoglm.agent.data.entities.ActionType
import kotlinx.coroutines.delay

/**
 * Executes UI actions through the AccessibilityService.
 *
 * This class serves as the bridge between the agent's action decisions and the
 * actual UI automation performed by the AccessibilityService. It handles:
 * - Parameter parsing from Action entities
 * - Execution of various action types (tap, swipe, type, navigation, etc.)
 * - Error handling when AccessibilityService is unavailable
 * - Timing information for action execution
 *
 * @param context Android context for launching apps and accessing system services
 */
class ActionExecutor(private val context: Context) {

    companion object {
        private const val DEFAULT_WAIT_AFTER_ACTION_MS = 300L
        private const val DEFAULT_SWIPE_DURATION_MS = 300L
        private const val LONG_PRESS_DURATION_MS = 1000L
        private const val DOUBLE_TAP_DELAY_MS = 100L

        // VLM model outputs coordinates in normalized range 0-999
        private const val NORMALIZED_COORDINATE_MAX = 999
    }

    // Cached screen dimensions
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    init {
        // Get screen dimensions - MUST use getRealMetrics to match ScreenCaptureManager
        // getRealMetrics returns full physical screen including status bar and navigation bar
        // This ensures coordinate conversion matches what the VLM sees in screenshots
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        android.util.Log.i("ActionExecutor", "Screen dimensions (real): ${screenWidth}x${screenHeight}")
    }

    /**
     * Converts normalized coordinates (0-999) to actual screen pixels.
     *
     * @param normalizedX Normalized X coordinate (0-999)
     * @param normalizedY Normalized Y coordinate (0-999)
     * @return Pair of actual (x, y) screen coordinates in pixels
     */
    private fun normalizedToScreenCoordinates(normalizedX: Int, normalizedY: Int): Pair<Int, Int> {
        val actualX = (normalizedX * screenWidth) / NORMALIZED_COORDINATE_MAX
        val actualY = (normalizedY * screenHeight) / NORMALIZED_COORDINATE_MAX
        return Pair(actualX, actualY)
    }

    /**
     * Main execution method that routes to specific action handlers based on action type.
     *
     * @param action The Action entity containing type and parameters
     * @return ActionResult with success/failure status, message, and timing info
     */
    suspend fun execute(action: Action): ActionResult {
        val startTime = System.currentTimeMillis()

        val result = when (action.type) {
            ActionType.TAP -> executeTapFromAction(action)
            ActionType.DOUBLE_TAP -> executeDoubleTapFromAction(action)
            ActionType.LONG_PRESS -> executeLongPressFromAction(action)
            ActionType.SWIPE -> executeSwipeFromAction(action)
            ActionType.TYPE -> executeTypeFromAction(action)
            ActionType.BACK -> executeBack()
            ActionType.HOME -> executeHome()
            ActionType.LAUNCH -> executeLaunchAppFromAction(action)
            ActionType.WAIT -> executeWaitFromAction(action)
            ActionType.TAKE_OVER -> executeTakeOver(action)
            ActionType.ENTER -> executeEnter()
            ActionType.FINISH -> executeFinish(action)
        }

        val executionTime = System.currentTimeMillis() - startTime

        return result.copy(
            actionId = action.id,
            message = result.message?.let { "$it (executed in ${executionTime}ms)" }
                ?: "Action completed in ${executionTime}ms"
        )
    }

    /**
     * Performs a tap gesture at the specified coordinates.
     *
     * @param x The x coordinate to tap
     * @param y The y coordinate to tap
     * @return ActionResult indicating success or failure
     */
    suspend fun executeTap(x: Int, y: Int): ActionResult {
        android.util.Log.i("ActionExecutor", "executeTap called: ($x, $y)")

        val service = getAccessibilityService()
        if (service == null) {
            android.util.Log.e("ActionExecutor", "AccessibilityService not available!")
            return ActionResult.failure(message = "AccessibilityService not available")
        }

        android.util.Log.i("ActionExecutor", "Calling performTap...")
        val success = service.performTap(x, y)
        android.util.Log.i("ActionExecutor", "performTap returned: $success")

        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (success) {
            ActionResult.success(message = "Tapped at ($x, $y)")
        } else {
            ActionResult.failure(message = "Failed to tap at ($x, $y)")
        }
    }

    /**
     * Performs a swipe gesture from start to end coordinates.
     *
     * @param startX Starting x coordinate
     * @param startY Starting y coordinate
     * @param endX Ending x coordinate
     * @param endY Ending y coordinate
     * @return ActionResult indicating success or failure
     */
    suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int): ActionResult {
        return executeSwipe(startX, startY, endX, endY, DEFAULT_SWIPE_DURATION_MS)
    }

    /**
     * Performs a swipe gesture with custom duration.
     *
     * @param startX Starting x coordinate
     * @param startY Starting y coordinate
     * @param endX Ending x coordinate
     * @param endY Ending y coordinate
     * @param duration Duration of the swipe in milliseconds
     * @return ActionResult indicating success or failure
     */
    suspend fun executeSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): ActionResult {
        val service = getAccessibilityService()
            ?: return ActionResult.failure(message = "AccessibilityService not available")

        val success = service.performSwipe(startX, startY, endX, endY, duration)
        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (success) {
            ActionResult.success(message = "Swiped from ($startX, $startY) to ($endX, $endY)")
        } else {
            ActionResult.failure(message = "Failed to swipe from ($startX, $startY) to ($endX, $endY)")
        }
    }

    /**
     * Types text into the currently focused editable field.
     *
     * @param text The text to type
     * @return ActionResult indicating success or failure
     */
    suspend fun executeType(text: String): ActionResult {
        val service = getAccessibilityService()
            ?: return ActionResult.failure(message = "AccessibilityService not available")

        val success = service.performType(text)
        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (success) {
            ActionResult.success(message = "Typed text: \"$text\"")
        } else {
            ActionResult.failure(message = "Failed to type text. No focused editable field found.")
        }
    }

    /**
     * Performs the system back navigation action.
     *
     * @return ActionResult indicating success or failure
     */
    suspend fun executeBack(): ActionResult {
        val service = getAccessibilityService()
            ?: return ActionResult.failure(message = "AccessibilityService not available")

        val success = service.performBack()
        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (success) {
            ActionResult.success(message = "Performed back navigation")
        } else {
            ActionResult.failure(message = "Failed to perform back navigation")
        }
    }

    /**
     * Performs the system home navigation action.
     *
     * @return ActionResult indicating success or failure
     */
    suspend fun executeHome(): ActionResult {
        android.util.Log.i("ActionExecutor", "executeHome called")
        val service = getAccessibilityService()
        if (service == null) {
            android.util.Log.e("ActionExecutor", "AccessibilityService not available for HOME action")
            return ActionResult.failure(message = "AccessibilityService not available")
        }

        android.util.Log.i("ActionExecutor", "Calling performHome on AccessibilityService")
        val success = service.performHome()
        android.util.Log.i("ActionExecutor", "performHome returned: $success")
        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (success) {
            ActionResult.success(message = "Performed home navigation")
        } else {
            ActionResult.failure(message = "Failed to perform home navigation")
        }
    }

    /**
     * Performs an Enter/IME action (submit search, send message, etc.).
     *
     * @return ActionResult indicating success or failure
     */
    suspend fun executeEnter(): ActionResult {
        val service = getAccessibilityService()
            ?: return ActionResult.failure(message = "AccessibilityService not available")

        val success = service.performEnter()
        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (success) {
            ActionResult.success(message = "Performed Enter/Submit action")
        } else {
            ActionResult.failure(message = "Failed to perform Enter action")
        }
    }

    /**
     * Launches an application by its package name.
     *
     * @param packageName The package name of the app to launch
     * @return ActionResult indicating success or failure
     */
    suspend fun executeLaunchApp(packageName: String): ActionResult {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent == null) {
                return ActionResult.failure(message = "App not found: $packageName")
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)

            // Wait for app to launch
            delay(1000L)

            ActionResult.success(message = "Launched app: $packageName")
        } catch (e: Exception) {
            ActionResult.failure(message = "Failed to launch app: ${e.message}")
        }
    }

    /**
     * Waits for the specified duration.
     *
     * @param durationMs Duration to wait in milliseconds
     * @return ActionResult indicating success
     */
    suspend fun executeWait(durationMs: Long): ActionResult {
        delay(durationMs.coerceAtLeast(0))
        return ActionResult.success(message = "Waited for ${durationMs}ms")
    }

    /**
     * Performs a double tap at the specified coordinates.
     *
     * @param x The x coordinate to double tap
     * @param y The y coordinate to double tap
     * @return ActionResult indicating success or failure
     */
    suspend fun executeDoubleTap(x: Int, y: Int): ActionResult {
        val service = getAccessibilityService()
            ?: return ActionResult.failure(message = "AccessibilityService not available")

        val firstTap = service.performTap(x, y)
        if (!firstTap) {
            return ActionResult.failure(message = "Failed to perform first tap at ($x, $y)")
        }

        delay(DOUBLE_TAP_DELAY_MS)

        val secondTap = service.performTap(x, y)
        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (secondTap) {
            ActionResult.success(message = "Double-tapped at ($x, $y)")
        } else {
            ActionResult.failure(message = "Failed to perform second tap at ($x, $y)")
        }
    }

    /**
     * Performs a long press at the specified coordinates.
     *
     * @param x The x coordinate to long press
     * @param y The y coordinate to long press
     * @param duration Duration of the press in milliseconds (default: 1000ms)
     * @return ActionResult indicating success or failure
     */
    suspend fun executeLongPress(x: Int, y: Int, duration: Long = LONG_PRESS_DURATION_MS): ActionResult {
        val service = getAccessibilityService()
            ?: return ActionResult.failure(message = "AccessibilityService not available")

        val success = service.performLongPress(x, y, duration)
        delay(DEFAULT_WAIT_AFTER_ACTION_MS)

        return if (success) {
            ActionResult.success(message = "Long-pressed at ($x, $y) for ${duration}ms")
        } else {
            ActionResult.failure(message = "Failed to long-press at ($x, $y)")
        }
    }

    // Private helper methods for parsing action parameters

    private suspend fun executeTapFromAction(action: Action): ActionResult {
        val normalizedX = action.parameters.getIntParam("x")
            ?: return ActionResult.failure(message = "Missing 'x' parameter for TAP action")
        val normalizedY = action.parameters.getIntParam("y")
            ?: return ActionResult.failure(message = "Missing 'y' parameter for TAP action")

        // Convert normalized coordinates (0-999) to actual screen pixels
        val (x, y) = normalizedToScreenCoordinates(normalizedX, normalizedY)
        android.util.Log.i("ActionExecutor", "TAP: normalized ($normalizedX, $normalizedY) -> screen ($x, $y)")

        return executeTap(x, y)
    }

    private suspend fun executeDoubleTapFromAction(action: Action): ActionResult {
        val normalizedX = action.parameters.getIntParam("x")
            ?: return ActionResult.failure(message = "Missing 'x' parameter for DOUBLE_TAP action")
        val normalizedY = action.parameters.getIntParam("y")
            ?: return ActionResult.failure(message = "Missing 'y' parameter for DOUBLE_TAP action")

        // Convert normalized coordinates (0-999) to actual screen pixels
        val (x, y) = normalizedToScreenCoordinates(normalizedX, normalizedY)
        android.util.Log.i("ActionExecutor", "DOUBLE_TAP: normalized ($normalizedX, $normalizedY) -> screen ($x, $y)")

        return executeDoubleTap(x, y)
    }

    private suspend fun executeLongPressFromAction(action: Action): ActionResult {
        val normalizedX = action.parameters.getIntParam("x")
            ?: return ActionResult.failure(message = "Missing 'x' parameter for LONG_PRESS action")
        val normalizedY = action.parameters.getIntParam("y")
            ?: return ActionResult.failure(message = "Missing 'y' parameter for LONG_PRESS action")
        val duration = action.parameters.getLongParam("duration") ?: LONG_PRESS_DURATION_MS

        // Convert normalized coordinates (0-999) to actual screen pixels
        val (x, y) = normalizedToScreenCoordinates(normalizedX, normalizedY)
        android.util.Log.i("ActionExecutor", "LONG_PRESS: normalized ($normalizedX, $normalizedY) -> screen ($x, $y)")

        return executeLongPress(x, y, duration)
    }

    private suspend fun executeSwipeFromAction(action: Action): ActionResult {
        val normalizedStartX = action.parameters.getIntParam("startX")
            ?: action.parameters.getIntParam("start_x")
            ?: return ActionResult.failure(message = "Missing 'startX' parameter for SWIPE action")
        val normalizedStartY = action.parameters.getIntParam("startY")
            ?: action.parameters.getIntParam("start_y")
            ?: return ActionResult.failure(message = "Missing 'startY' parameter for SWIPE action")
        val normalizedEndX = action.parameters.getIntParam("endX")
            ?: action.parameters.getIntParam("end_x")
            ?: return ActionResult.failure(message = "Missing 'endX' parameter for SWIPE action")
        val normalizedEndY = action.parameters.getIntParam("endY")
            ?: action.parameters.getIntParam("end_y")
            ?: return ActionResult.failure(message = "Missing 'endY' parameter for SWIPE action")
        val duration = action.parameters.getLongParam("duration") ?: DEFAULT_SWIPE_DURATION_MS

        // Convert normalized coordinates (0-999) to actual screen pixels
        val (startX, startY) = normalizedToScreenCoordinates(normalizedStartX, normalizedStartY)
        val (endX, endY) = normalizedToScreenCoordinates(normalizedEndX, normalizedEndY)
        android.util.Log.i("ActionExecutor", "SWIPE: normalized ($normalizedStartX, $normalizedStartY) -> ($normalizedEndX, $normalizedEndY) => screen ($startX, $startY) -> ($endX, $endY)")

        return executeSwipe(startX, startY, endX, endY, duration)
    }

    private suspend fun executeTypeFromAction(action: Action): ActionResult {
        val text = action.parameters.getStringParam("text")
            ?: return ActionResult.failure(message = "Missing 'text' parameter for TYPE action")

        return executeType(text)
    }

    private suspend fun executeLaunchAppFromAction(action: Action): ActionResult {
        val appName = action.parameters.getStringParam("app")
            ?: action.parameters.getStringParam("packageName")
            ?: action.parameters.getStringParam("package_name")
            ?: action.parameters.getStringParam("package")
            ?: return ActionResult.failure(message = "Missing 'app' parameter for LAUNCH action")

        // Try to resolve app name to package name
        val packageName = resolveAppNameToPackage(appName)
        return executeLaunchApp(packageName)
    }

    /**
     * Resolves an app name to its package name.
     * Supports common app names and their package names.
     */
    private fun resolveAppNameToPackage(appName: String): String {
        // If it looks like a package name (contains dots), use it directly
        if (appName.contains('.')) {
            return appName
        }

        // Map common app names to package names
        val appNameLower = appName.lowercase()
        return when {
            // Travel & Train booking
            appNameLower.contains("12306") || appNameLower.contains("铁路") -> "com.MobileTicket"
            appNameLower.contains("ctrip") || appNameLower.contains("携程") -> "ctrip.android.view"
            appNameLower.contains("trip.com") -> "com.ctrip.ibu.trip"
            appNameLower.contains("qunar") || appNameLower.contains("去哪儿") -> "com.Qunar"

            // Food & Local services
            appNameLower.contains("dianping") || appNameLower.contains("点评") || appNameLower.contains("大众点评") -> "com.dianping.v1"
            appNameLower.contains("meituan") || appNameLower.contains("美团") -> "com.sankuai.meituan"
            appNameLower.contains("eleme") || appNameLower.contains("饿了么") -> "me.ele"

            // E-commerce
            appNameLower.contains("taobao") || appNameLower.contains("淘宝") -> "com.taobao.taobao"
            appNameLower.contains("jd") || appNameLower.contains("京东") -> "com.jingdong.app.mall"
            appNameLower.contains("pinduoduo") || appNameLower.contains("拼多多") -> "com.xunmeng.pinduoduo"

            // Social & Messaging
            appNameLower.contains("wechat") || appNameLower.contains("微信") -> "com.tencent.mm"
            appNameLower.contains("alipay") || appNameLower.contains("支付宝") -> "com.eg.android.AlipayGphone"
            appNameLower.contains("douyin") || appNameLower.contains("抖音") -> "com.ss.android.ugc.aweme"
            appNameLower.contains("weibo") || appNameLower.contains("微博") -> "com.sina.weibo"
            appNameLower.contains("qq") && !appNameLower.contains("mail") -> "com.tencent.mobileqq"

            // System apps
            appNameLower.contains("chrome") -> "com.android.chrome"
            appNameLower.contains("settings") || appNameLower.contains("设置") -> "com.android.settings"
            appNameLower.contains("camera") || appNameLower.contains("相机") -> "com.android.camera"
            appNameLower.contains("gallery") || appNameLower.contains("相册") -> "com.android.gallery3d"
            appNameLower.contains("phone") || appNameLower.contains("电话") -> "com.android.dialer"
            appNameLower.contains("contacts") || appNameLower.contains("联系人") -> "com.android.contacts"
            appNameLower.contains("message") || appNameLower.contains("短信") -> "com.android.mms"
            appNameLower.contains("calendar") || appNameLower.contains("日历") -> "com.android.calendar"
            appNameLower.contains("clock") || appNameLower.contains("时钟") -> "com.android.deskclock"
            appNameLower.contains("calculator") || appNameLower.contains("计算器") -> "com.android.calculator2"
            appNameLower.contains("browser") || appNameLower.contains("浏览器") -> "com.android.browser"
            appNameLower.contains("maps") || appNameLower.contains("地图") -> "com.google.android.apps.maps"
            appNameLower.contains("youtube") -> "com.google.android.youtube"
            appNameLower.contains("gmail") -> "com.google.android.gm"
            appNameLower.contains("play store") -> "com.android.vending"
            appNameLower.contains("files") || appNameLower.contains("文件") -> "com.android.documentsui"
            else -> appName // Return original if no match, let the system try to find it
        }
    }

    private suspend fun executeWaitFromAction(action: Action): ActionResult {
        val duration = action.parameters.getLongParam("duration")
            ?: action.parameters.getLongParam("durationMs")
            ?: action.parameters.getLongParam("duration_ms")
            ?: 1000L

        return executeWait(duration)
    }

    private fun executeTakeOver(action: Action): ActionResult {
        val message = action.parameters.getStringParam("message")
            ?: "User intervention required"

        return ActionResult.requiresConfirmation(
            actionId = action.id,
            message = message
        )
    }

    private fun executeFinish(action: Action): ActionResult {
        val message = action.parameters.getStringParam("message")
            ?: "Task completed"
        val success = action.parameters.getBooleanParam("success") ?: true

        return ActionResult.finish(
            actionId = action.id,
            message = message,
            success = success
        )
    }

    /**
     * Gets the current AccessibilityService instance.
     *
     * @return The AutoGLMAccessibilityService instance, or null if not available
     */
    private fun getAccessibilityService(): AutoGLMAccessibilityService? {
        return AutoGLMAccessibilityService.instance
    }

    /**
     * Checks if the AccessibilityService is currently connected.
     *
     * @return true if the service is available, false otherwise
     */
    fun isServiceAvailable(): Boolean {
        return AutoGLMAccessibilityService.isServiceConnected
    }
}

// Extension functions for parameter parsing

/**
 * Gets an integer parameter from the map, handling various numeric types.
 */
private fun Map<String, Any>.getIntParam(key: String): Int? {
    return when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is Float -> value.toInt()
        is String -> value.toIntOrNull()
        is Number -> value.toInt()
        else -> null
    }
}

/**
 * Gets a long parameter from the map, handling various numeric types.
 */
private fun Map<String, Any>.getLongParam(key: String): Long? {
    return when (val value = this[key]) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Float -> value.toLong()
        is String -> value.toLongOrNull()
        is Number -> value.toLong()
        else -> null
    }
}

/**
 * Gets a string parameter from the map.
 */
private fun Map<String, Any>.getStringParam(key: String): String? {
    return this[key]?.toString()
}

/**
 * Gets a boolean parameter from the map, handling various types.
 */
private fun Map<String, Any>.getBooleanParam(key: String): Boolean? {
    return when (val value = this[key]) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        else -> null
    }
}
