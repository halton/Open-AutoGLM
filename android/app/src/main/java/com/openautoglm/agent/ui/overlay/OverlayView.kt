package com.openautoglm.agent.ui.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.min

/**
 * Custom view for the floating overlay that displays agent state.
 *
 * Features:
 * - Visual state indication with colors and animations
 * - Tap to pause/resume
 * - Long-press for status popup
 * - Smooth state transitions
 */
@SuppressLint("ViewConstructor")
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val SIZE_DP = 56
        private const val ICON_PADDING_DP = 12
        private const val LONG_PRESS_DELAY = 500L

        // State colors
        private const val COLOR_IDLE = 0xFF9E9E9E.toInt()       // Gray
        private const val COLOR_THINKING = 0xFF2196F3.toInt()   // Blue
        private const val COLOR_EXECUTING = 0xFF4CAF50.toInt()  // Green
        private const val COLOR_PAUSED = 0xFFFF9800.toInt()     // Orange
        private const val COLOR_ERROR = 0xFFF44336.toInt()      // Red
        private const val COLOR_FINISHED = 0xFF8BC34A.toInt()   // Light Green
    }

    // State
    private var currentState: String = "Idle"
    private var currentStep: Int = 0
    private var maxSteps: Int = 50
    private var taskDescription: String? = null
    private var stateMessage: String? = null

    // Callbacks
    var onPauseResumeClick: (() -> Unit)? = null
    var onStatusLongPress: (() -> Unit)? = null

    // Drawing
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val iconRect = RectF()
    private val progressRect = RectF()

    // Animation
    private var pulseAnimator: ObjectAnimator? = null
    private var rotationAnimator: ValueAnimator? = null
    private var currentRotation = 0f
    private var pulseScale = 1f

    // Touch handling
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false

    // Popup
    private var statusPopup: PopupWindow? = null

    init {
        val sizePx = (SIZE_DP * resources.displayMetrics.density).toInt()
        minimumWidth = sizePx
        minimumHeight = sizePx

        // Set text size based on density
        textPaint.textSize = 20f * resources.displayMetrics.density

        // Start idle state
        updateVisualState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sizePx = (SIZE_DP * resources.displayMetrics.density).toInt()
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 4f * resources.displayMetrics.density

        // Save canvas state for pulse animation
        canvas.save()
        canvas.scale(pulseScale, pulseScale, centerX, centerY)

        // Draw background circle
        paint.style = Paint.Style.FILL
        paint.color = getStateColor()
        paint.alpha = 220
        canvas.drawCircle(centerX, centerY, radius, paint)

        // Draw progress arc for running/thinking states
        if (currentState == "Running" || currentState == "Thinking") {
            val padding = 6f * resources.displayMetrics.density
            progressRect.set(
                padding,
                padding,
                width - padding,
                height - padding
            )
            progressPaint.color = Color.WHITE
            progressPaint.alpha = 180

            // Draw progress based on current step
            val sweepAngle = (currentStep.toFloat() / maxSteps) * 360f
            canvas.drawArc(progressRect, -90f, sweepAngle, false, progressPaint)

            // Draw spinning indicator for thinking
            if (currentState == "Thinking") {
                canvas.save()
                canvas.rotate(currentRotation, centerX, centerY)
                progressPaint.alpha = 255
                canvas.drawArc(progressRect, -90f, 60f, false, progressPaint)
                canvas.restore()
            }
        }

        // Draw state icon/letter
        val iconText = getStateIcon()
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(iconText, centerX, textY, textPaint)

        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isLongPressTriggered = false
                longPressRunnable = Runnable {
                    isLongPressTriggered = true
                    onStatusLongPress?.invoke()
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
                handler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                if (!isLongPressTriggered) {
                    // Handle tap - pause/resume
                    if (currentState == "Running" || currentState == "Paused") {
                        onPauseResumeClick?.invoke()
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Update the overlay state.
     */
    fun updateState(
        stateName: String,
        step: Int,
        maxSteps: Int,
        description: String?,
        message: String?
    ) {
        val stateChanged = currentState != stateName
        currentState = stateName
        currentStep = step
        this.maxSteps = maxSteps
        taskDescription = description
        stateMessage = message

        if (stateChanged) {
            updateVisualState()
        }

        invalidate()
    }

    /**
     * Show status popup with current task info.
     */
    fun showStatusPopup() {
        dismissStatusPopup()

        val popupView = TextView(context).apply {
            text = buildStatusText()
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD000000.toInt())
            setPadding(32, 24, 32, 24)
            textSize = 14f
        }

        statusPopup = PopupWindow(
            popupView,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = 8f * resources.displayMetrics.density
        }

        // Show to the left of the overlay
        val xOffset = -(popupView.paint.measureText(buildStatusText()) + 80).toInt()
        statusPopup?.showAsDropDown(this, xOffset, -height / 2, Gravity.END)

        // Auto-dismiss after 3 seconds
        handler.postDelayed({ dismissStatusPopup() }, 3000)
    }

    private fun dismissStatusPopup() {
        statusPopup?.dismiss()
        statusPopup = null
    }

    private fun buildStatusText(): String {
        return buildString {
            append("State: $currentState\n")
            append("Step: $currentStep / $maxSteps\n")
            taskDescription?.let { append("Task: ${it.take(40)}...") }
        }
    }

    private fun updateVisualState() {
        stopAnimations()

        when (currentState) {
            "Running" -> {
                startRotationAnimation()
            }
            "Thinking" -> {
                startRotationAnimation()
                startPulseAnimation()
            }
            "Paused" -> {
                startPulseAnimation()
            }
            "Error" -> {
                startPulseAnimation()
            }
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofFloat(this, "pulseScale", 1f, 1.1f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    private fun startRotationAnimation() {
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                currentRotation = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimations() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        rotationAnimator?.cancel()
        rotationAnimator = null
        pulseScale = 1f
        currentRotation = 0f
    }

    private fun getStateColor(): Int {
        return when (currentState) {
            "Idle" -> COLOR_IDLE
            "Running" -> COLOR_EXECUTING
            "Thinking" -> COLOR_THINKING
            "Paused" -> COLOR_PAUSED
            "Error" -> COLOR_ERROR
            "Finished" -> COLOR_FINISHED
            else -> COLOR_IDLE
        }
    }

    private fun getStateIcon(): String {
        return when (currentState) {
            "Idle" -> "A"
            "Running" -> "▶"
            "Thinking" -> "◐"
            "Paused" -> "⏸"
            "Error" -> "!"
            "Finished" -> "✓"
            else -> "A"
        }
    }

    @Suppress("unused")
    fun setPulseScale(scale: Float) {
        pulseScale = scale
    }

    @Suppress("unused")
    fun getPulseScale(): Float = pulseScale

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
        dismissStatusPopup()
        longPressRunnable?.let { handler.removeCallbacks(it) }
    }
}
