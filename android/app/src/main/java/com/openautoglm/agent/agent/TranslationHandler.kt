package com.openautoglm.agent.agent

import android.util.Log
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.inference.InferenceRouterImpl
import com.openautoglm.agent.model.ChatMessage
import com.openautoglm.agent.model.ContentPart

/**
 * Handles text translation requests.
 *
 * Supports:
 * - Direct text translation
 * - Translation from image (extract + translate)
 * - Multiple language pairs
 * - Cloud routing for better accuracy
 *
 * @param inferenceRouter Inference router for VLM calls
 */
class TranslationHandler(
    private val inferenceRouter: InferenceRouterImpl
) {
    companion object {
        private const val TAG = "TranslationHandler"

        // Supported languages
        val SUPPORTED_LANGUAGES = mapOf(
            "zh" to "中文 (Chinese)",
            "en" to "English",
            "ja" to "日本語 (Japanese)",
            "ko" to "한국어 (Korean)",
            "es" to "Español (Spanish)",
            "fr" to "Français (French)",
            "de" to "Deutsch (German)",
            "ru" to "Русский (Russian)",
            "pt" to "Português (Portuguese)",
            "ar" to "العربية (Arabic)"
        )

        // Common language aliases
        private val LANGUAGE_ALIASES = mapOf(
            "chinese" to "zh", "中文" to "zh", "汉语" to "zh",
            "english" to "en", "英文" to "en", "英语" to "en",
            "japanese" to "ja", "日文" to "ja", "日语" to "ja",
            "korean" to "ko", "韩文" to "ko", "韩语" to "ko",
            "spanish" to "es", "西班牙语" to "es",
            "french" to "fr", "法语" to "fr",
            "german" to "de", "德语" to "de",
            "russian" to "ru", "俄语" to "ru",
            "portuguese" to "pt", "葡萄牙语" to "pt",
            "arabic" to "ar", "阿拉伯语" to "ar"
        )
    }

    /**
     * Result of a translation.
     */
    data class TranslationResult(
        /** Original text */
        val originalText: String,
        /** Translated text */
        val translatedText: String,
        /** Source language code */
        val sourceLanguage: String,
        /** Target language code */
        val targetLanguage: String,
        /** Inference time in milliseconds */
        val inferenceTimeMs: Long,
        /** Any notes or warnings */
        val notes: List<String>
    )

    /**
     * Translates text from one language to another.
     *
     * @param text The text to translate
     * @param targetLanguage Target language code or name
     * @param sourceLanguage Source language code (auto-detect if null)
     * @return Translation result
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String? = null
    ): TranslationResult {
        Log.i(TAG, "Translating text (${text.length} chars) to $targetLanguage")

        val startTime = System.currentTimeMillis()

        // Normalize language codes
        val targetLang = normalizeLanguageCode(targetLanguage)
        val sourceLang = sourceLanguage?.let { normalizeLanguageCode(it) }

        // Build translation prompt
        val prompt = buildTranslationPrompt(text, targetLang, sourceLang)

        val messages = listOf(
            ChatMessage.system(getTranslationSystemPrompt(targetLang)),
            ChatMessage.user(prompt)
        )

        // Use cloud for translation (better accuracy)
        val response = inferenceRouter.route(messages, InferenceMode.CLOUD)

        val inferenceTime = System.currentTimeMillis() - startTime
        val translatedText = cleanTranslationOutput(response.action)

        val detectedSource = sourceLang ?: detectSourceLanguage(text)

        val notes = mutableListOf<String>()
        if (translatedText.length < text.length / 3) {
            notes.add("Translation may be incomplete")
        }

        Log.i(TAG, "Translation complete, ${translatedText.length} chars, ${inferenceTime}ms")

        return TranslationResult(
            originalText = text,
            translatedText = translatedText,
            sourceLanguage = detectedSource,
            targetLanguage = targetLang,
            inferenceTimeMs = inferenceTime,
            notes = notes
        )
    }

    /**
     * Translates text from an image (extract + translate).
     *
     * @param imageBase64 Base64-encoded image
     * @param targetLanguage Target language code or name
     * @return Translation result
     */
    suspend fun translateFromImage(
        imageBase64: String,
        targetLanguage: String
    ): TranslationResult {
        Log.i(TAG, "Translating text from image to $targetLanguage")

        val startTime = System.currentTimeMillis()

        val targetLang = normalizeLanguageCode(targetLanguage)

        // Combined prompt for extraction and translation
        val prompt = buildImageTranslationPrompt(targetLang)

        val messages = listOf(
            ChatMessage.system(getTranslationSystemPrompt(targetLang)),
            ChatMessage.user(
                listOf(
                    ContentPart.imageBase64(imageBase64, "image/png"),
                    ContentPart.text(prompt)
                )
            )
        )

        // Use cloud for image translation (more complex)
        val response = inferenceRouter.route(messages, InferenceMode.CLOUD)

        val inferenceTime = System.currentTimeMillis() - startTime

        // Parse response to extract original and translated text
        val (original, translated) = parseImageTranslationResponse(response.action)

        val detectedSource = detectSourceLanguage(original)

        return TranslationResult(
            originalText = original,
            translatedText = translated,
            sourceLanguage = detectedSource,
            targetLanguage = targetLang,
            inferenceTimeMs = inferenceTime,
            notes = emptyList()
        )
    }

    /**
     * Detects the target language from a natural language request.
     *
     * @param request The user's translation request
     * @return Detected target language code
     */
    fun detectTargetLanguage(request: String): String? {
        val lowerRequest = request.lowercase()

        // Check for explicit language mentions
        for ((alias, code) in LANGUAGE_ALIASES) {
            if (lowerRequest.contains(alias)) {
                return code
            }
        }

        // Check for patterns like "translate to X" or "翻译成X"
        val toPatterns = listOf(
            Regex("translate\\s+(?:in)?to\\s+(\\w+)"),
            Regex("翻译[成到为]([\\u4e00-\\u9fa5]+)"),
            Regex("译[成为]([\\u4e00-\\u9fa5]+)")
        )

        for (pattern in toPatterns) {
            val match = pattern.find(lowerRequest)
            if (match != null) {
                val targetName = match.groupValues[1]
                LANGUAGE_ALIASES[targetName.lowercase()]?.let { return it }
            }
        }

        // Default: if text is Chinese, translate to English, else to Chinese
        return if (containsChinese(request)) "en" else "zh"
    }

    private fun normalizeLanguageCode(language: String): String {
        val lower = language.lowercase().trim()
        return LANGUAGE_ALIASES[lower] ?: lower.take(2)
    }

    private fun buildTranslationPrompt(text: String, targetLang: String, sourceLang: String?): String {
        val targetName = SUPPORTED_LANGUAGES[targetLang] ?: targetLang

        return if (sourceLang != null) {
            val sourceName = SUPPORTED_LANGUAGES[sourceLang] ?: sourceLang
            """
Please translate the following text from $sourceName to $targetName.
Output only the translation, without any explanation.

Text to translate:
$text
            """.trimIndent()
        } else {
            """
Please translate the following text to $targetName.
Output only the translation, without any explanation.

Text to translate:
$text
            """.trimIndent()
        }
    }

    private fun buildImageTranslationPrompt(targetLang: String): String {
        val targetName = SUPPORTED_LANGUAGES[targetLang] ?: targetLang

        return """
Please extract the text from this image and translate it to $targetName.

Format your response as:
ORIGINAL:
[extracted text]

TRANSLATION:
[translated text]
        """.trimIndent()
    }

    private fun getTranslationSystemPrompt(targetLang: String): String {
        val targetName = SUPPORTED_LANGUAGES[targetLang] ?: targetLang

        return """
You are a professional translator. Your task is to accurately translate text to $targetName.
- Maintain the original meaning and tone
- Preserve formatting (paragraphs, lists, etc.)
- Handle idioms and cultural references appropriately
- For proper nouns, keep the original with translation in parentheses if helpful
        """.trimIndent()
    }

    private fun parseImageTranslationResponse(response: String): Pair<String, String> {
        val originalPattern = Regex("ORIGINAL:\\s*(.+?)\\s*TRANSLATION:", RegexOption.DOT_MATCHES_ALL)
        val translationPattern = Regex("TRANSLATION:\\s*(.+)", RegexOption.DOT_MATCHES_ALL)

        val originalMatch = originalPattern.find(response)
        val translationMatch = translationPattern.find(response)

        val original = originalMatch?.groupValues?.get(1)?.trim() ?: ""
        val translated = translationMatch?.groupValues?.get(1)?.trim() ?: response.trim()

        return Pair(original, translated)
    }

    private fun cleanTranslationOutput(output: String): String {
        // Remove common prefixes that models sometimes add
        val prefixes = listOf(
            "Translation:", "翻译:", "译文:",
            "Here is the translation:", "The translation is:"
        )

        var result = output.trim()
        for (prefix in prefixes) {
            if (result.startsWith(prefix, ignoreCase = true)) {
                result = result.removePrefix(prefix).trim()
            }
        }

        return result
    }

    private fun detectSourceLanguage(text: String): String {
        return when {
            containsChinese(text) -> "zh"
            containsJapanese(text) -> "ja"
            containsKorean(text) -> "ko"
            containsArabic(text) -> "ar"
            containsCyrillic(text) -> "ru"
            else -> "en"
        }
    }

    private fun containsChinese(text: String): Boolean {
        return Regex("[\\u4e00-\\u9fa5]").containsMatchIn(text)
    }

    private fun containsJapanese(text: String): Boolean {
        return Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]").containsMatchIn(text)
    }

    private fun containsKorean(text: String): Boolean {
        return Regex("[\\uAC00-\\uD7AF]").containsMatchIn(text)
    }

    private fun containsArabic(text: String): Boolean {
        return Regex("[\\u0600-\\u06FF]").containsMatchIn(text)
    }

    private fun containsCyrillic(text: String): Boolean {
        return Regex("[\\u0400-\\u04FF]").containsMatchIn(text)
    }
}
