package com.openautoglm.agent.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.openautoglm.agent.agent.AgentState
import com.openautoglm.agent.ui.overlay.OverlayView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Foreground service that manages the floating overlay button during task execution.
 *
 * The overlay provides:
 * - Visual feedback of agent state (idle, thinking, executing, paused, error)
 * - Tap to pause/resume task
 * - Long-press to show status popup with current step info
 *
 * Requires SYSTEM_ALERT_WINDOW permission to display overlay on top of other apps.
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingOverlayService"

        const val ACTION_UPDATE_STATE = "com.openautoglm.agent.UPDATE_STATE"
        const val EXTRA_STATE = "agent_state"
        const val EXTRA_STEP = "current_step"
        const val EXTRA_MAX_STEPS = "max_steps"
        const val EXTRA_DESCRIPTION = "task_description"
        const val EXTRA_MESSAGE = "message"

        /**
         * Start the overlay service.
         */
        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the overlay service.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }

        /**
         * Update the overlay state.
         */
        fun updateState(
            context: Context,
            state: AgentState,
            step: Int = 0,
            maxSteps: Int = 50,
            description: String? = null,
            message: String? = null
        ) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_STATE, state.javaClass.simpleName)
                putExtra(EXTRA_STEP, step)
                putExtra(EXTRA_MAX_STEPS, maxSteps)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_MESSAGE, message)
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null
    private var isOverlayAdded = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingOverlayService created")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        // Start as foreground service using ScreenCaptureService's notification
        // This ensures we stay alive while the agent is running
        startForeground(
            ScreenCaptureService.NOTIFICATION_ID,
            createNotification()
        )

        when (intent?.action) {
            ACTION_UPDATE_STATE -> {
                handleStateUpdate(intent)
            }
            else -> {
                // Initial start - show the overlay
                showOverlay()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "FloatingOverlayService destroyed")
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (isOverlayAdded) {
            Log.d(TAG, "Overlay already visible")
            return
        }

        if (!canDrawOverlays()) {
            Log.e(TAG, "No permission to draw overlays")
            return
        }

        try {
            overlayView = OverlayView(this).apply {
                onPauseResumeClick = { handlePauseResumeClick() }
                onStatusLongPress = { handleStatusLongPress() }
            }

            val params = createLayoutParams()
            windowManager?.addView(overlayView, params)
            isOverlayAdded = true
            Log.i(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun hideOverlay() {
        if (!isOverlayAdded) return

        try {
            overlayView?.let { windowManager?.removeView(it) }
            overlayView = null
            isOverlayAdded = false
            Log.i(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        }
    }

    private fun handleStateUpdate(intent: Intent) {
        val stateName = intent.getStringExtra(EXTRA_STATE) ?: return
        val step = intent.getIntExtra(EXTRA_STEP, 0)
        val maxSteps = intent.getIntExtra(EXTRA_MAX_STEPS, 50)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val message = intent.getStringExtra(EXTRA_MESSAGE)

        Log.d(TAG, "State update: $stateName, step=$step/$maxSteps")

        // Ensure overlay is visible
        if (!isOverlayAdded) {
            showOverlay()
        }

        // Update overlay state
        overlayView?.updateState(
            stateName = stateName,
            step = step,
            maxSteps = maxSteps,
            description = description,
            message = message
        )

        // Hide overlay when idle
        if (stateName == "Idle") {
            hideOverlay()
        }
    }

    private fun handlePauseResumeClick() {
        Log.d(TAG, "Pause/Resume clicked")
        // Use PauseResumeController to notify observers
        GlobalScope.launch {
            PauseResumeController.togglePauseResume()
        }
    }

    private fun handleStatusLongPress() {
        Log.d(TAG, "Status long press")
        overlayView?.showStatusPopup()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun createNotification(): android.app.Notification {
        // Reuse the notification channel from ScreenCaptureService
        return androidx.core.app.NotificationCompat.Builder(this, ScreenCaptureService.CHANNEL_ID)
            .setContentTitle("AutoGLM Agent Active")
            .setContentText("Agent is running")
            .setSmallIcon(com.openautoglm.agent.R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
