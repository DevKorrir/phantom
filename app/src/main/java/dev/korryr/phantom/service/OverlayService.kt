package dev.korryr.phantom.service

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.korryr.phantom.PhantomApplication
import dev.korryr.phantom.ai.GroqRepository
import dev.korryr.phantom.capture.ScreenCaptureManager
import dev.korryr.phantom.ui.overlay.AnswerOverlay
import dev.korryr.phantom.viewmodel.OverlayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "phantom_overlay"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var savedStateRegistryController: SavedStateRegistryController

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var viewModel: OverlayViewModel? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("OverlayService onCreate")
        lifecycleRegistry = LifecycleRegistry(this)
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("OverlayService onStartCommand")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Timber.e("Invalid result code ($resultCode) or null result data — stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        // Get screen metrics
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        Timber.d("Screen metrics: ${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi")

        // Create MediaProjection
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection: MediaProjection? = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Timber.e("MediaProjection is null — stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        Timber.d("MediaProjection created successfully")

        screenCaptureManager = ScreenCaptureManager(
            mediaProjection = projection,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            screenDensity = metrics.densityDpi
        )

        // Use Hilt's singleton OkHttpClient via the Application's DI container
        val app = application as PhantomApplication
        val httpClient = dagger.hilt.EntryPoints.get(app, OverlayServiceEntryPoint::class.java).okHttpClient()
        val groqRepo = GroqRepository(httpClient)
        Timber.d("GroqRepository initialized with Hilt OkHttpClient")

        viewModel = OverlayViewModel(groqRepo).also { vm ->
            vm.setScreenCaptureManager(screenCaptureManager!!)
            vm.startPreFetch(serviceScope)   // Start background OCR immediately
        }

        // Set up overlay
        windowManager = wm
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE: overlay never steals input focus → keyboard opens instantly
            // FLAG_NOT_TOUCH_MODAL: touches outside the overlay pass through to the app below
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                var offsetX by remember { mutableStateOf(layoutParams.x) }
                var offsetY by remember { mutableStateOf(layoutParams.y) }

                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x.toInt()
                            offsetY += dragAmount.y.toInt()
                            layoutParams.x = offsetX
                            layoutParams.y = offsetY
                            windowManager?.updateViewLayout(overlayView, layoutParams)
                        }
                    }
                ) {
                    AnswerOverlay(
                        stateFlow = viewModel!!.state,
                        onScanClick = {
                            Timber.i("Scan button clicked")
                            viewModel?.scanOnce(serviceScope)
                        },
                        onStopClick = {
                            Timber.i("Stop button clicked — stopping service")
                            this@OverlayService.stopSelf()
                        }
                    )
                }
            }

        }

        windowManager?.addView(overlayView, layoutParams)
        Timber.i("Overlay view added to WindowManager")

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Timber.i("OverlayService onDestroy — cleaning up")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModel?.stop()
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        serviceScope.cancel()
        Timber.i("OverlayService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Phantom Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Screen capture overlay service"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Timber.d("Notification channel created")
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phantom")
            .setContentText("Overlay active")
            .setSmallIcon(R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }
}

/**
 * Hilt EntryPoint to access the singleton OkHttpClient from a non-injected Service.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface OverlayServiceEntryPoint {
    fun okHttpClient(): OkHttpClient
}
