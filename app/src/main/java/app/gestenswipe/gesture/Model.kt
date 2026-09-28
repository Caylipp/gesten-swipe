package app.gestenswipe.gesture

/** Gesten, die der [GestureDetector] erkennen kann. */
enum class Gesture(val label: String, val hint: String) {
    NOD_DOWN("Nicken (Kopf runter)", "Kinn kurz Richtung Brust, dann zurück"),
    HEAD_UP("Kopf hoch", "Kinn kurz anheben, dann zurück"),
    TURN_LEFT("Kopf nach links", "Kopf kurz nach links drehen, dann zurück"),
    TURN_RIGHT("Kopf nach rechts", "Kopf kurz nach rechts drehen, dann zurück"),
    DOUBLE_BLINK("Doppelt blinzeln", "Zweimal schnell und bewusst blinzeln"),
    MOUTH_OPEN("Mund öffnen", "Mund kurz weit öffnen"),
}

/** Aktionen, die der Accessibility-Service in der Ziel-App ausführt. */
enum class SwipeAction(val label: String) {
    NEXT("Nächstes Video (hoch swipen)"),
    PREVIOUS("Vorheriges Video (runter swipen)"),
    LIKE("Liken (Doppeltipp)"),
    PLAY_PAUSE("Pause / Weiter (Tipp)"),
    BACK("Zurück"),
    NONE("Keine Aktion"),
}

/**
 * Ein ausgewertetes Kamerabild: Kopfpose in Grad und die relevanten
 * MediaPipe-Blendshapes (0..1).
 */
data class FaceFrame(
    val timestampMs: Long,
    val pitch: Float,
    val yaw: Float,
    val roll: Float,
    val blinkLeft: Float,
    val blinkRight: Float,
    val jawOpen: Float,
)

data class GestureEvent(
    val gesture: Gesture,
    val action: SwipeAction,
    val timestampMs: Long,
    /** false, wenn die Aktion unterdrückt wurde (z. B. während der Kalibrierung). */
    val dispatched: Boolean,
)

/** Differenz zweier Winkel in Grad, normiert auf -180..180. */
fun angleDelta(a: Float, b: Float): Float {
    var d = (a - b) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}
