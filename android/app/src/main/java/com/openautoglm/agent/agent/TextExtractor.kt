package com.openautoglm.agent.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.inference.InferenceRouterImpl
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ContentPart
import java.io.ByteArrayOutputStream

/**
 * Handles text extraction from images and screenshots.
 *
 * Features:
 * - OCR using on-device VLM
 * - Handwriting recognition
 * - Multi-language support
 * - Confidence scoring
 * - Result formatting (copyable)
 *
 * @param context Application context
 * @param inferenceRouter Inference router for VLM calls
 */
class TextExtractor(
    private val context: Context,
    private val inferenceRouter: InferenceRouterImpl
) {
    companion object {
        private const val TAG = "TextExtractor"

        // Prompts for text extraction
        private val TEXT_EXTRACTION_PROMPT_ZH = """
请仔细查看这张图片，提取其中所有可见的文字内容。

要求：
1. 按照原始布局排列文字（从上到下，从左到右）
2. 保留段落结构
3. 如果有表格，用制表符或竖线分隔列
4. 如果有手写文字，尽量识别但标注【手写】
5. 对于模糊或不确定的文字，用[?]标注

请直接输出提取的文字内容，不需要额外说明。
        """.trimIndent()

        private val TEXT_EXTRACTION_PROMPT_EN = """
Please carefully examine this image and extract all visible text content.

Requirements:
1. Arrange text following the original layout (top to bottom, left to right)
2. Preserve paragraph structure
3. If there are tables, separate columns with tabs or pipes
4. If there is handwritten text, try to recognize it and mark as [handwritten]
5. For unclear or uncertain text, mark with [?]

Please output the extracted text directly without additional explanation.
        """.trimIndent()

        private val HANDWRITING_PROMPT_ZH = """
请识别这张图片中的手写文字内容。

要求：
1. 尽可能准确地识别手写内容
2. 保持原始布局
3. 对于难以辨认的字，用[?]标注
4. 如果能识别但不确定，标注可能的替代字

请直接输出识别的文字内容。
        """.trimIndent()

        private val HANDWRITING_PROMPT_EN = """
Please recognize the handwritten text in this image.

Requirements:
1. Recognize handwritten content as accurately as possible
2. Maintain original layout
3. For illegible characters, mark with [?]
4. If recognizable but uncertain, note possible alternatives

Please output the recognized text directly.
        """.trimIndent()
    }

    /**
     * Result of text extraction.
     */
    data class ExtractionResult(
        /** Extracted text content */
        val text: String,
        /** Confidence score (0.0 - 1.0) */
        val confidence: Float,
        /** Whether handwriting was detected */
        val hasHandwriting: Boolean,
        /** Detected languages */
        val detectedLanguages: List<String>,
        /** Any warnings or notes */
        val notes: List<String>,
        /** Inference time in milliseconds */
        val inferenceTimeMs: Long
    )

    /**
     * Extracts text from a bitmap image.
     *
     * @param bitmap The image to extract text from
     * @param language Preferred language for prompts (zh/en)
     * @param isHandwriting If true, uses handwriting-specific prompt
     * @return Extraction result
     */
    suspend fun extractText(
        bitmap: Bitmap,
        language: String = "zh",
        isHandwriting: Boolean = false
    ): ExtractionResult {
        Log.i(TAG, "Starting text extraction, size: ${bitmap.width}x${bitmap.height}, handwriting: $isHandwriting")

        val startTime = System.currentTimeMillis()

        // Convert bitmap to base64
        val base64 = bitmapToBase64(bitmap)

        // Build prompt
        val prompt = when {
            isHandwriting && language == "zh" -> HANDWRITING_PROMPT_ZH
            isHandwriting -> HANDWRITING_PROMPT_EN
            language == "zh" -> TEXT_EXTRACTION_PROMPT_ZH
            else -> TEXT_EXTRACTION_PROMPT_EN
        }

        // Build messages
        val messages = listOf(
            ChatMessage.system(getSystemPrompt(language)),
            ChatMessage.user(
                listOf(
                    ContentPart.imageBase64(base64, "image/png"),
                    ContentPart.text(prompt)
                )
            )
        )

        // Run inference (prefer on-device for privacy)
        val response = inferenceRouter.route(messages, InferenceMode.ON_DEVICE)

        val inferenceTime = System.currentTimeMillis() - startTime

        // Parse and format result
        val extractedText = response.action.trim()
        val hasHandwritingDetected = extractedText.contains("手写") ||
                                      extractedText.contains("[handwritten]")
        val hasUncertainty = extractedText.contains("[?]")

        // Estimate confidence
        val confidence = calculateConfidence(extractedText)

        // Detect languages
        val languages = detectLanguages(extractedText)

        // Collect notes
        val notes = mutableListOf<String>()
        if (hasHandwritingDetected) {
            notes.add(if (language == "zh") "检测到手写文字" else "Handwritten text detected")
        }
        if (hasUncertainty) {
            notes.add(if (language == "zh") "部分文字可能不准确" else "Some text may be inaccurate")
        }

        Log.i(TAG, "Text extraction complete, length: ${extractedText.length}, confidence: $confidence")

        return ExtractionResult(
            text = extractedText,
            confidence = confidence,
            hasHandwriting = hasHandwritingDetected,
            detectedLanguages = languages,
            notes = notes,
            inferenceTimeMs = inferenceTime
        )
    }

    /**
     * Extracts text from a base64-encoded image.
     */
    suspend fun extractTextFromBase64(
        base64Image: String,
        language: String = "zh",
        isHandwriting: Boolean = false
    ): ExtractionResult {
        Log.i(TAG, "Starting text extraction from base64, handwriting: $isHandwriting")

        val startTime = System.currentTimeMillis()

        val prompt = when {
            isHandwriting && language == "zh" -> HANDWRITING_PROMPT_ZH
            isHandwriting -> HANDWRITING_PROMPT_EN
            language == "zh" -> TEXT_EXTRACTION_PROMPT_ZH
            else -> TEXT_EXTRACTION_PROMPT_EN
        }

        val messages = listOf(
            ChatMessage.system(getSystemPrompt(language)),
            ChatMessage.user(
                listOf(
                    ContentPart.imageBase64(base64Image, "image/png"),
                    ContentPart.text(prompt)
                )
            )
        )

        val response = inferenceRouter.route(messages, InferenceMode.ON_DEVICE)

        val inferenceTime = System.currentTimeMillis() - startTime
        val extractedText = response.action.trim()

        val hasHandwritingDetected = extractedText.contains("手写") ||
                                      extractedText.contains("[handwritten]")
        val confidence = calculateConfidence(extractedText)
        val languages = detectLanguages(extractedText)

        val notes = mutableListOf<String>()
        if (hasHandwritingDetected) {
            notes.add(if (language == "zh") "检测到手写文字" else "Handwritten text detected")
        }
        if (extractedText.contains("[?]")) {
            notes.add(if (language == "zh") "部分文字可能不准确" else "Some text may be inaccurate")
        }

        return ExtractionResult(
            text = extractedText,
            confidence = confidence,
            hasHandwriting = hasHandwritingDetected,
            detectedLanguages = languages,
            notes = notes,
            inferenceTimeMs = inferenceTime
        )
    }

    /**
     * Copies extracted text to clipboard.
     *
     * @param text The text to copy
     * @return true if successful
     */
    fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Extracted Text", text)
            clipboard.setPrimaryClip(clip)
            Log.i(TAG, "Text copied to clipboard, length: ${text.length}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard: ${e.message}")
            false
        }
    }

    /**
     * Formats extracted text for display.
     *
     * @param result The extraction result
     * @param showMetadata Whether to include metadata
     * @return Formatted string
     */
    fun formatForDisplay(result: ExtractionResult, showMetadata: Boolean = true): String {
        val sb = StringBuilder()

        sb.append(result.text)

        if (showMetadata && result.notes.isNotEmpty()) {
            sb.append("\n\n---\n")
            result.notes.forEach { note ->
                sb.append("• $note\n")
            }
        }

        return sb.toString()
    }

    private fun getSystemPrompt(language: String): String {
        return if (language == "zh") {
            "你是一个专业的文字识别助手，擅长从图片中提取和识别文字内容。"
        } else {
            "You are a professional text recognition assistant, specialized in extracting and recognizing text content from images."
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun calculateConfidence(text: String): Float {
        // Heuristic confidence calculation
        var confidence = 1.0f

        // Reduce confidence for uncertain markers
        val uncertainCount = text.count { it == '?' } / 2 // [?] = 2 question marks worth
        confidence -= (uncertainCount * 0.05f).coerceAtMost(0.3f)

        // Reduce for very short text (might indicate recognition failure)
        if (text.length < 10) {
            confidence -= 0.2f
        }

        // Reduce for all caps (might be OCR artifacts)
        if (text.uppercase() == text && text.length > 20) {
            confidence -= 0.1f
        }

        return confidence.coerceIn(0.1f, 1.0f)
    }

    private fun detectLanguages(text: String): List<String> {
        val languages = mutableListOf<String>()

        // Simple heuristic language detection
        val chinesePattern = Regex("[\\u4e00-\\u9fa5]")
        val japanesePattern = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]")
        val koreanPattern = Regex("[\\uAC00-\\uD7AF]")

        if (chinesePattern.containsMatchIn(text)) {
            languages.add("zh")
        }
        if (japanesePattern.containsMatchIn(text)) {
            languages.add("ja")
        }
        if (koreanPattern.containsMatchIn(text)) {
            languages.add("ko")
        }

        // If no CJK characters, assume English
        if (languages.isEmpty()) {
            languages.add("en")
        }

        return languages
    }
}
