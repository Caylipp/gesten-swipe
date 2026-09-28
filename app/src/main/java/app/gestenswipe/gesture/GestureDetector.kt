package app.gestenswipe.gesture

import kotlin.math.max
import kotlin.math.min

data class DetectorConfig(
    /** Vorzeichen von (pitch - grundhaltung), wenn der Kopf nach unten geht. */
    val pitchDownSign: Float = -1f,
    /** Vorzeichen von (yaw - grundhaltung), wenn der Kopf nach links geht. */
    val yawLeftSign: Float = 1f,
    val nodThresholdDeg: Float = 12f,
    val upThresholdDeg: Float = 12f,
    val turnThresholdDeg: Float = 20f,
    val blinkThreshold: Float = 0.5f,
    val jawThreshold: Float = 0.45f,
    /** > 1 = empfindlicher (Kopf-Schwellwerte werden geteilt). */
    val sensitivity: Float = 1f,
    val cooldownMs: Long = 900,
    /** Rückkehr unter Schwelle * hysteresis beendet eine Kopfgeste. */
    val hysteresis: Float = 0.5f,
    val minHeadGestureMs: Long = 60,
    val maxHeadGestureMs: Long = 1500,
    /** Bleibt der Kopf so lange ausgelenkt, wird die neue Haltung zur Grundhaltung. */
    val rebaselineAfterMs: Long = 3000,
    /** Anteil pro Frame, mit dem die Grundhaltung im Ruhezustand nachgeführt wird. */
    val baselineAdaptRate: Float = 0.01f,
    val maxBlinkMs: Long = 500,
    val doubleBlinkWindowMs: Long = 700,
    val mouthHoldMs: Long = 250,
    /** Ohne Gesicht für diese Zeit wird die Grundhaltung neu bestimmt. */
    val faceLostResetMs: Long = 2000,
)

/**
 * Zustandsautomat, der aus einer Folge von [FaceFrame]s einzelne [Gesture]s macht.
 *
 * Robustheit:
 * - Kopfgesten sind relativ zu einer Grundhaltung, die im Ruhezustand langsam mitwandert.
 * - Hysterese: eine Kopfgeste beginnt über der Schwelle und endet erst deutlich darunter.
 * - Eine Kopfgeste zählt nur, wenn der Kopf innerhalb von [DetectorConfig.maxHeadGestureMs]
 *   zurückkommt. Längeres Runterschauen löst nichts aus.
 * - Globaler Cooldown nach jeder Geste. Blinzeln wird während Kopfbewegungen ignoriert,
 *   weil Runterschauen die Blink-Werte verfälscht.
 *
 * Nicht thread-sicher; alle Aufrufe müssen vom selben Thread kommen oder synchronisiert sein.
 */
class GestureDetector(config: DetectorConfig = DetectorConfig()) {

    var config: DetectorConfig = config
        set(value) {
            field = value
            reset()
        }

    private enum class Dir { DOWN, UP, LEFT, RIGHT }

    private var hasBaseline = false
    private var basePitch = 0f
    private var baseYaw = 0f
    private var lastFaceTs = Long.MIN_VALUE
    private var lastEmitTs = Long.MIN_VALUE

    private var activeDir: Dir? = null
    private var activeStart = 0L
    private var lastHeadEndTs = Long.MIN_VALUE

    private var eyesClosed = false
    private var eyesClosedSince = 0L
    private var lastBlinkEndTs: Long? = null

    private var mouthOpenSince: Long? = null
    private var mouthFired = false

    /** Aktuelle Auslenkung nach unten in Grad (für die Debug-Anzeige). */
    var currentDown = 0f
        private set

    /** Aktuelle Auslenkung nach links in Grad (für die Debug-Anzeige). */
    var currentLeft = 0f
        private set

    fun reset() {
        hasBaseline = false
        activeDir = null
        lastHeadEndTs = Long.MIN_VALUE
        eyesClosed = false
        lastBlinkEndTs = null
        mouthOpenSince = null
        mouthFired = false
        currentDown = 0f
        currentLeft = 0f
    }

    /** Aufrufen, wenn in einem Frame kein Gesicht erkannt wurde. */
    fun onNoFace() {
        activeDir = null
        eyesClosed = false
        lastBlinkEndTs = null
        mouthOpenSince = null
        mouthFired = false
    }

    fun process(f: FaceFrame): Gesture? {
        val c = config
        val ts = f.timestampMs
        if (!hasBaseline || (lastFaceTs != Long.MIN_VALUE && ts - lastFaceTs > c.faceLostResetMs)) {
            basePitch = f.pitch
            baseYaw = f.yaw
            hasBaseline = true
            activeDir = null
        }
        lastFaceTs = ts

        val inCooldown = lastEmitTs != Long.MIN_VALUE && ts - lastEmitTs < c.cooldownMs
        val head = processHead(f, c, inCooldown)
        val blink = processBlink(f, c, inCooldown || head != null)
        val mouth = processMouth(f, c, inCooldown || head != null || blink != null)

        val gesture = head ?: blink ?: mouth
        if (gesture != null) lastEmitTs = ts
        return gesture
    }

    private fun thresholds(c: DetectorConfig): Triple<Float, Float, Float> {
        val s = c.sensitivity.coerceIn(0.3f, 3f)
        return Triple(c.nodThresholdDeg / s, c.upThresholdDeg / s, c.turnThresholdDeg / s)
    }

    private fun processHead(f: FaceFrame, c: DetectorConfig, inCooldown: Boolean): Gesture? {
        val ts = f.timestampMs
        val down = angleDelta(f.pitch, basePitch) * c.pitchDownSign
        val left = angleDelta(f.yaw, baseYaw) * c.yawLeftSign
        currentDown = down
        currentLeft = left
        val (thrDown, thrUp, thrTurn) = thresholds(c)

        fun ratio(dir: Dir) = when (dir) {
            Dir.DOWN -> down / thrDown
            Dir.UP -> -down / thrUp
            Dir.LEFT -> left / thrTurn
            Dir.RIGHT -> -left / thrTurn
        }

        val current = activeDir
        if (current == null) {
            if (!inCooldown) {
                val best = Dir.entries.maxBy { ratio(it) }
                if (ratio(best) > 1f) {
                    activeDir = best
                    activeStart = ts
                    return null
                }
            }
            basePitch += angleDelta(f.pitch, basePitch) * c.baselineAdaptRate
            baseYaw += angleDelta(f.yaw, baseYaw) * c.baselineAdaptRate
            return null
        }

        val duration = ts - activeStart
        if (ratio(current) < c.hysteresis) {
            activeDir = null
            lastHeadEndTs = ts
            val valid = duration in c.minHeadGestureMs..c.maxHeadGestureMs
            return if (valid && !inCooldown) current.toGesture() else null
        }
        if (duration > c.rebaselineAfterMs) {
            // Haltung dauerhaft geändert: neu einrasten statt hängen zu bleiben.
            basePitch = f.pitch
            baseYaw = f.yaw
            activeDir = null
            lastHeadEndTs = ts
        }
        return null
    }

    private fun processBlink(f: FaceFrame, c: DetectorConfig, blocked: Boolean): Gesture? {
        val ts = f.timestampMs
        val headBusy = activeDir != null ||
            (lastHeadEndTs != Long.MIN_VALUE && ts - lastHeadEndTs < 300)
        val closed = min(f.blinkLeft, f.blinkRight) > c.blinkThreshold
        val open = max(f.blinkLeft, f.blinkRight) < c.blinkThreshold * 0.6f

        if (!eyesClosed && closed) {
            eyesClosed = true
            eyesClosedSince = ts
            return null
        }
        if (eyesClosed && open) {
            eyesClosed = false
            if (headBusy || ts - eyesClosedSince > c.maxBlinkMs) {
                lastBlinkEndTs = null
                return null
            }
            val prev = lastBlinkEndTs
            if (prev != null && ts - prev <= c.doubleBlinkWindowMs && !blocked) {
                lastBlinkEndTs = null
                return Gesture.DOUBLE_BLINK
            }
            lastBlinkEndTs = ts
        }
        return null
    }

    private fun processMouth(f: FaceFrame, c: DetectorConfig, blocked: Boolean): Gesture? {
        val ts = f.timestampMs
        if (f.jawOpen > c.jawThreshold) {
            val since = mouthOpenSince ?: ts.also { mouthOpenSince = it }
            if (!mouthFired && !blocked && ts - since >= c.mouthHoldMs) {
                mouthFired = true
                return Gesture.MOUTH_OPEN
            }
        } else if (f.jawOpen < c.jawThreshold * 0.5f) {
            mouthOpenSince = null
            mouthFired = false
        }
        return null
    }

    private fun Dir.toGesture() = when (this) {
        Dir.DOWN -> Gesture.NOD_DOWN
        Dir.UP -> Gesture.HEAD_UP
        Dir.LEFT -> Gesture.TURN_LEFT
        Dir.RIGHT -> Gesture.TURN_RIGHT
    }
}
