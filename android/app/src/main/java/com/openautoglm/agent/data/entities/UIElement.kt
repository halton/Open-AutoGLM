package com.openautoglm.agent.data.entities

/**
 * Represents a rectangular bounds region on the screen.
 * Used to define the position and size of UI elements.
 */
data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    /**
     * The horizontal center coordinate of the rectangle.
     */
    val centerX: Int
        get() = (left + right) / 2

    /**
     * The vertical center coordinate of the rectangle.
     */
    val centerY: Int
        get() = (top + bottom) / 2

    /**
     * The width of the rectangle.
     */
    val width: Int
        get() = right - left

    /**
     * The height of the rectangle.
     */
    val height: Int
        get() = bottom - top

    /**
     * Checks if the rectangle contains a given point.
     */
    fun contains(x: Int, y: Int): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }

    /**
     * Checks if this rectangle intersects with another rectangle.
     */
    fun intersects(other: Rect): Boolean {
        return left < other.right && right > other.left &&
                top < other.bottom && bottom > other.top
    }

    /**
     * Returns true if the rectangle has valid dimensions (non-negative width and height).
     */
    fun isValid(): Boolean {
        return width >= 0 && height >= 0
    }

    companion object {
        /**
         * Creates a Rect with all coordinates set to zero.
         */
        val EMPTY = Rect(0, 0, 0, 0)

        /**
         * Creates a Rect from Android's Rect object.
         */
        fun fromAndroidRect(rect: android.graphics.Rect): Rect {
            return Rect(rect.left, rect.top, rect.right, rect.bottom)
        }
    }
}

/**
 * Represents a UI element parsed from the accessibility tree.
 * This is a plain data class used for representing UI elements during agent processing,
 * not a Room entity.
 *
 * @property id Unique identifier for this UI element within the current screen state
 * @property type The type/category of the UI element (e.g., "button", "text", "input")
 * @property text The visible text content of the element, if any
 * @property contentDescription The accessibility content description, if any
 * @property bounds The rectangular bounds of the element on screen
 * @property clickable Whether the element can be clicked
 * @property scrollable Whether the element can be scrolled
 * @property focused Whether the element currently has focus
 * @property enabled Whether the element is enabled for interaction
 * @property className The Android class name of the view (e.g., "android.widget.Button")
 */
data class UIElement(
    val id: String,
    val type: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: Rect,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val focused: Boolean = false,
    val enabled: Boolean = true,
    val className: String? = null
) {
    /**
     * Returns the display text for this element, preferring text over contentDescription.
     */
    val displayText: String?
        get() = text ?: contentDescription

    /**
     * Returns true if this element has any text content (text or contentDescription).
     */
    val hasText: Boolean
        get() = !text.isNullOrBlank() || !contentDescription.isNullOrBlank()

    /**
     * Returns true if this element can be interacted with (clickable and enabled).
     */
    val isInteractable: Boolean
        get() = clickable && enabled

    /**
     * Returns the center point of this element's bounds.
     */
    val center: Pair<Int, Int>
        get() = Pair(bounds.centerX, bounds.centerY)
}
