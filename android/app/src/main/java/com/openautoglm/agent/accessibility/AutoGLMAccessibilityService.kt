package com.openautoglm.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * AutoGLM Accessibility Service provides UI automation capabilities for the VLM Android agent.
 *
 * This service enables:
 * - Screen state capture (UI tree and screenshots)
 * - Touch gestures (tap, swipe)
 * - Navigation actions (back, home)
 * - Text input via focused nodes
 *
 * The service maintains a singleton instance accessible via [instance] for use by other components.
 */
class AutoGLMAccessibilityService : AccessibilityService() {

    companion object {
        private val instanceRef = AtomicReference<AutoGLMAccessibilityService?>(null)

        /**
         * Returns the current service instance, or null if not connected.
         */
        val instance: AutoGLMAccessibilityService?
            get() = instanceRef.get()

        /**
         * Checks if the accessibility service is currently connected and available.
         */
        val isServiceConnected: Boolean
            get() = instanceRef.get() != null

        private const val GESTURE_TIMEOUT_MS = 5000L
        private const val DEFAULT_TAP_DURATION_MS = 100L
        private const val DEFAULT_SWIPE_DURATION_MS = 300L
    }

    private var currentPackageName: String? = null
    private var currentClassName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef.set(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            // Track current foreground app
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                it.packageName?.toString()?.let { pkg ->
                    currentPackageName = pkg
                }
                it.className?.toString()?.let { cls ->
                    currentClassName = cls
                }
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted - can be used for cleanup if needed
    }

    override fun onDestroy() {
        instanceRef.compareAndSet(this, null)
        super.onDestroy()
    }

    /**
     * Data class representing the current screen state.
     */
    data class ScreenState(
        val rootNode: AccessibilityNodeInfo?,
        val screenshot: Bitmap?,
        val packageName: String?,
        val className: String?
    )

    /**
     * Captures the current screen state including UI tree and screenshot.
     *
     * @return ScreenState containing the root accessibility node, screenshot bitmap,
     *         and current package/class names.
     */
    fun captureScreenState(): ScreenState {
        val rootNode = getRootNode()
        val screenshot = captureScreenshot()

        return ScreenState(
            rootNode = rootNode,
            screenshot = screenshot,
            packageName = currentPackageName,
            className = currentClassName
        )
    }

    /**
     * Captures a screenshot of the current screen.
     * Requires API 30+ (Android 11) for takeScreenshot API.
     *
     * @return Bitmap of the current screen, or null if capture fails or API < 30.
     */
    private fun captureScreenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        return runBlocking {
            val deferred = CompletableDeferred<Bitmap?>()

            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        screenshot.hardwareBuffer.close()
                        deferred.complete(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        deferred.complete(null)
                    }
                }
            )

            withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
                deferred.await()
            }
        }
    }

    /**
     * Performs a tap gesture at the specified coordinates.
     * Uses GestureDescription API (API 24+).
     *
     * @param x The x coordinate to tap.
     * @param y The y coordinate to tap.
     * @return true if the gesture was dispatched successfully, false otherwise.
     */
    fun performTap(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            DEFAULT_TAP_DURATION_MS
        )
        gestureBuilder.addStroke(stroke)

        return dispatchGestureAndWait(gestureBuilder.build())
    }

    /**
     * Performs a swipe gesture from start to end coordinates.
     * Uses GestureDescription API (API 24+).
     *
     * @param startX Starting x coordinate.
     * @param startY Starting y coordinate.
     * @param endX Ending x coordinate.
     * @param endY Ending y coordinate.
     * @param duration Duration of the swipe in milliseconds.
     * @return true if the gesture was dispatched successfully, false otherwise.
     */
    fun performSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = DEFAULT_SWIPE_DURATION_MS
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            duration.coerceAtLeast(1)
        )
        gestureBuilder.addStroke(stroke)

        return dispatchGestureAndWait(gestureBuilder.build())
    }

    /**
     * Dispatches a gesture and waits for completion.
     *
     * @param gesture The GestureDescription to dispatch.
     * @return true if completed successfully, false otherwise.
     */
    private fun dispatchGestureAndWait(gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        return runBlocking {
            val deferred = CompletableDeferred<Boolean>()

            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    deferred.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    deferred.complete(false)
                }
            }

            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                return@runBlocking false
            }

            withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
                deferred.await()
            } ?: false
        }
    }

    /**
     * Performs the back navigation action.
     *
     * @return true if the action was performed successfully.
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Performs the home navigation action.
     *
     * @return true if the action was performed successfully.
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Performs the recent apps action.
     *
     * @return true if the action was performed successfully.
     */
    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Performs the notifications panel action.
     *
     * @return true if the action was performed successfully.
     */
    fun performNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * Types text into the currently focused editable field.
     *
     * This method finds the currently focused node and sets its text content.
     * The node must be editable and focused for this to work.
     *
     * @param text The text to type.
     * @return true if text was successfully set, false otherwise.
     */
    fun performType(text: String): Boolean {
        val rootNode = getRootNode() ?: return false
        val focusedNode = findFocusedEditableNode(rootNode)

        return if (focusedNode != null) {
            val result = setNodeText(focusedNode, text)
            focusedNode.recycle()
            rootNode.recycle()
            result
        } else {
            rootNode.recycle()
            false
        }
    }

    /**
     * Types text into a specific node.
     *
     * @param node The node to type into.
     * @param text The text to type.
     * @return true if text was successfully set.
     */
    fun performTypeOnNode(node: AccessibilityNodeInfo, text: String): Boolean {
        // First focus the node if not already focused
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        return setNodeText(node, text)
    }

    /**
     * Sets text on an accessibility node.
     *
     * @param node The node to set text on.
     * @param text The text to set.
     * @return true if successful.
     */
    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable) {
            return false
        }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }

        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Finds the currently focused editable node in the UI tree.
     *
     * @param root The root node to search from.
     * @return The focused editable node, or null if not found.
     */
    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Try to find input-focused node first
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            return focusedNode
        }
        focusedNode?.recycle()

        // Fall back to searching for any focused editable node
        return findFocusedEditableNodeRecursive(root)
    }

    /**
     * Recursively searches for a focused editable node.
     */
    private fun findFocusedEditableNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNodeRecursive(child)
            child.recycle()
            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * Gets the root accessibility node of the current window.
     *
     * @return The root AccessibilityNodeInfo, or null if not available.
     *         Caller is responsible for recycling the returned node.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    /**
     * Gets the package name of the current foreground application.
     *
     * @return The package name string, or null if not available.
     */
    fun getCurrentPackage(): String? {
        return currentPackageName ?: rootInActiveWindow?.packageName?.toString()
    }

    /**
     * Gets the class name of the current foreground activity.
     *
     * @return The class name string, or null if not available.
     */
    fun getCurrentClassName(): String? {
        return currentClassName
    }

    /**
     * Finds all nodes matching the given text.
     *
     * @param text The text to search for.
     * @return List of matching nodes. Caller is responsible for recycling.
     */
    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val rootNode = getRootNode() ?: return emptyList()
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        rootNode.recycle()
        return nodes ?: emptyList()
    }

    /**
     * Finds all nodes matching the given view ID.
     *
     * @param viewId The view ID to search for (e.g., "com.example:id/button").
     * @return List of matching nodes. Caller is responsible for recycling.
     */
    fun findNodesById(viewId: String): List<AccessibilityNodeInfo> {
        val rootNode = getRootNode() ?: return emptyList()
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        rootNode.recycle()
        return nodes ?: emptyList()
    }

    /**
     * Clicks on a node.
     *
     * @param node The node to click.
     * @return true if the click action was performed.
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Long clicks on a node.
     *
     * @param node The node to long click.
     * @return true if the long click action was performed.
     */
    fun longClickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    /**
     * Scrolls a node forward (down or right).
     *
     * @param node The scrollable node.
     * @return true if the scroll action was performed.
     */
    fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /**
     * Scrolls a node backward (up or left).
     *
     * @param node The scrollable node.
     * @return true if the scroll action was performed.
     */
    fun scrollBackward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /**
     * Performs a long press at the specified coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param duration Duration of the press in milliseconds.
     * @return true if the gesture was performed successfully.
     */
    fun performLongPress(x: Int, y: Int, duration: Long = 1000L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            duration.coerceAtLeast(500)
        )
        gestureBuilder.addStroke(stroke)

        return dispatchGestureAndWait(gestureBuilder.build())
    }

    /**
     * Performs a pinch gesture (zoom in/out).
     *
     * @param centerX Center x coordinate of the pinch.
     * @param centerY Center y coordinate of the pinch.
     * @param startDistance Initial distance between fingers.
     * @param endDistance Final distance between fingers.
     * @param duration Duration of the gesture.
     * @return true if the gesture was performed successfully.
     */
    fun performPinch(
        centerX: Int,
        centerY: Int,
        startDistance: Int,
        endDistance: Int,
        duration: Long = 500L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val halfStartDist = startDistance / 2
        val halfEndDist = endDistance / 2

        // First finger path
        val path1 = Path().apply {
            moveTo((centerX - halfStartDist).toFloat(), centerY.toFloat())
            lineTo((centerX - halfEndDist).toFloat(), centerY.toFloat())
        }

        // Second finger path
        val path2 = Path().apply {
            moveTo((centerX + halfStartDist).toFloat(), centerY.toFloat())
            lineTo((centerX + halfEndDist).toFloat(), centerY.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path2, 0, duration))

        return dispatchGestureAndWait(gestureBuilder.build())
    }
}
