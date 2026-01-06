package com.openautoglm.agent.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Controller for pause/resume functionality across the app.
 *
 * This singleton allows the FloatingOverlayService to trigger pause/resume
 * events that can be observed by TaskViewModel or other components.
 */
object PauseResumeController {

    private val _pauseResumeEvents = MutableSharedFlow<PauseResumeEvent>(replay = 0)
    val pauseResumeEvents: SharedFlow<PauseResumeEvent> = _pauseResumeEvents.asSharedFlow()

    /**
     * Toggles pause/resume state.
     */
    suspend fun togglePauseResume() {
        _pauseResumeEvents.emit(PauseResumeEvent.Toggle)
    }

    /**
     * Requests pause.
     */
    suspend fun requestPause() {
        _pauseResumeEvents.emit(PauseResumeEvent.Pause)
    }

    /**
     * Requests resume.
     */
    suspend fun requestResume() {
        _pauseResumeEvents.emit(PauseResumeEvent.Resume)
    }
}

sealed class PauseResumeEvent {
    object Toggle : PauseResumeEvent()
    object Pause : PauseResumeEvent()
    object Resume : PauseResumeEvent()
}
