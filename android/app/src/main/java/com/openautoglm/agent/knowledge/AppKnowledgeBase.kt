package com.openautoglm.agent.knowledge

import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.data.entities.AppMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * App knowledge base that combines default app mappings with user-customized mappings.
 *
 * This class provides app lookup and knowledge management for the VLM Android Agent,
 * enabling intelligent app resolution from natural language queries and providing
 * localized prompts for VLM interactions.
 *
 * @param repository The AgentRepository for database operations
 */
class AppKnowledgeBase(
    private val repository: AgentRepository
) {

    /**
     * Initialize the knowledge base by loading default mappings into the database.
     *
     * This method checks if default mappings are already present and only inserts
     * them if the database is empty or missing specific mappings. User-defined
     * mappings are preserved and take precedence over defaults.
     */
    suspend fun initialize() {
        val existingMappings = repository.getAllAppMappings().first()
        val existingPackages = existingMappings.map { it.packageName }.toSet()

        // Get default mappings that are not already in the database
        val defaultMappings = DefaultAppMappings.getAllMappings()
        val newMappings = defaultMappings.filter { mapping ->
            mapping.packageName !in existingPackages
        }

        if (newMappings.isNotEmpty()) {
            repository.insertAppMappings(newMappings)
        }
    }

    /**
     * Look up an app mapping by its Android package name.
     *
     * @param packageName The Android package name (e.g., "com.android.settings")
     * @return The AppMapping if found, null otherwise
     */
    suspend fun getAppByPackage(packageName: String): AppMapping? {
        return repository.getAppMappingByPackageName(packageName)
    }

    /**
     * Get all app mappings in a specific category as a reactive Flow.
     *
     * @param category The AppCategory to filter by
     * @return Flow emitting list of AppMappings in the specified category
     */
    fun getAppsByCategory(category: AppCategory): Flow<List<AppMapping>> {
        return repository.getAppMappingsByCategory(category)
    }

    /**
     * Search for apps by name or alias.
     *
     * Performs a case-insensitive search across app names and aliases.
     *
     * @param query The search query string
     * @return List of matching AppMappings
     */
    suspend fun searchApps(query: String): List<AppMapping> {
        if (query.isBlank()) {
            return emptyList()
        }
        return repository.searchAppMappings(query.trim()).first()
    }

    /**
     * Resolve an app from natural language text.
     *
     * This method performs NLP-like resolution to find the most relevant app
     * from a natural language description or command. It uses multiple strategies:
     * 1. Exact package name match
     * 2. Exact app name match (case-insensitive)
     * 3. Alias matching
     * 4. Fuzzy keyword matching
     *
     * @param text The natural language text to resolve
     * @return The best matching AppMapping, or null if no match found
     */
    suspend fun resolveAppFromText(text: String): AppMapping? {
        if (text.isBlank()) {
            return null
        }

        val normalizedText = text.trim().lowercase(Locale.getDefault())
        val allMappings = repository.getAllAppMappings().first()

        // Strategy 1: Exact package name match
        allMappings.find { it.packageName.equals(normalizedText, ignoreCase = true) }
            ?.let { return it }

        // Strategy 2: Exact app name match
        allMappings.find { it.appName.equals(normalizedText, ignoreCase = true) }
            ?.let { return it }

        // Strategy 3: Alias matching (exact match)
        allMappings.find { mapping ->
            mapping.aliases.any { alias ->
                alias.equals(normalizedText, ignoreCase = true)
            }
        }?.let { return it }

        // Strategy 4: Partial matching with scoring
        val scoredMappings = allMappings.mapNotNull { mapping ->
            val score = calculateMatchScore(normalizedText, mapping)
            if (score > 0) Pair(mapping, score) else null
        }.sortedByDescending { it.second }

        return scoredMappings.firstOrNull()?.first
    }

    /**
     * Calculate a match score between text and an app mapping.
     *
     * Higher scores indicate better matches.
     *
     * @param text The normalized search text
     * @param mapping The app mapping to score
     * @return Match score (0 if no match)
     */
    private fun calculateMatchScore(text: String, mapping: AppMapping): Int {
        var score = 0
        val words = text.split(Regex("\\s+"))

        // Check app name contains the text or vice versa
        val appNameLower = mapping.appName.lowercase(Locale.getDefault())
        if (appNameLower.contains(text)) {
            score += 100
        } else if (text.contains(appNameLower)) {
            score += 80
        }

        // Check each word against app name
        for (word in words) {
            if (word.length < 2) continue
            if (appNameLower.contains(word)) {
                score += 20
            }
        }

        // Check aliases
        for (alias in mapping.aliases) {
            val aliasLower = alias.lowercase(Locale.getDefault())
            if (aliasLower.contains(text)) {
                score += 90
            } else if (text.contains(aliasLower)) {
                score += 70
            }

            for (word in words) {
                if (word.length < 2) continue
                if (aliasLower.contains(word)) {
                    score += 15
                }
            }
        }

        // Common action keywords that might indicate app intent
        val appKeywordMatches = mapOf(
            "settings" to listOf("com.android.settings"),
            "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
            "photos" to listOf("com.google.android.apps.photos"),
            "browser" to listOf("com.android.chrome", "com.microsoft.emmx"),
            "chrome" to listOf("com.android.chrome"),
            "edge" to listOf("com.microsoft.emmx"),
            "message" to listOf("com.google.android.apps.messaging"),
            "sms" to listOf("com.google.android.apps.messaging"),
            "phone" to listOf("com.android.dialer", "com.google.android.dialer"),
            "call" to listOf("com.android.dialer", "com.google.android.dialer"),
            "maps" to listOf("com.google.android.apps.maps"),
            "navigation" to listOf("com.google.android.apps.maps"),
            "email" to listOf("com.google.android.gm", "com.microsoft.office.outlook"),
            "gmail" to listOf("com.google.android.gm"),
            "calendar" to listOf("com.google.android.calendar"),
            "clock" to listOf("com.android.deskclock", "com.google.android.deskclock"),
            "alarm" to listOf("com.android.deskclock", "com.google.android.deskclock"),
            "calculator" to listOf("com.android.calculator2", "com.google.android.calculator"),
            "wechat" to listOf("com.tencent.mm"),
            "weixin" to listOf("com.tencent.mm"),
            "alipay" to listOf("com.eg.android.AlipayGphone"),
            "taobao" to listOf("com.taobao.taobao"),
            "jd" to listOf("com.jingdong.app.mall"),
            "meituan" to listOf("com.sankuai.meituan"),
            "didi" to listOf("com.sdu.didi.psnger"),
            "douyin" to listOf("com.ss.android.ugc.aweme"),
            "tiktok" to listOf("com.zhiliaoapp.musically")
        )

        for (word in words) {
            appKeywordMatches[word]?.let { matchingPackages ->
                if (mapping.packageName in matchingPackages) {
                    score += 50
                }
            }
        }

        return score
    }

    /**
     * Get the localized prompt template for an app.
     *
     * Returns the prompt in the specified language, falling back to English
     * if the requested language is not available.
     *
     * @param appMapping The app mapping containing prompts
     * @param language The language code (e.g., "zh", "en")
     * @return The localized prompt string
     */
    fun getPromptForApp(appMapping: AppMapping, language: String): String {
        // Try exact language match
        appMapping.prompts[language]?.let { return it }

        // Try language without region (e.g., "zh-CN" -> "zh")
        val baseLanguage = language.split("-", "_").firstOrNull() ?: language
        appMapping.prompts[baseLanguage]?.let { return it }

        // Fall back to English
        return appMapping.prompts["en"] ?: appMapping.prompts.values.firstOrNull() ?: ""
    }

    /**
     * Add a user-defined app mapping.
     *
     * User-defined mappings take precedence over system defaults.
     * If a mapping for the same package already exists, it will be replaced.
     *
     * @param mapping The AppMapping to add (will be marked as user-defined)
     */
    suspend fun addCustomMapping(mapping: AppMapping) {
        // Ensure the mapping is marked as user-defined
        val userMapping = if (!mapping.isUserDefined) {
            mapping.copy(isUserDefined = true)
        } else {
            mapping
        }

        // Insert or replace the mapping
        repository.insertAppMapping(userMapping)
    }

    companion object {
        private const val TAG = "AppKnowledgeBase"
    }
}
