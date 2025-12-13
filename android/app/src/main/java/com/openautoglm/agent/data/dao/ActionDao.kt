package com.openautoglm.agent.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openautoglm.agent.data.entities.Action
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for Action entities.
 *
 * Provides CRUD operations and reactive queries for actions executed by the
 * VLM-powered Android agent. Actions represent atomic operations like tapping,
 * typing, swiping, or navigating.
 */
@Dao
interface ActionDao {

    /**
     * Insert a single action.
     *
     * @param action The action to insert
     * @return The row ID of the inserted action
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: Action): Long

    /**
     * Insert multiple actions.
     *
     * @param actions The list of actions to insert
     * @return List of row IDs for the inserted actions
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actions: List<Action>): List<Long>

    /**
     * Update an existing action.
     *
     * @param action The action to update
     * @return The number of rows updated (should be 1 if successful)
     */
    @Update
    suspend fun update(action: Action): Int

    /**
     * Delete an action.
     *
     * @param action The action to delete
     * @return The number of rows deleted (should be 1 if successful)
     */
    @Delete
    suspend fun delete(action: Action): Int

    /**
     * Get an action by its ID.
     *
     * @param id The UUID of the action
     * @return The action if found, null otherwise
     */
    @Query("SELECT * FROM actions WHERE id = :id")
    suspend fun getById(id: UUID): Action?

    /**
     * Get all actions for a specific task.
     *
     * Returns a Flow that emits updates whenever the underlying data changes.
     *
     * @param taskId The UUID of the task
     * @return Flow emitting the list of actions for the task
     */
    @Query("SELECT * FROM actions WHERE task_id = :taskId")
    fun getByTaskId(taskId: UUID): Flow<List<Action>>

    /**
     * Get all actions for a specific task, ordered by timestamp ascending.
     *
     * Returns a Flow that emits updates whenever the underlying data changes.
     * Actions are ordered chronologically to show execution sequence.
     *
     * @param taskId The UUID of the task
     * @return Flow emitting the ordered list of actions for the task
     */
    @Query("SELECT * FROM actions WHERE task_id = :taskId ORDER BY timestamp ASC")
    fun getByTaskIdOrdered(taskId: UUID): Flow<List<Action>>

    /**
     * Get recent actions across all tasks.
     *
     * Returns a Flow that emits updates whenever the underlying data changes.
     * Actions are ordered by timestamp descending (most recent first).
     *
     * @param limit Maximum number of actions to return
     * @return Flow emitting the list of recent actions
     */
    @Query("SELECT * FROM actions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentActions(limit: Int): Flow<List<Action>>

    /**
     * Delete all actions for a specific task.
     *
     * Note: This may not be necessary if cascade delete is configured on the
     * foreign key relationship, but provides explicit control when needed.
     *
     * @param taskId The UUID of the task whose actions should be deleted
     * @return The number of rows deleted
     */
    @Query("DELETE FROM actions WHERE task_id = :taskId")
    suspend fun deleteByTaskId(taskId: UUID): Int
}
