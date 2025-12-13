package com.openautoglm.agent.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.data.entities.AppMapping
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AppMapping entities.
 *
 * Provides CRUD operations and reactive queries for app mapping configurations
 * used by the VLM agent to understand and interact with installed applications.
 */
@Dao
interface AppMappingDao {

    /**
     * Insert a single app mapping.
     *
     * @param appMapping The app mapping to insert
     * @return The row ID of the inserted mapping
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appMapping: AppMapping): Long

    /**
     * Insert multiple app mappings.
     *
     * @param mappings The list of app mappings to insert
     * @return List of row IDs for inserted mappings
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<AppMapping>): List<Long>

    /**
     * Update an existing app mapping.
     *
     * @param appMapping The app mapping to update
     * @return Number of rows updated
     */
    @Update
    suspend fun update(appMapping: AppMapping): Int

    /**
     * Delete an app mapping.
     *
     * @param appMapping The app mapping to delete
     * @return Number of rows deleted
     */
    @Delete
    suspend fun delete(appMapping: AppMapping): Int

    /**
     * Get an app mapping by its package name.
     *
     * @param packageName The Android package name to look up
     * @return The app mapping if found, null otherwise
     */
    @Query("SELECT * FROM app_mappings WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): AppMapping?

    /**
     * Get all app mappings as a reactive Flow.
     *
     * @return Flow emitting list of all app mappings
     */
    @Query("SELECT * FROM app_mappings ORDER BY app_name ASC")
    fun getAll(): Flow<List<AppMapping>>

    /**
     * Get app mappings by category as a reactive Flow.
     *
     * @param category The app category to filter by
     * @return Flow emitting list of app mappings in the specified category
     */
    @Query("SELECT * FROM app_mappings WHERE category = :category ORDER BY app_name ASC")
    fun getByCategory(category: AppCategory): Flow<List<AppMapping>>

    /**
     * Search app mappings by name or aliases.
     *
     * Performs a case-insensitive search on the app name and aliases fields.
     * The aliases field is stored as JSON, so we use LIKE for pattern matching.
     *
     * @param query The search query string
     * @return Flow emitting list of matching app mappings
     */
    @Query("""
        SELECT * FROM app_mappings
        WHERE app_name LIKE '%' || :query || '%' COLLATE NOCASE
           OR aliases LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY app_name ASC
    """)
    fun searchByNameOrAlias(query: String): Flow<List<AppMapping>>

    /**
     * Get all user-defined app mappings.
     *
     * User-defined mappings are those created or modified by the user,
     * as opposed to system default mappings.
     *
     * @return Flow emitting list of user-defined app mappings
     */
    @Query("SELECT * FROM app_mappings WHERE is_user_defined = 1 ORDER BY app_name ASC")
    fun getUserDefined(): Flow<List<AppMapping>>

    /**
     * Delete all app mappings from the database.
     */
    @Query("DELETE FROM app_mappings")
    suspend fun deleteAll()
}
