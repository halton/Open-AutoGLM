package com.openautoglm.agent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.openautoglm.agent.data.dao.ActionDao
import com.openautoglm.agent.data.dao.AppMappingDao
import com.openautoglm.agent.data.dao.ModelConfigDao
import com.openautoglm.agent.data.dao.TaskDao
import com.openautoglm.agent.data.dao.UserPreferencesDao
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.AppMapping
import com.openautoglm.agent.data.entities.ModelConfig
import com.openautoglm.agent.data.entities.Task
import com.openautoglm.agent.data.entities.UserPreferences

/**
 * Room database for the OpenAutoGLM Android agent.
 *
 * Contains tables for:
 * - Tasks: User-requested automation tasks
 * - Actions: Individual actions executed within tasks
 * - AppMappings: App package to metadata mappings
 * - UserPreferences: User configuration and settings
 * - ModelConfigs: VLM model configurations
 */
@Database(
    entities = [
        Task::class,
        Action::class,
        AppMapping::class,
        UserPreferences::class,
        ModelConfig::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun actionDao(): ActionDao
    abstract fun appMappingDao(): AppMappingDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun modelConfigDao(): ModelConfigDao

    companion object {
        private const val DATABASE_NAME = "openautoglm_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * @param context Application context
         * @return The database instance
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Close the database instance.
         * Should be called when the application is terminating.
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
