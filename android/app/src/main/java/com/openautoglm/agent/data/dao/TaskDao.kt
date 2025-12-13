package com.openautoglm.agent.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for Task entities.
 *
 * Provides CRUD operations and reactive queries for managing tasks
 * in the Room database.
 */
@Dao
interface TaskDao {

    /**
     * Insert a new task into the database.
     *
     * @param task The task to insert
     * @return The row ID of the inserted task
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    /**
     * Update an existing task in the database.
     *
     * @param task The task to update
     */
    @Update
    suspend fun update(task: Task)

    /**
     * Delete a task from the database.
     *
     * @param task The task to delete
     */
    @Delete
    suspend fun delete(task: Task)

    /**
     * Get a task by its unique identifier.
     *
     * @param id The UUID of the task
     * @return The task if found, null otherwise
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: UUID): Task?

    /**
     * Get all tasks as a reactive Flow.
     *
     * Results are ordered by creation time in descending order (newest first).
     *
     * @return Flow emitting list of all tasks
     */
    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    fun getAll(): Flow<List<Task>>

    /**
     * Get tasks filtered by status as a reactive Flow.
     *
     * Results are ordered by creation time in descending order.
     *
     * @param status The task status to filter by
     * @return Flow emitting list of tasks with the specified status
     */
    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY created_at DESC")
    fun getByStatus(status: TaskStatus): Flow<List<Task>>

    /**
     * Get active tasks (PENDING or RUNNING) as a reactive Flow.
     *
     * Active tasks are those that are either waiting to start or currently executing.
     * Results are ordered by creation time in ascending order (oldest first)
     * to process tasks in FIFO order.
     *
     * @return Flow emitting list of active tasks
     */
    @Query("SELECT * FROM tasks WHERE status = 'PENDING' OR status = 'RUNNING' ORDER BY created_at ASC")
    fun getActiveTasks(): Flow<List<Task>>

    /**
     * Delete all tasks from the database.
     *
     * Use with caution - this operation cannot be undone.
     */
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
