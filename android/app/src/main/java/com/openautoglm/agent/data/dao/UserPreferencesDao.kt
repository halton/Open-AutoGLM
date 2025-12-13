package com.openautoglm.agent.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openautoglm.agent.data.entities.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UserPreferences entities.
 * Provides CRUD operations and reactive queries using Kotlin Flow.
 *
 * Typically, there is only one row with id="default" that stores
 * the user's preferences for the agent.
 */
@Dao
interface UserPreferencesDao {

    /**
     * Insert user preferences.
     * If preferences with the same ID already exist, they will be ignored.
     *
     * @param preferences The user preferences to insert
     * @return The row ID of the inserted preferences, or -1 if ignored
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(preferences: UserPreferences): Long

    /**
     * Update existing user preferences.
     *
     * @param preferences The user preferences to update
     * @return Number of rows updated (1 if successful, 0 if not found)
     */
    @Update
    suspend fun update(preferences: UserPreferences): Int

    /**
     * Insert or update user preferences (upsert operation).
     * If preferences with the same ID exist, they will be replaced.
     *
     * @param preferences The user preferences to insert or update
     * @return The row ID of the inserted/updated preferences
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preferences: UserPreferences): Long

    /**
     * Get the default user preferences as a reactive Flow.
     * Emits new value whenever the preferences change.
     *
     * @return Flow of the default user preferences, or null if not found
     */
    @Query("SELECT * FROM user_preferences WHERE id = 'default'")
    fun getDefault(): Flow<UserPreferences?>

    /**
     * Get user preferences by ID.
     *
     * @param id The unique identifier of the preferences
     * @return The user preferences or null if not found
     */
    @Query("SELECT * FROM user_preferences WHERE id = :id")
    suspend fun getById(id: String): UserPreferences?

    /**
     * Delete all user preferences.
     * Use with caution - this removes all stored preferences.
     */
    @Query("DELETE FROM user_preferences")
    suspend fun deleteAll()
}
