package com.openautoglm.agent.data

import com.openautoglm.agent.data.dao.ActionDao
import com.openautoglm.agent.data.dao.AppMappingDao
import com.openautoglm.agent.data.dao.ModelConfigDao
import com.openautoglm.agent.data.dao.TaskDao
import com.openautoglm.agent.data.dao.UserPreferencesDao
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.data.entities.AppMapping
import com.openautoglm.agent.data.entities.InferenceType
import com.openautoglm.agent.data.entities.ModelConfig
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.TaskStatus
import com.openautoglm.agent.data.entities.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository providing a clean API for data access to the rest of the application.
 *
 * This repository abstracts the data sources (Room database) and provides
 * a single source of truth for agent-related data operations.
 */
class AgentRepository(
    private val taskDao: TaskDao,
    private val actionDao: ActionDao,
    private val appMappingDao: AppMappingDao,
    private val userPreferencesDao: UserPreferencesDao,
    private val modelConfigDao: ModelConfigDao
) {
    // ==================== Task Operations ====================

    /**
     * Get all tasks as a Flow.
     */
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAll()

    /**
     * Get tasks by status.
     */
    fun getTasksByStatus(status: TaskStatus): Flow<List<Task>> = taskDao.getByStatus(status)

    /**
     * Get active tasks (PENDING or RUNNING).
     */
    fun getActiveTasks(): Flow<List<Task>> = taskDao.getActiveTasks()

    /**
     * Get a task by ID.
     */
    suspend fun getTaskById(id: UUID): Task? = taskDao.getById(id)

    /**
     * Insert a new task.
     */
    suspend fun insertTask(task: Task) = taskDao.insert(task)

    /**
     * Update an existing task.
     */
    suspend fun updateTask(task: Task) = taskDao.update(task)

    /**
     * Delete a task.
     */
    suspend fun deleteTask(task: Task) = taskDao.delete(task)

    /**
     * Delete all tasks.
     */
    suspend fun deleteAllTasks() = taskDao.deleteAll()

    // ==================== Action Operations ====================

    /**
     * Get actions for a specific task.
     */
    fun getActionsByTaskId(taskId: UUID): Flow<List<Action>> = actionDao.getByTaskId(taskId)

    /**
     * Get actions for a task, ordered by timestamp.
     */
    fun getActionsByTaskIdOrdered(taskId: UUID): Flow<List<Action>> = actionDao.getByTaskIdOrdered(taskId)

    /**
     * Get recent actions across all tasks.
     */
    fun getRecentActions(limit: Int): Flow<List<Action>> = actionDao.getRecentActions(limit)

    /**
     * Get an action by ID.
     */
    suspend fun getActionById(id: UUID): Action? = actionDao.getById(id)

    /**
     * Insert a new action.
     */
    suspend fun insertAction(action: Action) = actionDao.insert(action)

    /**
     * Insert multiple actions.
     */
    suspend fun insertActions(actions: List<Action>) = actionDao.insertAll(actions)

    /**
     * Update an existing action.
     */
    suspend fun updateAction(action: Action) = actionDao.update(action)

    /**
     * Delete an action.
     */
    suspend fun deleteAction(action: Action) = actionDao.delete(action)

    /**
     * Delete all actions for a task.
     */
    suspend fun deleteActionsByTaskId(taskId: UUID) = actionDao.deleteByTaskId(taskId)

    // ==================== App Mapping Operations ====================

    /**
     * Get all app mappings.
     */
    fun getAllAppMappings(): Flow<List<AppMapping>> = appMappingDao.getAll()

    /**
     * Get app mappings by category.
     */
    fun getAppMappingsByCategory(category: AppCategory): Flow<List<AppMapping>> =
        appMappingDao.getByCategory(category)

    /**
     * Get user-defined app mappings.
     */
    fun getUserDefinedAppMappings(): Flow<List<AppMapping>> = appMappingDao.getUserDefined()

    /**
     * Search app mappings by name or alias.
     */
    fun searchAppMappings(query: String): Flow<List<AppMapping>> =
        appMappingDao.searchByNameOrAlias(query)

    /**
     * Get an app mapping by package name.
     */
    suspend fun getAppMappingByPackageName(packageName: String): AppMapping? =
        appMappingDao.getByPackageName(packageName)

    /**
     * Insert a new app mapping.
     */
    suspend fun insertAppMapping(appMapping: AppMapping) = appMappingDao.insert(appMapping)

    /**
     * Insert multiple app mappings.
     */
    suspend fun insertAppMappings(mappings: List<AppMapping>) = appMappingDao.insertAll(mappings)

    /**
     * Update an existing app mapping.
     */
    suspend fun updateAppMapping(appMapping: AppMapping) = appMappingDao.update(appMapping)

    /**
     * Delete an app mapping.
     */
    suspend fun deleteAppMapping(appMapping: AppMapping) = appMappingDao.delete(appMapping)

    /**
     * Delete all app mappings.
     */
    suspend fun deleteAllAppMappings() = appMappingDao.deleteAll()

    // ==================== User Preferences Operations ====================

    /**
     * Get default user preferences as a Flow.
     */
    fun getDefaultPreferences(): Flow<UserPreferences?> = userPreferencesDao.getDefault()

    /**
     * Get user preferences by ID.
     */
    suspend fun getPreferencesById(id: String): UserPreferences? = userPreferencesDao.getById(id)

    /**
     * Insert or update user preferences.
     */
    suspend fun upsertPreferences(preferences: UserPreferences) = userPreferencesDao.upsert(preferences)

    /**
     * Delete all user preferences.
     */
    suspend fun deleteAllPreferences() = userPreferencesDao.deleteAll()

    // ==================== Model Config Operations ====================

    /**
     * Get all model configs.
     */
    fun getAllModelConfigs(): Flow<List<ModelConfig>> = modelConfigDao.getAll()

    /**
     * Get model configs by inference type.
     */
    fun getModelConfigsByType(type: InferenceType): Flow<List<ModelConfig>> =
        modelConfigDao.getByType(type)

    /**
     * Get active model configs.
     */
    fun getActiveModelConfigs(): Flow<List<ModelConfig>> = modelConfigDao.getActiveConfigs()

    /**
     * Get a model config by ID.
     */
    suspend fun getModelConfigById(id: String): ModelConfig? = modelConfigDao.getById(id)

    /**
     * Insert a new model config.
     */
    suspend fun insertModelConfig(config: ModelConfig) = modelConfigDao.insert(config)

    /**
     * Insert multiple model configs.
     */
    suspend fun insertModelConfigs(configs: List<ModelConfig>) = modelConfigDao.insertAll(configs)

    /**
     * Update an existing model config.
     */
    suspend fun updateModelConfig(config: ModelConfig) = modelConfigDao.update(config)

    /**
     * Set a model config's active status.
     */
    suspend fun setModelConfigActive(id: String, isActive: Boolean) =
        modelConfigDao.setActive(id, isActive)

    /**
     * Delete a model config.
     */
    suspend fun deleteModelConfig(config: ModelConfig) = modelConfigDao.delete(config)

    /**
     * Delete all model configs.
     */
    suspend fun deleteAllModelConfigs() = modelConfigDao.deleteAll()

    companion object {
        @Volatile
        private var INSTANCE: AgentRepository? = null

        /**
         * Get the singleton repository instance.
         *
         * @param database The AppDatabase instance
         * @return The repository instance
         */
        fun getInstance(database: AppDatabase): AgentRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentRepository(
                    taskDao = database.taskDao(),
                    actionDao = database.actionDao(),
                    appMappingDao = database.appMappingDao(),
                    userPreferencesDao = database.userPreferencesDao(),
                    modelConfigDao = database.modelConfigDao()
                ).also { INSTANCE = it }
            }
        }
    }
}
