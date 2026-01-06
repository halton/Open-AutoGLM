package com.openautoglm.agent.voice

/**
 * State of RECORD_AUDIO permission for voice input.
 * Used to determine UI state and user flow.
 */
enum class AudioPermissionState {
    /**
     * Permission has been granted.
     * Voice input is fully available.
     */
    GRANTED,

    /**
     * Permission has not been requested yet.
     * Will be requested when user taps mic.
     */
    NOT_REQUESTED,

    /**
     * Permission was denied once.
     * Should show rationale before requesting again.
     */
    DENIED_SHOW_RATIONALE,

    /**
     * Permission permanently denied ("Don't ask again").
     * Must direct user to app settings.
     */
    PERMANENTLY_DENIED,

    /**
     * Permission state unknown (error checking).
     * Treat as NOT_REQUESTED.
     */
    UNKNOWN
}
