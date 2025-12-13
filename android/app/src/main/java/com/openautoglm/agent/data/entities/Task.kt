package com.openautoglm.agent.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

/**
 * Task entity representing an agent task in the database.
 *
 * A task encapsulates a user request that the VLM agent will execute
 * through a series of steps until completion or failure.
 */
@Entity(tableName = "tasks")
@TypeConverters(TaskConverters::class)
data class Task(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID = UUID.randomUUID(),

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "status")
    val status: TaskStatus = TaskStatus.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "result")
    val result: String? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "step_count")
    val stepCount: Int = 0,

    @ColumnInfo(name = "max_steps")
    val maxSteps: Int = 100,

    @ColumnInfo(name = "inference_mode")
    val inferenceMode: InferenceMode = InferenceMode.CLOUD
) {
    init {
        require(description.length <= 1000) {
            "Task description must not exceed 1000 characters"
        }
        require(stepCount >= 0) {
            "Step count must be non-negative"
        }
        require(maxSteps > 0) {
            "Max steps must be positive"
        }
    }

    /**
     * Check if the task can accept more steps.
     */
    fun canContinue(): Boolean = stepCount < maxSteps && status == TaskStatus.RUNNING

    /**
     * Check if the task has reached the maximum number of steps.
     */
    fun hasReachedMaxSteps(): Boolean = stepCount >= maxSteps
}

/**
 * Type converters for Room database to handle UUID and enum types.
 */
class TaskConverters {
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(uuidString: String?): UUID? = uuidString?.let { UUID.fromString(it) }

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(statusString: String): TaskStatus = TaskStatus.valueOf(statusString)

    @TypeConverter
    fun fromInferenceMode(mode: InferenceMode): String = mode.name

    @TypeConverter
    fun toInferenceMode(modeString: String): InferenceMode = InferenceMode.valueOf(modeString)
}
