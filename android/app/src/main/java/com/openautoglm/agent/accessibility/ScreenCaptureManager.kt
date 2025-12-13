package com.openautoglm.agent.accessibility

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ScreenCaptureManager handles screen capture using MediaProjection API.
 *
 * This singleton class provides high-quality screenshot capabilities for the VLM Android agent.
 * It uses MediaProjection with VirtualDisplay and ImageReader to capture screen content
 * and convert it to base64 format suitable for VLM processing.
 *
 * Usage:
 * 1. Request screen capture permission via MediaProjectionManager.createScreenCaptureIntent()
 * 2. Call startCapture() with the result code and data from onActivityResult
 * 3. Use captureScreen() to take screenshots
 * 4. Call stopCapture() when done to release resources
 */
class ScreenCaptureManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        private const val VIRTUAL_DISPLAY_NAME = "OpenAutoGLM_ScreenCapture"
        private const val IMAGE_READER_MAX_IMAGES = 2

        @Volatile
        private var instance: ScreenCaptureManager? = null

        /**
         * Get the singleton instance of ScreenCaptureManager.
         *
         * @param context Application context (will use applicationContext internally)
         * @return The singleton instance
         */
        fun getInstance(context: Context): ScreenCaptureManager {
            return instance ?: synchronized(this) {
                instance ?: ScreenCaptureManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * Release the singleton instance and all associated resources.
         * Call this when the application is terminating or when screen capture is no longer needed.
         */
        fun release() {
            synchronized(this) {
                instance?.stopCapture()
                instance = null
            }
        }
    }

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    private val isCapturing = AtomicBoolean(false)
    private val captureInProgress = AtomicBoolean(false)

    @Volatile
    private var latestBitmap: Bitmap? = null
    private val bitmapLock = Any()

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped")
            cleanupResources()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.d(TAG, "Captured content resized: ${width}x${height}")
            // Recreate virtual display with new dimensions if needed
            if (width > 0 && height > 0 && (width != screenWidth || height != screenHeight)) {
                recreateVirtualDisplay(width, height)
            }
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            Log.d(TAG, "Captured content visibility changed: $isVisible")
        }
    }

    init {
        initializeDisplayMetrics()
    }

    private fun initializeDisplayMetrics() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi

        Log.d(TAG, "Display metrics: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")
    }

    /**
     * Initialize MediaProjection and start screen capture.
     *
     * This method should be called with the result from MediaProjectionManager.createScreenCaptureIntent()
     * after the user grants permission.
     *
     * @param resultCode The result code from onActivityResult (should be Activity.RESULT_OK)
     * @param data The Intent data from onActivityResult containing the permission token
     * @return true if capture was started successfully, false otherwise
     */
    fun startCapture(resultCode: Int, data: Intent): Boolean {
        if (isCapturing.get()) {
            Log.w(TAG, "Capture already in progress")
            return true
        }

        return try {
            // Initialize handler thread for ImageReader callbacks
            handlerThread = HandlerThread("ScreenCaptureThread").apply {
                start()
            }
            handler = Handler(handlerThread!!.looper)

            // Get MediaProjection from the permission result
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to get MediaProjection")
                cleanupResources()
                return false
            }

            // Register callback for MediaProjection lifecycle events
            mediaProjection?.registerCallback(mediaProjectionCallback, handler)

            // Create ImageReader with RGBA_8888 format for high-quality capture
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                IMAGE_READER_MAX_IMAGES
            ).apply {
                setOnImageAvailableListener({ reader ->
                    processImage(reader)
                }, handler)
            }

            // Create VirtualDisplay to capture screen content
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Log.d(TAG, "VirtualDisplay paused")
                    }

                    override fun onResumed() {
                        Log.d(TAG, "VirtualDisplay resumed")
                    }

                    override fun onStopped() {
                        Log.d(TAG, "VirtualDisplay stopped")
                    }
                },
                handler
            )

            if (virtualDisplay == null) {
                Log.e(TAG, "Failed to create VirtualDisplay")
                cleanupResources()
                return false
            }

            isCapturing.set(true)
            Log.i(TAG, "Screen capture started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture", e)
            cleanupResources()
            false
        }
    }

    /**
     * Capture the current screen and return as a Bitmap.
     *
     * This method captures the current screen content. The capture is synchronous
     * but uses the latest available frame from the ImageReader.
     *
     * @return Bitmap of the current screen, or null if capture failed
     */
    fun captureScreen(): Bitmap? {
        if (!isCapturing.get()) {
            Log.w(TAG, "Screen capture not started")
            return null
        }

        if (captureInProgress.getAndSet(true)) {
            Log.w(TAG, "Capture already in progress, waiting...")
            // Wait for current capture to complete
            Thread.sleep(50)
        }

        try {
            // Allow time for new frame to be available
            Thread.sleep(100)

            synchronized(bitmapLock) {
                val bitmap = latestBitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    // Return a copy to prevent issues with recycling
                    return bitmap.copy(Bitmap.Config.ARGB_8888, false)
                }
            }

            Log.w(TAG, "No bitmap available")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen", e)
            return null
        } finally {
            captureInProgress.set(false)
        }
    }

    /**
     * Stop screen capture and release all resources.
     *
     * This method should be called when screen capture is no longer needed.
     * It properly releases MediaProjection, VirtualDisplay, and ImageReader resources.
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping screen capture")

        if (!isCapturing.getAndSet(false)) {
            Log.d(TAG, "Capture was not running")
            return
        }

        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection", e)
        }

        cleanupResources()
        Log.i(TAG, "Screen capture stopped")
    }

    /**
     * Convert a Bitmap to base64 encoded string for VLM processing.
     *
     * The bitmap is encoded as PNG format to preserve quality.
     *
     * @param bitmap The bitmap to convert
     * @param quality Compression quality (0-100), default is 90
     * @return Base64 encoded string of the bitmap
     */
    fun toBase64(bitmap: Bitmap, quality: Int = 90): String {
        return ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    /**
     * Check if screen capture is currently active.
     *
     * @return true if capture is active, false otherwise
     */
    fun isCapturing(): Boolean = isCapturing.get()

    /**
     * Get the current screen dimensions.
     *
     * @return Pair of (width, height) in pixels
     */
    fun getScreenDimensions(): Pair<Int, Int> = Pair(screenWidth, screenHeight)

    private fun processImage(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) {
                return
            }

            val planes = image.planes
            if (planes.isEmpty()) {
                return
            }

            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            // Create bitmap with proper dimensions accounting for row padding
            val bitmapWidth = screenWidth + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to actual screen size if there's padding
            val croppedBitmap = if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight).also {
                    bitmap.recycle()
                }
            } else {
                bitmap
            }

            synchronized(bitmapLock) {
                latestBitmap?.recycle()
                latestBitmap = croppedBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        } finally {
            image?.close()
        }
    }

    private fun recreateVirtualDisplay(newWidth: Int, newHeight: Int) {
        if (!isCapturing.get()) return

        try {
            virtualDisplay?.release()
            imageReader?.close()

            screenWidth = newWidth
            screenHeight = newHeight

            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                IMAGE_READER_MAX_IMAGES
            ).apply {
                setOnImageAvailableListener({ reader ->
                    processImage(reader)
                }, handler)
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )

            Log.d(TAG, "VirtualDisplay recreated with size: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating VirtualDisplay", e)
        }
    }

    private fun cleanupResources() {
        synchronized(bitmapLock) {
            latestBitmap?.recycle()
            latestBitmap = null
        }

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VirtualDisplay", e)
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ImageReader", e)
        }

        mediaProjection = null

        try {
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping handler thread", e)
        }

        isCapturing.set(false)
        captureInProgress.set(false)
    }
}
