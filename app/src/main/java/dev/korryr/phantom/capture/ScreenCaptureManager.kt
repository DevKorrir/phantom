package dev.korryr.phantom.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import timber.log.Timber

class ScreenCaptureManager(
    private val mediaProjection: MediaProjection,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val screenDensity: Int
) {
    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "PhantomCapture"
    }

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var latestImage: Image? = null

    init {
        Timber.d("Initializing ScreenCaptureManager: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        ).also { reader ->
            reader.setOnImageAvailableListener({ ir ->
                latestImage?.close()
                latestImage = ir.acquireLatestImage()
            }, null)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )
            Timber.d("VirtualDisplay created successfully")
        }
    }

    fun captureFrame(): Bitmap? {
        return try {
            val image = latestImage ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val fullBitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)

            // Crop to actual screen size then scale down 50%
            val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, screenWidth, screenHeight)
            val scaled = Bitmap.createScaledBitmap(
                cropped,
                screenWidth / 2,
                screenHeight / 2,
                true
            )

            if (cropped !== fullBitmap) fullBitmap.recycle()
            if (scaled !== cropped) cropped.recycle()

            scaled
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture frame")
            null
        }
    }

    fun release() {
        Timber.d("Releasing ScreenCaptureManager resources")
        latestImage?.close()
        latestImage = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection.stop()
        Timber.d("ScreenCaptureManager released")
    }
}
