package com.openautoglm.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.openautoglm.agent.data.AppDatabase
import com.openautoglm.agent.data.TaskRepository
import com.openautoglm.agent.knowledge.AppRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for AutoGLM Android Agent.
 *
 * Initializes core components:
 * - Room database
 * - App knowledge base registry
 * - Notification channels
 */
class AutoGLMApplication : Application() {

    // Application-scoped coroutine scope for background initialization
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lazy-initialized database instance
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    // Lazy-initialized repository
    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao(), database.actionDao())
    }

    // Lazy-initialized app registry
    val appRegistry: AppRegistry by lazy {
        AppRegistry(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Create notification channels
        createNotificationChannels()

        // Initialize app registry in background
        applicationScope.launch {
            appRegistry.loadMappings()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Task execution channel
            val taskChannel = NotificationChannel(
                CHANNEL_TASK_EXECUTION,
                "Task Execution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while AutoGLM executes tasks"
            }
            notificationManager.createNotificationChannel(taskChannel)

            // Task completion channel
            val completionChannel = NotificationChannel(
                CHANNEL_TASK_COMPLETION,
                "Task Completion",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when tasks are completed"
            }
            notificationManager.createNotificationChannel(completionChannel)

            // Confirmation required channel
            val confirmChannel = NotificationChannel(
                CHANNEL_CONFIRMATION_REQUIRED,
                "Confirmation Required",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when user confirmation is needed"
            }
            notificationManager.createNotificationChannel(confirmChannel)
        }
    }

    companion object {
        const val CHANNEL_TASK_EXECUTION = "task_execution"
        const val CHANNEL_TASK_COMPLETION = "task_completion"
        const val CHANNEL_CONFIRMATION_REQUIRED = "confirmation_required"

        @Volatile
        private var instance: AutoGLMApplication? = null

        fun getInstance(): AutoGLMApplication {
            return instance ?: throw IllegalStateException(
                "AutoGLMApplication not initialized. Make sure the application is running."
            )
        }

        fun getContext(): Context = getInstance().applicationContext
    }
}
