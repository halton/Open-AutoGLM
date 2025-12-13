package com.openautoglm.agent.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openautoglm.agent.data.entities.InferenceType
import com.openautoglm.agent.data.entities.ModelConfig
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ModelConfig entities.
 * Provides CRUD operations and reactive queries using Kotlin Flow.
 */
@Dao
interface ModelConfigDao {

    /**
     * Insert a single model configuration.
     * Replaces existing config if ID already exists.
     *
     * @param config The model configuration to insert
     * @return The row ID of the inserted config
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ModelConfig): Long

    /**
     * Insert multiple model configurations.
     * Replaces existing configs if IDs already exist.
     *
     * @param configs The list of model configurations to insert
     * @return List of row IDs for inserted configs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<ModelConfig>): List<Long>

    /**
     * Update an existing model configuration.
     *
     * @param config The model configuration to update
     * @return Number of rows updated (1 if successful, 0 if not found)
     */
    @Update
    suspend fun update(config: ModelConfig): Int

    /**
     * Delete a model configuration.
     *
     * @param config The model configuration to delete
     * @return Number of rows deleted (1 if successful, 0 if not found)
     */
    @Delete
    suspend fun delete(config: ModelConfig): Int

    /**
     * Get a model configuration by its ID.
     *
     * @param id The unique identifier of the config
     * @return The model configuration or null if not found
     */
    @Query("SELECT * FROM model_configs WHERE id = :id")
    suspend fun getById(id: String): ModelConfig?

    /**
     * Get all model configurations as a reactive Flow.
     * Emits new list whenever the data changes.
     *
     * @return Flow of all model configurations
     */
    @Query("SELECT * FROM model_configs ORDER BY model_name ASC")
    fun getAll(): Flow<List<ModelConfig>>

    /**
     * Get model configurations filtered by inference type.
     *
     * @param type The inference type to filter by (ON_DEVICE or CLOUD)
     * @return Flow of model configurations matching the type
     */
    @Query("SELECT * FROM model_configs WHERE type = :type ORDER BY model_name ASC")
    fun getByType(type: InferenceType): Flow<List<ModelConfig>>

    /**
     * Get only active model configurations.
     *
     * @return Flow of active model configurations
     */
    @Query("SELECT * FROM model_configs WHERE is_active = 1 ORDER BY model_name ASC")
    fun getActiveConfigs(): Flow<List<ModelConfig>>

    /**
     * Update the active status of a model configuration.
     *
     * @param id The unique identifier of the config
     * @param isActive The new active status
     * @return Number of rows updated (1 if successful, 0 if not found)
     */
    @Query("UPDATE model_configs SET is_active = :isActive WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean): Int

    /**
     * Delete all model configurations.
     * Use with caution - this removes all stored configs.
     */
    @Query("DELETE FROM model_configs")
    suspend fun deleteAll()
}
