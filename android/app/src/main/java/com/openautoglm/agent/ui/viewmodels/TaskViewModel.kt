package com.openautoglm.agent.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautoglm.agent.agent.AgentConfig
import com.openautoglm.agent.agent.AgentState
import com.openautoglm.agent.agent.PhoneAgent
import com.openautoglm.agent.accessibility.ScreenCaptureManager
import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.AppDatabase
import com.openautoglm.agent.inference.CloudInference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Task screen.
 *
 * Manages the PhoneAgent lifecycle and exposes agent state to the UI.
 */
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = AgentRepository.getInstance(database)
    private val modelClient = CloudInference(application)
    private val screenCapture = ScreenCaptureManager(application)
    private val config = AgentConfig.DEFAULT

    private val agent = PhoneAgent(
        modelClient = modelClient,
        screenCapture = screenCapture,
        repository = repository,
        config = config,
        context = application
    )

    val agentState: StateFlow<AgentState> = agent.agentState

    /**
     * Starts a new task.
     */
    fun startTask(taskDescription: String) {
        viewModelScope.launch {
            try {
                agent.run(taskDescription)
            } catch (e: Exception) {
                // Error handling - state will be updated by agent
            }
        }
    }

    /**
     * Pauses the current task.
     */
    fun pauseTask() {
        viewModelScope.launch {
            agent.pause()
        }
    }

    /**
     * Resumes a paused task.
     */
    fun resumeTask() {
        viewModelScope.launch {
            agent.step()
        }
    }

    /**
     * Cancels the current task.
     */
    fun cancelTask() {
        viewModelScope.launch {
            agent.cancel()
        }
    }

    /**
     * Resets the agent for a new task.
     */
    fun reset() {
        agent.reset()
    }
}
