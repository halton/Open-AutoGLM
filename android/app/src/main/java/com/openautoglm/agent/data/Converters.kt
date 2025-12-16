package com.openautoglm.agent.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.openautoglm.agent.data.entities.ActionType
import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.data.entities.InferenceType
import com.openautoglm.agent.data.entities.TaskStatus
import java.util.UUID

/**
 * Common type converters for Room database.
 *
 * Centralizes all type conversions to avoid duplicate converter definitions.
 */
class Converters {
    private val gson = Gson()

    // UUID converters
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(uuidString: String?): UUID? = uuidString?.let { UUID.fromString(it) }

    // TaskStatus converters
    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(statusString: String): TaskStatus = TaskStatus.valueOf(statusString)

    // InferenceMode converters
    @TypeConverter
    fun fromInferenceMode(mode: InferenceMode): String = mode.name

    @TypeConverter
    fun toInferenceMode(modeString: String): InferenceMode = InferenceMode.valueOf(modeString)

    // ActionType converters
    @TypeConverter
    fun fromActionType(type: ActionType): String = type.name

    @TypeConverter
    fun toActionType(typeString: String): ActionType = ActionType.valueOf(typeString)

    // AppCategory converters
    @TypeConverter
    fun fromAppCategory(category: AppCategory): String = category.name

    @TypeConverter
    fun toAppCategory(categoryString: String): AppCategory = AppCategory.valueOf(categoryString)

    // InferenceType converters
    @TypeConverter
    fun fromInferenceType(type: InferenceType): String = type.name

    @TypeConverter
    fun toInferenceType(typeString: String): InferenceType = InferenceType.valueOf(typeString)

    // Map<String, Any> converters
    @TypeConverter
    fun fromMap(map: Map<String, Any>?): String? {
        return map?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toMap(json: String?): Map<String, Any>? {
        if (json == null) return null
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(json, type)
    }

    // List<String> converters
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

    // Map<String, String> converters
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

    // Map<String, List<String>> converters
    @TypeConverter
    fun fromStringListMap(map: Map<String, List<String>>?): String? {
        return map?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringListMap(json: String?): Map<String, List<String>>?{
        if (json == null) return null
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        return gson.fromJson(json, type)
    }

    // Set<String> converters
    @TypeConverter
    fun fromStringSet(set: Set<String>?): String? {
        return set?.joinToString(separator = ",")
    }

    @TypeConverter
    fun toStringSet(value: String?): Set<String>? {
        return value?.split(",")?.toSet()?.takeIf { it.isNotEmpty() && it.first().isNotEmpty() }
    }
}
