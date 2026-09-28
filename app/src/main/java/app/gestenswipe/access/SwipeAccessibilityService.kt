package app.gestenswipe.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import app.gestenswipe.engine.EngineState
import app.gestenswipe.gesture.SwipeAction

/**
 * Führt Swipes und Tipps systemweit aus (dispatchGesture) und meldet die Vordergrund-App,
 * damit die Kamera nur in Ziel-Apps läuft.
 */
class SwipeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Benachrichtigungsleiste und Tastatur sollen die Ziel-App nicht "verdrängen".
        if (pkg in IGNORED_PACKAGES || pkg.contains("inputmethod")) return
        EngineState.foregroundPackage.value = pkg
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        EngineState.foregroundPackage.value = null
        super.onDestroy()
    }

    fun perform(action: SwipeAction): Boolean {
        val (w, h) = screenSize()
        val cx = w / 2f
        return when (action) {
            SwipeAction.NEXT -> swipe(cx, h * 0.72f, cx, h * 0.28f)
            SwipeAction.PREVIOUS -> swipe(cx, h * 0.28f, cx, h * 0.72f)
            SwipeAction.LIKE -> doubleTap(cx, h * 0.45f)
            SwipeAction.PLAY_PAUSE -> tap(cx, h * 0.45f)
            SwipeAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            SwipeAction.NONE -> false
        }
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun tap(x: Float, y: Float): Boolean {
        val stroke = GestureDescription.StrokeDescription(point(x, y), 0, TAP_DURATION_MS)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun doubleTap(x: Float, y: Float): Boolean {
        val first = GestureDescription.StrokeDescription(point(x, y), 0, TAP_DURATION_MS)
        val second = GestureDescription.StrokeDescription(point(x, y), DOUBLE_TAP_GAP_MS, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(first).addStroke(second).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun point(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    private fun screenSize(): Pair<Float, Float> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width().toFloat() to b.height().toFloat()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels.toFloat() to dm.heightPixels.toFloat()
        }
    }

    companion object {
        private const val SWIPE_DURATION_MS = 220L
        private const val TAP_DURATION_MS = 40L
        private const val DOUBLE_TAP_GAP_MS = 120L
        private val IGNORED_PACKAGES = setOf("com.android.systemui")

        @Volatile
        var instance: SwipeAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, SwipeAccessibilityService::class.java)
            val names = setOf(me.flattenToString(), me.flattenToShortString())
            return enabled.split(':').any { entry -> names.any { it.equals(entry, ignoreCase = true) } }
        }
    }
}
