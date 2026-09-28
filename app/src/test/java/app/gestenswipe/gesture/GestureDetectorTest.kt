package app.gestenswipe.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDetectorTest {

    /** Simuliert eine Kamera mit 15 fps. */
    private class Sim(config: DetectorConfig = DetectorConfig()) {
        val detector = GestureDetector(config)
        var ts = 0L
        val gestures = mutableListOf<Gesture>()

        fun frames(
            count: Int,
            pitch: Float = 0f,
            yaw: Float = 0f,
            blink: Float = 0.05f,
            jaw: Float = 0.02f,
        ) {
            repeat(count) {
                ts += 66
                detector.process(FaceFrame(ts, pitch, yaw, 0f, blink, blink, jaw))?.let { gestures += it }
            }
        }

        fun ms(duration: Long) = (duration / 66).toInt().coerceAtLeast(1)
    }

    // Standard: pitchDownSign = -1, d. h. Kopf runter = Pitch wird kleiner.

    @Test
    fun `kurzes Nicken nach unten loest NOD_DOWN aus`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(400), pitch = -20f)
        sim.frames(10)
        assertEquals(listOf(Gesture.NOD_DOWN), sim.gestures)
    }

    @Test
    fun `Kopf kurz hoch loest HEAD_UP aus`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(400), pitch = 20f)
        sim.frames(10)
        assertEquals(listOf(Gesture.HEAD_UP), sim.gestures)
    }

    @Test
    fun `Kopfdrehung links und rechts`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(400), yaw = 30f)
        sim.frames(20)
        sim.frames(sim.ms(400), yaw = -30f)
        sim.frames(10)
        assertEquals(listOf(Gesture.TURN_LEFT, Gesture.TURN_RIGHT), sim.gestures)
    }

    @Test
    fun `kleine Bewegung unter der Schwelle loest nichts aus`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(400), pitch = -8f)
        sim.frames(10)
        assertTrue(sim.gestures.isEmpty())
    }

    @Test
    fun `langes Runterschauen loest nichts aus und rastet neu ein`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(4000), pitch = -20f)
        // Neue Haltung ist jetzt Grundhaltung: dort verharren darf nichts auslösen …
        sim.frames(30, pitch = -20f)
        assertTrue(sim.gestures.isEmpty())
        // … aber ein Nicken relativ zur neuen Haltung schon.
        sim.frames(sim.ms(400), pitch = -40f)
        sim.frames(10, pitch = -20f)
        assertEquals(listOf(Gesture.NOD_DOWN), sim.gestures)
    }

    @Test
    fun `Cooldown verhindert Doppelausloesung durch Zurueckschwingen`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(300), pitch = -20f)
        // Überschwingen nach oben direkt nach dem Nicken
        sim.frames(sim.ms(300), pitch = 18f)
        sim.frames(20)
        assertEquals(listOf(Gesture.NOD_DOWN), sim.gestures)
    }

    @Test
    fun `zwei schnelle Nicken nach dem Cooldown zaehlen doppelt`() {
        val sim = Sim()
        sim.frames(15)
        repeat(2) {
            sim.frames(sim.ms(300), pitch = -20f)
            sim.frames(sim.ms(1200))
        }
        assertEquals(listOf(Gesture.NOD_DOWN, Gesture.NOD_DOWN), sim.gestures)
    }

    @Test
    fun `Doppelblinzeln wird erkannt, einzelnes Blinzeln nicht`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(2, blink = 0.9f)
        sim.frames(20)
        assertTrue("Einzelnes Blinzeln darf nichts auslösen", sim.gestures.isEmpty())

        sim.frames(2, blink = 0.9f)
        sim.frames(3)
        sim.frames(2, blink = 0.9f)
        sim.frames(5)
        assertEquals(listOf(Gesture.DOUBLE_BLINK), sim.gestures)
    }

    @Test
    fun `lang geschlossene Augen zaehlen nicht als Blinzeln`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(1500), blink = 0.9f)
        sim.frames(3)
        sim.frames(2, blink = 0.9f)
        sim.frames(5)
        assertTrue(sim.gestures.isEmpty())
    }

    @Test
    fun `Mund oeffnen loest einmal aus und muss erst wieder schliessen`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(sim.ms(2000), jaw = 0.7f)
        assertEquals(listOf(Gesture.MOUTH_OPEN), sim.gestures)
        sim.frames(sim.ms(1000))
        sim.frames(sim.ms(500), jaw = 0.7f)
        assertEquals(listOf(Gesture.MOUTH_OPEN, Gesture.MOUTH_OPEN), sim.gestures)
    }

    @Test
    fun `kurzes Mundzucken loest nichts aus`() {
        val sim = Sim()
        sim.frames(15)
        sim.frames(2, jaw = 0.7f)
        sim.frames(10)
        assertTrue(sim.gestures.isEmpty())
    }

    @Test
    fun `Kalibriertes Vorzeichen dreht die Richtung`() {
        val sim = Sim(DetectorConfig(pitchDownSign = 1f))
        sim.frames(15)
        sim.frames(sim.ms(400), pitch = 20f)
        sim.frames(10)
        assertEquals(listOf(Gesture.NOD_DOWN), sim.gestures)
    }

    @Test
    fun `hoehere Empfindlichkeit erkennt kleinere Bewegungen`() {
        val sim = Sim(DetectorConfig(sensitivity = 2f))
        sim.frames(15)
        sim.frames(sim.ms(400), pitch = -8f)
        sim.frames(10)
        assertEquals(listOf(Gesture.NOD_DOWN), sim.gestures)
    }

    @Test
    fun `Grundhaltung um 180 Grad springt nicht`() {
        val sim = Sim()
        sim.frames(15, pitch = 179f)
        sim.frames(10, pitch = -179f)
        assertTrue(sim.gestures.isEmpty())
    }

    @Test
    fun `nach Gesichtsverlust wird die Grundhaltung neu bestimmt`() {
        val sim = Sim()
        sim.frames(15)
        sim.detector.onNoFace()
        sim.ts += 3000
        sim.frames(15, pitch = -25f)
        assertNull(sim.gestures.firstOrNull())
    }
}
