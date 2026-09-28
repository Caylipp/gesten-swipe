package app.gestenswipe.engine

import app.gestenswipe.gesture.FaceFrame
import app.gestenswipe.gesture.GestureEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Prozessweiter Zustand, den Kamera-Service, Accessibility-Service und UI teilen.
 * Alle Komponenten laufen im selben Prozess, daher reicht ein Singleton.
 */
object EngineState {
    /** Der Kamera-Service läuft (Gestensteuerung eingeschaltet). */
    val running = MutableStateFlow(false)

    /** Vom Nutzer über die Benachrichtigung pausiert. */
    val paused = MutableStateFlow(false)

    /** Kamera ist gerade gebunden (nur in Ziel-Apps und bei eingeschaltetem Bildschirm). */
    val cameraActive = MutableStateFlow(false)

    val faceVisible = MutableStateFlow(false)
    val lastFrame = MutableStateFlow<FaceFrame?>(null)
    val frames = MutableSharedFlow<FaceFrame>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val lastGesture = MutableStateFlow<GestureEvent?>(null)

    /** Auslenkung relativ zur Grundhaltung in Grad (Debug-Anzeige). */
    val headDown = MutableStateFlow(0f)
    val headLeft = MutableStateFlow(0f)

    val fps = MutableStateFlow(0f)

    /** Paketname der App im Vordergrund, gemeldet vom Accessibility-Service. */
    val foregroundPackage = MutableStateFlow<String?>(null)

    /** Während der Kalibrierung werden erkannte Gesten nicht ausgeführt. */
    val suppressActions = MutableStateFlow(false)

    val error = MutableStateFlow<String?>(null)
}
