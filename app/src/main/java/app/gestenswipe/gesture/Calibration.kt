package app.gestenswipe.gesture

import kotlin.math.abs
import kotlin.math.sign

enum class CalibrationStep(val title: String, val instruction: String) {
    NEUTRAL("Grundhaltung", "Schau entspannt aufs Handy, so wie beim Videos schauen."),
    DOWN("Kopf runter", "Neige den Kopf deutlich nach unten und halte ihn dort."),
    UP("Kopf hoch", "Hebe das Kinn deutlich an und halte den Kopf dort."),
    LEFT("Kopf links", "Drehe den Kopf nach links und halte ihn dort."),
    MOUTH("Mund auf", "Öffne den Mund weit und halte ihn offen."),
    EYES_CLOSED("Augen zu", "Schließe beide Augen und halte sie geschlossen."),
}

/** Ergebnis einer Kalibrierung; wird in den Einstellungen gespeichert. */
data class Calibration(
    val pitchDownSign: Float,
    val yawLeftSign: Float,
    val nodThresholdDeg: Float,
    val upThresholdDeg: Float,
    val turnThresholdDeg: Float,
    val jawThreshold: Float,
    val blinkThreshold: Float,
)

sealed interface CalibrationOutcome {
    data class Success(val calibration: Calibration) : CalibrationOutcome
    data class Failure(val step: CalibrationStep, val message: String) : CalibrationOutcome
}

object Calibrator {
    private const val MIN_SAMPLES = 5

    fun compute(samples: Map<CalibrationStep, List<FaceFrame>>): CalibrationOutcome {
        for (step in CalibrationStep.entries) {
            if ((samples[step]?.size ?: 0) < MIN_SAMPLES) {
                return CalibrationOutcome.Failure(step, "Zu wenige Messwerte – ist dein Gesicht gut sichtbar?")
            }
        }
        val neutral = samples.getValue(CalibrationStep.NEUTRAL)
        val nPitch = median(neutral.map { it.pitch })
        val nYaw = median(neutral.map { it.yaw })
        val nJaw = median(neutral.map { it.jawOpen })
        val nBlink = median(neutral.map { (it.blinkLeft + it.blinkRight) / 2f })

        val down = robustExtreme(samples.getValue(CalibrationStep.DOWN).map { angleDelta(it.pitch, nPitch) })
        if (abs(down) < 4f) {
            return CalibrationOutcome.Failure(CalibrationStep.DOWN, "Kopfbewegung nach unten zu klein (${fmt(down)}°).")
        }
        val downSign = sign(down)

        val up = robustExtreme(samples.getValue(CalibrationStep.UP).map { angleDelta(it.pitch, nPitch) }) * downSign
        if (up > -3f) {
            return CalibrationOutcome.Failure(CalibrationStep.UP, "Kopf hoch nicht erkannt (${fmt(-up)}°).")
        }

        val left = robustExtreme(samples.getValue(CalibrationStep.LEFT).map { angleDelta(it.yaw, nYaw) })
        if (abs(left) < 5f) {
            return CalibrationOutcome.Failure(CalibrationStep.LEFT, "Kopfdrehung zu klein (${fmt(left)}°).")
        }

        val jawMax = percentile(samples.getValue(CalibrationStep.MOUTH).map { it.jawOpen }, 0.8f)
        if (jawMax - nJaw < 0.15f) {
            return CalibrationOutcome.Failure(CalibrationStep.MOUTH, "Mundöffnung zu klein erkannt.")
        }

        val closed = median(samples.getValue(CalibrationStep.EYES_CLOSED).map { minOf(it.blinkLeft, it.blinkRight) })
        if (closed - nBlink < 0.2f) {
            return CalibrationOutcome.Failure(CalibrationStep.EYES_CLOSED, "Geschlossene Augen nicht sicher erkannt.")
        }

        return CalibrationOutcome.Success(
            Calibration(
                pitchDownSign = downSign,
                yawLeftSign = sign(left),
                nodThresholdDeg = (abs(down) * 0.55f).coerceIn(5f, 30f),
                upThresholdDeg = (abs(up) * 0.55f).coerceIn(5f, 30f),
                turnThresholdDeg = (abs(left) * 0.5f).coerceIn(8f, 40f),
                jawThreshold = (nJaw + (jawMax - nJaw) * 0.5f).coerceIn(0.15f, 0.8f),
                blinkThreshold = (nBlink + (closed - nBlink) * 0.5f).coerceIn(0.25f, 0.8f),
            )
        )
    }

    internal fun median(values: List<Float>): Float = percentile(values, 0.5f)

    internal fun percentile(values: List<Float>, p: Float): Float {
        val sorted = values.sorted()
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    /** Wert mit großer Auslenkung (80. Perzentil nach Betrag), Vorzeichen bleibt erhalten. */
    internal fun robustExtreme(values: List<Float>): Float {
        val sorted = values.sortedBy { abs(it) }
        return sorted[((sorted.size - 1) * 0.8f).toInt()]
    }

    private fun fmt(v: Float) = "%.1f".format(v)
}
