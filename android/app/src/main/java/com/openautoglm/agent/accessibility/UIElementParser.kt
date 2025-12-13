package com.openautoglm.agent.accessibility

import android.graphics.Rect as AndroidRect
import android.view.accessibility.AccessibilityNodeInfo
import com.openautoglm.agent.data.entities.Rect
import com.openautoglm.agent.data.entities.UIElement

/**
 * Parser for converting AccessibilityNodeInfo tree into a flat list of UIElement objects.
 * Handles recursive traversal, null safety, and filtering of non-visible/non-important nodes.
 */
class UIElementParser {

    private var elementIdCounter = 0

    /**
     * Parses the accessibility node tree starting from the root node.
     *
     * @param rootNode The root AccessibilityNodeInfo to parse
     * @return List of UIElement objects representing the parsed UI hierarchy
     */
    fun parse(rootNode: AccessibilityNodeInfo?): List<UIElement> {
        elementIdCounter = 0
        val elements = mutableListOf<UIElement>()

        if (rootNode == null) {
            return elements
        }

        parseNode(rootNode, elements)
        return elements
    }

    /**
     * Recursively parses a node and its children.
     *
     * @param node The current node to parse
     * @param elements The list to accumulate parsed elements
     */
    private fun parseNode(node: AccessibilityNodeInfo, elements: MutableList<UIElement>) {
        // Filter out non-visible and non-important nodes
        if (!isNodeVisible(node) || !isNodeImportant(node)) {
            // Still traverse children even if this node is filtered
            traverseChildren(node, elements)
            return
        }

        // Extract element properties with null safety
        val element = extractUIElement(node)
        elements.add(element)

        // Recursively process children
        traverseChildren(node, elements)
    }

    /**
     * Traverses all child nodes of the given node.
     *
     * @param node The parent node
     * @param elements The list to accumulate parsed elements
     */
    private fun traverseChildren(node: AccessibilityNodeInfo, elements: MutableList<UIElement>) {
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                try {
                    parseNode(childNode, elements)
                } finally {
                    childNode.recycle()
                }
            }
        }
    }

    /**
     * Extracts a UIElement from an AccessibilityNodeInfo with null safety.
     *
     * @param node The node to extract properties from
     * @return A UIElement representing the node
     */
    private fun extractUIElement(node: AccessibilityNodeInfo): UIElement {
        val id = generateElementId()
        val type = extractType(node)
        val text = extractText(node)
        val contentDescription = node.contentDescription?.toString()
        val bounds = extractBounds(node)
        val clickable = node.isClickable
        val scrollable = node.isScrollable
        val focused = node.isFocused
        val enabled = node.isEnabled

        return UIElement(
            id = id,
            type = type,
            text = text,
            contentDescription = contentDescription,
            bounds = bounds,
            clickable = clickable,
            scrollable = scrollable,
            focused = focused,
            enabled = enabled
        )
    }

    /**
     * Generates a unique element ID.
     *
     * @return A unique string ID for the element
     */
    private fun generateElementId(): String {
        return "element_${elementIdCounter++}"
    }

    /**
     * Extracts the element type from the node's class name.
     *
     * @param node The node to extract type from
     * @return The simplified type name or "Unknown" if not available
     */
    private fun extractType(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString() ?: return "Unknown"

        // Extract simple class name from fully qualified name
        return className.substringAfterLast('.')
    }

    /**
     * Extracts text content from the node.
     *
     * @param node The node to extract text from
     * @return The text content or null if not available
     */
    private fun extractText(node: AccessibilityNodeInfo): String? {
        return node.text?.toString()
    }

    /**
     * Extracts the bounds rectangle from the node.
     *
     * @param node The node to extract bounds from
     * @return A Rect representing the node's screen bounds
     */
    private fun extractBounds(node: AccessibilityNodeInfo): Rect {
        val androidRect = AndroidRect()
        node.getBoundsInScreen(androidRect)

        return Rect(
            left = androidRect.left,
            top = androidRect.top,
            right = androidRect.right,
            bottom = androidRect.bottom
        )
    }

    /**
     * Checks if a node is visible on screen.
     *
     * @param node The node to check
     * @return true if the node is visible, false otherwise
     */
    private fun isNodeVisible(node: AccessibilityNodeInfo): Boolean {
        // Check if node is visible to user
        if (!node.isVisibleToUser) {
            return false
        }

        // Check if bounds are valid (non-zero dimensions)
        val bounds = AndroidRect()
        node.getBoundsInScreen(bounds)

        return bounds.width() > 0 && bounds.height() > 0
    }

    /**
     * Checks if a node is important for accessibility purposes.
     *
     * @param node The node to check
     * @return true if the node is important, false otherwise
     */
    private fun isNodeImportant(node: AccessibilityNodeInfo): Boolean {
        // A node is considered important if it:
        // 1. Has text or content description
        // 2. Is clickable, scrollable, or focusable
        // 3. Is enabled and has some interaction capability

        val hasText = !node.text.isNullOrEmpty()
        val hasContentDescription = !node.contentDescription.isNullOrEmpty()
        val isInteractive = node.isClickable || node.isScrollable || node.isFocusable || node.isLongClickable
        val isCheckable = node.isCheckable
        val isEditable = node.isEditable

        // Consider important if it has content or is interactive
        return hasText || hasContentDescription || isInteractive || isCheckable || isEditable
    }

    companion object {
        private const val TAG = "UIElementParser"
    }
}
