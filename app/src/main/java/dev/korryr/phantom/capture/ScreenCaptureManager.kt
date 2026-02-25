package dev.korryr.phantom.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

class ScreenCaptureManager(
    private val mediaProjection: MediaProjection,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val screenDensity: Int
) {
    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "PhantomCapture"
        private const val FRAME_TIMEOUT_MS     = 2000L
    }

    // Dedicated HandlerThread — ImageReader callbacks never run on main thread
    private val handlerThread = HandlerThread("PhantomImageReader").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // ── Shared state between HandlerThread and coroutines ──────────────────────
    // We store a BITMAP (already decoded), never a raw Image.
    // This avoids the "maxImages exceeded" crash: Images are always .close()d
    // inside the callback before anything else can acquire them.
    @Volatile private var latestBitmap: Bitmap? = null
    @Volatile private var pendingListener: ((Bitmap?) -> Unit)? = null

    init {
        Timber.d("ScreenCaptureManager init: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")

        // Only need 2 buffer slots — images are decoded and closed immediately in the callback
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        ).also { reader ->
            reader.setOnImageAvailableListener({ ir ->
                // Acquire, decode, and ALWAYS close — all within this callback.
                // Raw Image objects NEVER leave this block.
                val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                val bitmap: Bitmap? = try {
                    decodeImageToBitmap(img)
                } finally {
                    img.close()   // ← guaranteed close, prevents "maxImages exceeded" crash
                }

                val listener = pendingListener
                if (listener != null) {
                    // Someone is waiting for a fresh frame — deliver directly
                    pendingListener = null
                    listener(bitmap)
                } else {
                    // No active scan — update the cached latest bitmap
                    latestBitmap?.recycle()
                    latestBitmap = bitmap
                }
            }, handler)

            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Timber.d("MediaProjection stopped")
                }
            }, handler)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )
            Timber.d("VirtualDisplay created")
        }
    }

    /**
     * Instant grab of the most recently decoded bitmap — NO waiting.
     * Returns a COPY so the caller can safely .recycle() it without destroying the cache.
     * Returns null only on the very first scan before any frame has arrived.
     */
    fun captureFrame(): Bitmap? = latestBitmap?.copy(Bitmap.Config.ARGB_8888, false)

    /**
     * Waits for the NEXT frame from the VirtualDisplay and returns it as a Bitmap.
     * Only needed as a fallback when [captureFrame] returns null (cold start).
     * Times out after [FRAME_TIMEOUT_MS] and falls back to the cached latest bitmap.
     */
    suspend fun captureNextFrame(): Bitmap? {
        return withTimeoutOrNull(FRAME_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                pendingListener = { bmp ->
                    if (cont.isActive) cont.resume(bmp)
                }
                cont.invokeOnCancellation { pendingListener = null }
            }
        } ?: run {
            Timber.w("captureNextFrame timed out — using cached bitmap")
            latestBitmap
        }
    }

    /**
     * Decodes a raw RGBA_8888 Image into a scaled-down ARGB_8888 Bitmap.
     *
     * CORRECTNESS NOTE:
     *   ImageReader format = PixelFormat.RGBA_8888 → 4 bytes per pixel in buffer.
     *   bitmap.copyPixelsFromBuffer() requires the bitmap config to match the buffer's
     *   bytes-per-pixel. Using Bitmap.Config.ARGB_8888 (4 bytes/px) is the only correct
     *   choice. RGB_565 (2 bytes/px) would silently decode only the top ~50% of the screen.
     *
     * Scaled to 50% — sufficient for ML Kit OCR, halves memory and OCR processing time.
     * filterBitmap=false skips bilinear smoothing (irrelevant for OCR, saves ~5ms).
     */
    private fun decodeImageToBitmap(image: android.media.Image): Bitmap? {
        var raw: Bitmap? = null
        var cropped: Bitmap? = null
        return try {
            val plane       = image.planes[0]
            val buffer      = plane.buffer
            val pixelStride = plane.pixelStride        // 4 for RGBA_8888
            val rowStride   = plane.rowStride
            val rowPadding  = rowStride - pixelStride * screenWidth

            // Full-row-width bitmap required by copyPixelsFromBuffer
            val rawWidth = screenWidth + rowPadding / pixelStride
            raw = Bitmap.createBitmap(rawWidth, screenHeight, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)

            val targetW = screenWidth  / 2
            val targetH = screenHeight / 2

            if (rowPadding == 0) {
                Bitmap.createScaledBitmap(raw, targetW, targetH, false)
            } else {
                cropped = Bitmap.createBitmap(raw, 0, 0, screenWidth, screenHeight)
                Bitmap.createScaledBitmap(cropped, targetW, targetH, false)
            }
        } catch (e: Exception) {
            Timber.e(e, "decodeImageToBitmap failed")
            null
        } finally {
            raw?.recycle()
            cropped?.recycle()
        }
    }

    fun release() {
        Timber.d("Releasing ScreenCaptureManager")
        pendingListener = null
        latestBitmap?.recycle()
        latestBitmap = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection.stop()
        handlerThread.quitSafely()
        Timber.d("ScreenCaptureManager released")
    }
}
