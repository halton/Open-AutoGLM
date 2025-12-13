package com.openautoglm.agent.data.entities

import java.util.UUID

/**
 * Represents the captured state of the device screen at a specific moment.
 * Used for VLM analysis and agent decision-making.
 *
 * This is a plain data class (not a Room entity) for runtime screen state capture.
 *
 * @property id Unique identifier for this screen state
 * @property taskId The task this screen state is associated with
 * @property capturedAt Timestamp when the screen was captured (epoch milliseconds)
 * @property width Screen width in pixels
 * @property height Screen height in pixels
 * @property screenshotPath File path to the saved screenshot (nullable if not persisted)
 * @property screenshotBase64 Base64-encoded screenshot data (transient, not persisted)
 * @property currentApp Display name of the currently active app
 * @property currentPackage Package name of the currently active app
 * @property uiElements List of UI elements detected on screen
 */
data class ScreenState(
    val id: UUID = UUID.randomUUID(),
    val taskId: UUID,
    val capturedAt: Long = System.currentTimeMillis(),
    val width: Int,
    val height: Int,
    val screenshotPath: String? = null,
    @Transient
    val screenshotBase64: String? = null,
    val currentApp: String,
    val currentPackage: String,
    val uiElements: List<UIElement> = emptyList()
) {
    /**
     * Returns whether a screenshot is available (either as file or base64).
     */
    val hasScreenshot: Boolean
        get() = screenshotPath != null || screenshotBase64 != null

    /**
     * Returns the screen aspect ratio.
     */
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 0f

    /**
     * Returns the total number of UI elements on screen.
     */
    val elementCount: Int
        get() = uiElements.size

    /**
     * Finds UI elements by their type.
     */
    fun findElementsByType(type: String): List<UIElement> =
        uiElements.filter { it.type.equals(type, ignoreCase = true) }

    /**
     * Finds clickable UI elements.
     */
    fun findClickableElements(): List<UIElement> =
        uiElements.filter { it.isClickable }

    /**
     * Finds a UI element by its ID.
     */
    fun findElementById(elementId: UUID): UIElement? =
        uiElements.find { it.id == elementId }

    /**
     * Finds UI elements containing the specified text.
     */
    fun findElementsByText(text: String, ignoreCase: Boolean = true): List<UIElement> =
        uiElements.filter { element ->
            element.text?.contains(text, ignoreCase = ignoreCase) == true ||
                element.contentDescription?.contains(text, ignoreCase = ignoreCase) == true
        }

    /**
     * Finds UI elements at the specified screen coordinates.
     */
    fun findElementsAtPoint(x: Int, y: Int): List<UIElement> =
        uiElements.filter { element ->
            x >= element.bounds.left &&
                x <= element.bounds.right &&
                y >= element.bounds.top &&
                y <= element.bounds.bottom
        }
}
