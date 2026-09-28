package app.gestenswipe.engine

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.gestenswipe.GestenSwipeApp
import app.gestenswipe.R
import app.gestenswipe.access.SwipeAccessibilityService
import app.gestenswipe.gesture.FaceFrame
import app.gestenswipe.gesture.GestureDetector
import app.gestenswipe.gesture.GestureEvent
import app.gestenswipe.gesture.SwipeAction
import app.gestenswipe.settings.AppSettings
import app.gestenswipe.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground-Service mit Frontkamera: CameraX liefert kleine Bilder (ca. 320×240),
 * MediaPipe wertet sie aus, der [GestureDetector] macht daraus Gesten und der
 * [SwipeAccessibilityService] führt die zugeordnete Aktion aus.
 *
 * Die Kamera läuft nur, wenn eine Ziel-App im Vordergrund ist, der Bildschirm an ist
 * und der Nutzer nicht pausiert hat.
 */
class CameraGestureService : LifecycleService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var analysisExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var tracker: FaceTracker? = null
    private val detector = GestureDetector()
    private val detectorLock = Any()

    @Volatile private var settings = AppSettings()
    @Volatile private var frameIntervalMs = 66L
    private var lastSubmittedTs = 0L
    private var fpsWindowStart = 0L
    private var fpsCount = 0

    private val screenOn = MutableStateFlow(true)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn.value = false
                Intent.ACTION_SCREEN_ON -> screenOn.value = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        analysisExecutor = Executors.newSingleThreadExecutor()
        screenOn.value = getSystemService(PowerManager::class.java).isInteractive
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val repo = (application as GestenSwipeApp).settings

        lifecycleScope.launch {
            repo.settings.collect { s ->
                settings = s
                frameIntervalMs = 1000L / s.fps.coerceIn(5, 30)
                synchronized(detectorLock) { detector.config = s.detectorConfig() }
            }
        }
        lifecycleScope.launch {
            combine(
                repo.settings,
                EngineState.foregroundPackage,
                screenOn,
                EngineState.paused,
            ) { s, fg, on, paused ->
                on && !paused && s.isTarget(fg, packageName)
            }.distinctUntilChanged().collect { active ->
                if (active) startCamera() else stopCamera()
            }
        }
        lifecycleScope.launch {
            EngineState.paused.collect { updateNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                EngineState.paused.value = !EngineState.paused.value
                return START_NOT_STICKY
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            EngineState.error.value = "Kamera-Berechtigung fehlt."
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        EngineState.running.value = true
        EngineState.error.value = null
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCamera()
        unregisterReceiver(screenReceiver)
        analysisExecutor.execute {
            tracker?.close()
            tracker = null
        }
        analysisExecutor.shutdown()
        EngineState.running.value = false
        EngineState.paused.value = false
        EngineState.faceVisible.value = false
        EngineState.fps.value = 0f
        super.onDestroy()
    }

    // --- Kamera -----------------------------------------------------------------------------

    private fun startCamera() {
        if (cameraProvider != null) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrElse {
                reportError("Kamera nicht verfügbar: ${it.message}")
                return@addListener
            }
            val resolution = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(analysisExecutor, ::analyze)
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                cameraProvider = provider
                EngineState.cameraActive.value = true
                synchronized(detectorLock) { detector.reset() }
            } catch (e: Exception) {
                reportError("Frontkamera konnte nicht gestartet werden: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        EngineState.cameraActive.value = false
        EngineState.faceVisible.value = false
        EngineState.fps.value = 0f
    }

    /** Läuft auf [analysisExecutor]. */
    private fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastSubmittedTs < frameIntervalMs) return
            lastSubmittedTs = now
            val t = tracker ?: createTracker() ?: return
            t.detect(rotate(image.toBitmap(), image.imageInfo.rotationDegrees), now)
        } catch (e: Exception) {
            Log.w(TAG, "Frame verworfen", e)
        } finally {
            image.close()
        }
    }

    private fun createTracker(): FaceTracker? = runCatching {
        FaceTracker(this, ::onTrackerResult) { reportError(it) }
    }.onSuccess { tracker = it }
        .onFailure { reportError("Gesichtserkennung konnte nicht geladen werden: ${it.message}") }
        .getOrNull()

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    // --- Auswertung -------------------------------------------------------------------------

    /** Läuft auf dem MediaPipe-Ergebnis-Thread. */
    private fun onTrackerResult(frame: FaceFrame?, timestampMs: Long) {
        countFps(timestampMs)
        if (frame == null) {
            EngineState.faceVisible.value = false
            synchronized(detectorLock) { detector.onNoFace() }
            return
        }
        EngineState.faceVisible.value = true
        EngineState.lastFrame.value = frame
        EngineState.frames.tryEmit(frame)

        val gesture = synchronized(detectorLock) {
            val g = detector.process(frame)
            EngineState.headDown.value = detector.currentDown
            EngineState.headLeft.value = detector.currentLeft
            g
        } ?: return

        val action = settings.actionFor(gesture)
        val dispatch = action != SwipeAction.NONE && !EngineState.suppressActions.value
        EngineState.lastGesture.value = GestureEvent(gesture, action, timestampMs, dispatch)
        if (dispatch) {
            mainHandler.post { SwipeAccessibilityService.instance?.perform(action) }
        }
    }

    private fun countFps(ts: Long) {
        if (fpsWindowStart == 0L) fpsWindowStart = ts
        fpsCount++
        val elapsed = ts - fpsWindowStart
        if (elapsed >= 1000) {
            EngineState.fps.value = fpsCount * 1000f / elapsed
            fpsCount = 0
            fpsWindowStart = ts
        }
    }

    private fun reportError(message: String) {
        Log.e(TAG, message)
        EngineState.error.value = message
    }

    // --- Benachrichtigung -------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val paused = EngineState.paused.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, GestenSwipeApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (paused) R.string.notif_paused else R.string.notif_running))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(if (paused) R.string.action_resume else R.string.action_pause), servicePending(ACTION_TOGGLE_PAUSE, 1))
            .addAction(0, getString(R.string.action_stop), servicePending(ACTION_STOP, 2))
            .build()
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, CameraGestureService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun updateNotification() {
        if (!EngineState.running.value) return
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val TAG = "CameraGestureService"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "app.gestenswipe.STOP"
        const val ACTION_TOGGLE_PAUSE = "app.gestenswipe.TOGGLE_PAUSE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CameraGestureService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraGestureService::class.java))
        }
    }
}
