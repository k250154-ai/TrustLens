package com.example.service

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
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScreenCaptureHelper(private val context: Context) {

    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    companion object {
        private const val TAG = "ScreenCaptureHelper"
        var captureIntentData: Intent? = null
        var captureResultCode: Int = 0
    }

    fun initMediaProjection(resultCode: Int, data: Intent) {
        captureResultCode = resultCode
        captureIntentData = data
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            Log.d(TAG, "MediaProjection successfully initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaProjection: ${e.message}", e)
        }
    }

    fun isReady(): Boolean = mediaProjection != null

    suspend fun captureFrame(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val projection = mediaProjection
        if (projection == null) {
            Log.w(TAG, "MediaProjection is null. Cannot capture frame.")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        val handler = Handler(Looper.getMainLooper())

        val imageListener = object : ImageReader.OnImageAvailableListener {
            private var captured = false

            override fun onImageAvailable(reader: ImageReader?) {
                if (captured) return
                captured = true

                var image: Image? = null
                var bitmap: Bitmap? = null
                try {
                    image = reader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val tempBitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        tempBitmap.copyPixelsFromBuffer(buffer)
                        bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height)
                        tempBitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting image buffer: ${e.message}", e)
                } finally {
                    image?.close()
                    try {
                        virtualDisplay?.release()
                        imageReader.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing virtual display: ${e.message}")
                    }
                    continuation.resume(bitmap)
                }
            }
        }

        imageReader.setOnImageAvailableListener(imageListener, handler)

        try {
            virtualDisplay = projection.createVirtualDisplay(
                "TrustLensCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create virtual display: ${e.message}", e)
            virtualDisplay?.release()
            imageReader.close()
            continuation.resume(null)
        }
    }

    fun release() {
        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaProjection: ${e.message}")
        }
    }
}
