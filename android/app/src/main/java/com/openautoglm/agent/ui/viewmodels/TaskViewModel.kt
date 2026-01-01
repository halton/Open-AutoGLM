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
import com.openautoglm.agent.inference.InferenceRouterImpl
import com.openautoglm.agent.inference.SecureKeyStorage
import com.openautoglm.agent.voice.AudioPermissionState
import com.openautoglm.agent.voice.PermissionHandler
import com.openautoglm.agent.voice.SpeechRecognizerType
import com.openautoglm.agent.voice.UnifiedVoiceInputManager
import com.openautoglm.agent.voice.VoiceInputConfig
import com.openautoglm.agent.voice.VoiceInputManager
import com.openautoglm.agent.voice.VoiceInputManagerImpl
import com.openautoglm.agent.voice.VoiceInputState
import com.openautoglm.agent.service.PauseResumeController
import com.openautoglm.agent.service.PauseResumeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ViewModel for the Task screen.
 *
 * Manages the PhoneAgent lifecycle and exposes agent state to the UI.
 * Also manages voice input for task creation.
 */
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = AgentRepository(
        taskDao = database.taskDao(),
        actionDao = database.actionDao(),
        appMappingDao = database.appMappingDao(),
        userPreferencesDao = database.userPreferencesDao(),
        modelConfigDao = database.modelConfigDao()
    )
    // Use InferenceRouterImpl for smart routing between on-device and cloud
    // with automatic fallback when network is unavailable
    private val modelClient = InferenceRouterImpl(application, repository)
    private val screenCapture = ScreenCaptureManager.getInstance(application)
    private val config = AgentConfig.DEFAULT

    private val agent = PhoneAgent(
        modelClient = modelClient,
        screenCapture = screenCapture,
        repository = repository,
        config = config,
        context = application
    )

    val agentState: StateFlow<AgentState> = agent.agentState

    init {
        // Listen for pause/resume events from the floating overlay
        viewModelScope.launch {
            PauseResumeController.pauseResumeEvents.collect { event ->
                android.util.Log.i("TaskViewModel", "Received pause/resume event: $event")
                handlePauseResumeEvent(event)
            }
        }
    }

    private fun handlePauseResumeEvent(event: PauseResumeEvent) {
        when (event) {
            is PauseResumeEvent.Toggle -> {
                val currentState = agentState.value
                if (currentState is AgentState.Running) {
                    pauseTask()
                } else if (currentState is AgentState.Paused) {
                    resumeTask()
                }
            }
            is PauseResumeEvent.Pause -> pauseTask()
            is PauseResumeEvent.Resume -> resumeTask()
        }
    }

    // Voice input support - uses UnifiedVoiceInputManager to support both Android and Vosk
    private val secureStorage = SecureKeyStorage(application)
    private val speechRecognizerType: SpeechRecognizerType = try {
        SpeechRecognizerType.valueOf(secureStorage.getSpeechRecognizerType())
    } catch (e: IllegalArgumentException) {
        SpeechRecognizerType.ANDROID_BUILTIN
    }

    private val voiceInputManager: VoiceInputManager = UnifiedVoiceInputManager(
        context = application,
        initialType = speechRecognizerType,
        config = VoiceInputConfig.fromAgentLanguage(config.language),
        language = config.language
    )

    /**
     * Current state of voice input.
     */
    val voiceInputState: StateFlow<VoiceInputState> = voiceInputManager.state

    /**
     * Whether speech recognition is available on this device.
     */
    val isVoiceInputAvailable: Boolean
        get() = voiceInputManager.isAvailable

    private val _audioPermissionState = MutableStateFlow(
        PermissionHandler.checkAudioPermissionState(application)
    )

    /**
     * Current state of audio permission.
     */
    val audioPermissionState: StateFlow<AudioPermissionState> = _audioPermissionState.asStateFlow()

    /**
     * Updates the audio permission state (call after permission request result).
     */
    fun updateAudioPermissionState(state: AudioPermissionState) {
        _audioPermissionState.value = state
    }

    /**
     * Refresh the audio permission state from system.
     */
    fun refreshAudioPermissionState() {
        _audioPermissionState.value = PermissionHandler.checkAudioPermissionState(getApplication())
    }

    /**
     * Start voice input recognition.
     */
    fun startVoiceInput() {
        val locale = VoiceInputManagerImpl.getLocaleFromLanguage(config.language)
        voiceInputManager.startListening(locale)
    }

    /**
     * Stop voice input and process final result.
     */
    fun stopVoiceInput() {
        voiceInputManager.stopListening()
    }

    /**
     * Cancel voice input without processing.
     */
    fun cancelVoiceInput() {
        voiceInputManager.cancelListening()
    }

    /**
     * Reset voice input to idle state.
     */
    fun resetVoiceInput() {
        voiceInputManager.cancelListening()
    }

    /**
     * Starts a new task.
     */
    fun startTask(taskDescription: String) {
        android.util.Log.i("TaskViewModel", "========== START TASK CALLED ==========")
        android.util.Log.i("TaskViewModel", "Task description: $taskDescription")

        // Check if screen capture permission is granted
        if (!com.openautoglm.agent.ui.MainActivity.isScreenCapturePermissionGranted()) {
            android.util.Log.e("TaskViewModel", "ERROR: Screen capture permission not granted")
            return
        }

        android.util.Log.i("TaskViewModel", "Screen capture permission OK, starting agent...")

        viewModelScope.launch {
            try {
                android.util.Log.i("TaskViewModel", "Calling agent.run()...")
                agent.run(taskDescription)
                android.util.Log.i("TaskViewModel", "agent.run() completed")
            } catch (e: Exception) {
                // Error handling - state will be updated by agent
                android.util.Log.e("TaskViewModel", "ERROR in agent.run()", e)
                e.printStackTrace()
            }
        }

        android.util.Log.i("TaskViewModel", "startTask() method completed")
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
        agent.resume()
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

    override fun onCleared() {
        super.onCleared()
        voiceInputManager.destroy()
    }
}
