package app.gestenswipe.settings

import android.content.Context
import androidx.core.content.edit
import app.gestenswipe.gesture.Calibration
import app.gestenswipe.gesture.DetectorConfig
import app.gestenswipe.gesture.Gesture
import app.gestenswipe.gesture.SwipeAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TargetApp(val label: String, val packages: Set<String>) {
    TIKTOK("TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
    INSTAGRAM("Instagram Reels", setOf("com.instagram.android")),
    YOUTUBE("YouTube Shorts", setOf("com.google.android.youtube")),
    SNAPCHAT("Snapchat Spotlight", setOf("com.snapchat.android")),
    FACEBOOK("Facebook Reels", setOf("com.facebook.katana")),
}

data class AppSettings(
    val mapping: Map<Gesture, SwipeAction> = DEFAULT_MAPPING,
    val targetApps: Set<TargetApp> = setOf(TargetApp.TIKTOK, TargetApp.INSTAGRAM, TargetApp.YOUTUBE),
    val allApps: Boolean = false,
    val sensitivity: Float = 1f,
    val cooldownMs: Long = 900,
    val fps: Int = 15,
    val swapLeftRight: Boolean = false,
    val calibration: Calibration? = null,
) {
    fun actionFor(gesture: Gesture): SwipeAction = mapping[gesture] ?: SwipeAction.NONE

    fun isTarget(packageName: String?, ownPackage: String): Boolean {
        // Ohne Accessibility-Service kennen wir die Vordergrund-App nicht: dann nicht blockieren.
        if (packageName == null || allApps || packageName == ownPackage) return true
        return targetApps.any { packageName in it.packages }
    }

    fun detectorConfig(): DetectorConfig {
        val base = DetectorConfig(sensitivity = sensitivity, cooldownMs = cooldownMs)
        val withCal = calibration?.let {
            base.copy(
                pitchDownSign = it.pitchDownSign,
                yawLeftSign = it.yawLeftSign,
                nodThresholdDeg = it.nodThresholdDeg,
                upThresholdDeg = it.upThresholdDeg,
                turnThresholdDeg = it.turnThresholdDeg,
                jawThreshold = it.jawThreshold,
                blinkThreshold = it.blinkThreshold,
            )
        } ?: base
        return if (swapLeftRight) withCal.copy(yawLeftSign = -withCal.yawLeftSign) else withCal
    }

    companion object {
        val DEFAULT_MAPPING = mapOf(
            Gesture.NOD_DOWN to SwipeAction.NEXT,
            Gesture.HEAD_UP to SwipeAction.PREVIOUS,
            Gesture.TURN_LEFT to SwipeAction.NONE,
            Gesture.TURN_RIGHT to SwipeAction.NONE,
            Gesture.DOUBLE_BLINK to SwipeAction.LIKE,
            Gesture.MOUTH_OPEN to SwipeAction.PLAY_PAUSE,
        )
    }
}

/** Einstellungen in SharedPreferences, beobachtbar als [StateFlow]. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        save(next)
        _settings.value = next
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        val mapping = Gesture.entries.associateWith { g ->
            prefs.getString("map_${g.name}", null)
                ?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
                ?: d.actionFor(g)
        }
        val targets = prefs.getStringSet("targets", null)
            ?.mapNotNull { runCatching { TargetApp.valueOf(it) }.getOrNull() }?.toSet()
            ?: d.targetApps
        val calibration = if (prefs.getBoolean("cal_present", false)) {
            Calibration(
                pitchDownSign = prefs.getFloat("cal_pitchDownSign", -1f),
                yawLeftSign = prefs.getFloat("cal_yawLeftSign", 1f),
                nodThresholdDeg = prefs.getFloat("cal_nod", 12f),
                upThresholdDeg = prefs.getFloat("cal_up", 12f),
                turnThresholdDeg = prefs.getFloat("cal_turn", 20f),
                jawThreshold = prefs.getFloat("cal_jaw", 0.45f),
                blinkThreshold = prefs.getFloat("cal_blink", 0.5f),
            )
        } else null
        return AppSettings(
            mapping = mapping,
            targetApps = targets,
            allApps = prefs.getBoolean("allApps", d.allApps),
            sensitivity = prefs.getFloat("sensitivity", d.sensitivity),
            cooldownMs = prefs.getLong("cooldownMs", d.cooldownMs),
            fps = prefs.getInt("fps", d.fps),
            swapLeftRight = prefs.getBoolean("swapLeftRight", d.swapLeftRight),
            calibration = calibration,
        )
    }

    private fun save(s: AppSettings) = prefs.edit {
        s.mapping.forEach { (g, a) -> putString("map_${g.name}", a.name) }
        putStringSet("targets", s.targetApps.map { it.name }.toSet())
        putBoolean("allApps", s.allApps)
        putFloat("sensitivity", s.sensitivity)
        putLong("cooldownMs", s.cooldownMs)
        putInt("fps", s.fps)
        putBoolean("swapLeftRight", s.swapLeftRight)
        val c = s.calibration
        putBoolean("cal_present", c != null)
        if (c != null) {
            putFloat("cal_pitchDownSign", c.pitchDownSign)
            putFloat("cal_yawLeftSign", c.yawLeftSign)
            putFloat("cal_nod", c.nodThresholdDeg)
            putFloat("cal_up", c.upThresholdDeg)
            putFloat("cal_turn", c.turnThresholdDeg)
            putFloat("cal_jaw", c.jawThreshold)
            putFloat("cal_blink", c.blinkThreshold)
        }
    }
}
