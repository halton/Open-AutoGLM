package com.openautoglm.agent.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautoglm.agent.agent.TaskRedoHandler
import com.openautoglm.agent.data.AppDatabase
import com.openautoglm.agent.data.entities.Task
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the History screen.
 *
 * Manages task history data from the database and exposes it to the UI.
 * Supports task redo functionality to re-execute previous tasks.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val taskDao = database.taskDao()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    /**
     * Event emitted when user requests to redo a task.
     * Contains the task description to be executed.
     */
    private val _redoTaskEvent = MutableSharedFlow<TaskRedoHandler.RedoTaskInfo>()
    val redoTaskEvent: SharedFlow<TaskRedoHandler.RedoTaskInfo> = _redoTaskEvent.asSharedFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            taskDao.getAll().collect { taskList ->
                _tasks.value = taskList
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskDao.delete(task)
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            taskDao.deleteAll()
        }
    }

    /**
     * Initiates a redo of the given task.
     *
     * Emits a redo event that the UI can observe to navigate
     * to the task screen with the task description pre-filled.
     *
     * @param task The task to redo
     */
    fun redoTask(task: Task) {
        if (TaskRedoHandler.canRedo(task)) {
            val redoInfo = TaskRedoHandler.prepareForRedo(task)
            viewModelScope.launch {
                _redoTaskEvent.emit(redoInfo)
            }
        }
    }

    /**
     * Checks if a task can be redone.
     *
     * @param task The task to check
     * @return true if the task can be redone
     */
    fun canRedoTask(task: Task): Boolean {
        return TaskRedoHandler.canRedo(task)
    }
}
