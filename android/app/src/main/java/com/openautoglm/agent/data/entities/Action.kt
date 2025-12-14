package com.openautoglm.agent.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Entity representing an action executed by the VLM-powered Android agent.
 *
 * Actions are the atomic operations performed by the agent to accomplish tasks,
 * such as tapping, typing, swiping, or navigating.
 */
@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class Action(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID = UUID.randomUUID(),

    @ColumnInfo(name = "task_id")
    val taskId: UUID,

    @ColumnInfo(name = "type")
    val type: ActionType,

    @ColumnInfo(name = "parameters")
    val parameters: Map<String, Any> = emptyMap(),

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "success")
    val success: Boolean = false,

    @ColumnInfo(name = "thinking")
    val thinking: String = "",

    @ColumnInfo(name = "screen_state_id")
    val screenStateId: String? = null
) {
    init {
        require(thinking.length <= MAX_THINKING_LENGTH) {
            "Thinking text exceeds maximum length of $MAX_THINKING_LENGTH characters"
        }
    }

    companion object {
        const val MAX_THINKING_LENGTH = 2000
    }
}

/**
 * Type converters for Action entity.
 */
