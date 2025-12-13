package com.openautoglm.agent.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Entity representing an app mapping configuration.
 *
 * Maps package names to app metadata, UI patterns, and localized prompts
 * for the VLM agent to understand and interact with installed applications.
 */
@Entity(tableName = "app_mappings")
@TypeConverters(AppMappingConverters::class)
data class AppMapping(
    /**
     * Android package name (e.g., "com.android.settings")
     * Serves as the unique identifier for each app mapping
     */
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    /**
     * Human-readable display name of the application
     */
    @ColumnInfo(name = "app_name")
    val appName: String,

    /**
     * Alternative names or nicknames for the app
     * Used for natural language matching (e.g., ["Chrome", "Browser", "Google Chrome"])
     */
    @ColumnInfo(name = "aliases")
    val aliases: List<String>,

    /**
     * Category classification of the app
     */
    @ColumnInfo(name = "category")
    val category: AppCategory,

    /**
     * Optional path to the app's icon resource
     */
    @ColumnInfo(name = "icon_path")
    val iconPath: String? = null,

    /**
     * UI patterns for different screens/activities within the app
     * Key: screen identifier (e.g., "main", "settings", "search")
     * Value: List of UI element patterns to recognize
     * Stored as JSON in the database
     */
    @ColumnInfo(name = "ui_patterns")
    val uiPatterns: Map<String, List<String>>,

    /**
     * Localized prompt templates for the VLM
     * Must contain at least "zh" and "en" keys for Chinese and English
     * Stored as JSON in the database
     */
    @ColumnInfo(name = "prompts")
    val prompts: Map<String, String>,

    /**
     * Flag indicating if this mapping was created/modified by the user
     * User-defined mappings take precedence over system defaults
     */
    @ColumnInfo(name = "is_user_defined", defaultValue = "0")
    val isUserDefined: Boolean = false
) {
    init {
        require(prompts.containsKey("zh")) { "Prompts must contain 'zh' (Chinese) key" }
        require(prompts.containsKey("en")) { "Prompts must contain 'en' (English) key" }
    }

    companion object {
        /**
         * Creates a basic AppMapping with minimal required fields
         */
        fun create(
            packageName: String,
            appName: String,
            category: AppCategory,
            zhPrompt: String,
            enPrompt: String,
            aliases: List<String> = emptyList(),
            uiPatterns: Map<String, List<String>> = emptyMap(),
            iconPath: String? = null,
            isUserDefined: Boolean = false
        ): AppMapping {
            return AppMapping(
                packageName = packageName,
                appName = appName,
                aliases = aliases,
                category = category,
                iconPath = iconPath,
                uiPatterns = uiPatterns,
                prompts = mapOf("zh" to zhPrompt, "en" to enPrompt),
                isUserDefined = isUserDefined
            )
        }
    }
}

/**
 * Type converters for AppMapping entity.
 */
class AppMappingConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromAppCategory(category: AppCategory): String = category.name

    @TypeConverter
    fun toAppCategory(categoryString: String): AppCategory = AppCategory.valueOf(categoryString)

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        if (json == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? {
        return map?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringMap(json: String?): Map<String, String>? {
        if (json == null) return null
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun fromStringListMap(map: Map<String, List<String>>?): String? {
        return map?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringListMap(json: String?): Map<String, List<String>>? {
        if (json == null) return null
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        return gson.fromJson(json, type)
    }
}
