package app.gestenswipe.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CalibratorTest {

    private fun frames(
        n: Int = 20,
        pitch: Float = 0f,
        yaw: Float = 0f,
        blink: Float = 0.05f,
        jaw: Float = 0.02f,
    ) = List(n) { i ->
        val jitter = if (i % 2 == 0) 0.3f else -0.3f
        FaceFrame(i * 66L, pitch + jitter, yaw + jitter, 0f, blink, blink, jaw)
    }

    private fun validSamples(downPitch: Float = 15f, upPitch: Float = -12f, leftYaw: Float = -25f) = mapOf(
        CalibrationStep.NEUTRAL to frames(),
        CalibrationStep.DOWN to frames(pitch = downPitch),
        CalibrationStep.UP to frames(pitch = upPitch),
        CalibrationStep.LEFT to frames(yaw = leftYaw),
        CalibrationStep.MOUTH to frames(jaw = 0.8f),
        CalibrationStep.EYES_CLOSED to frames(blink = 0.85f),
    )

    @Test
    fun `bestimmt Vorzeichen und Schwellwerte`() {
        val outcome = Calibrator.compute(validSamples())
        assertTrue(outcome is CalibrationOutcome.Success)
        val c = (outcome as CalibrationOutcome.Success).calibration
        assertEquals(1f, c.pitchDownSign)
        assertEquals(-1f, c.yawLeftSign)
        assertTrue("nod ${c.nodThresholdDeg}", abs(c.nodThresholdDeg - 15f * 0.55f) < 0.5f)
        assertTrue("up ${c.upThresholdDeg}", abs(c.upThresholdDeg - 12f * 0.55f) < 0.5f)
        assertTrue(c.jawThreshold in 0.3f..0.5f)
        assertTrue(c.blinkThreshold in 0.35f..0.55f)
    }

    @Test
    fun `kalibrierter Detektor erkennt Nicken in kalibrierter Richtung`() {
        val c = (Calibrator.compute(validSamples()) as CalibrationOutcome.Success).calibration
        val detector = GestureDetector(
            DetectorConfig(
                pitchDownSign = c.pitchDownSign,
                yawLeftSign = c.yawLeftSign,
                nodThresholdDeg = c.nodThresholdDeg,
                upThresholdDeg = c.upThresholdDeg,
                turnThresholdDeg = c.turnThresholdDeg,
            ),
        )
        var ts = 0L
        val out = mutableListOf<Gesture>()
        fun feed(n: Int, pitch: Float) = repeat(n) {
            ts += 66
            detector.process(FaceFrame(ts, pitch, 0f, 0f, 0f, 0f, 0f))?.let { out += it }
        }
        feed(15, 0f)
        feed(6, 14f)
        feed(10, 0f)
        assertEquals(listOf(Gesture.NOD_DOWN), out)
    }

    @Test
    fun `zu kleine Bewegung nach unten schlaegt fehl`() {
        val outcome = Calibrator.compute(validSamples(downPitch = 2f))
        assertTrue(outcome is CalibrationOutcome.Failure)
        assertEquals(CalibrationStep.DOWN, (outcome as CalibrationOutcome.Failure).step)
    }

    @Test
    fun `hoch in gleicher Richtung wie runter schlaegt fehl`() {
        val outcome = Calibrator.compute(validSamples(upPitch = 10f))
        assertTrue(outcome is CalibrationOutcome.Failure)
        assertEquals(CalibrationStep.UP, (outcome as CalibrationOutcome.Failure).step)
    }

    @Test
    fun `fehlende Messwerte schlagen fehl`() {
        val samples = validSamples() + (CalibrationStep.MOUTH to frames(n = 2))
        val outcome = Calibrator.compute(samples)
        assertTrue(outcome is CalibrationOutcome.Failure)
        assertEquals(CalibrationStep.MOUTH, (outcome as CalibrationOutcome.Failure).step)
    }

    @Test
    fun `HeadPose liefert null bei Einheitsmatrix`() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val e = HeadPose.fromMatrix(identity)
        assertEquals(0f, e.pitch, 0.001f)
        assertEquals(0f, e.yaw, 0.001f)
        assertEquals(0f, e.roll, 0.001f)
    }

    @Test
    fun `HeadPose erkennt Rotation um die x-Achse`() {
        val a = Math.toRadians(20.0)
        val c = kotlin.math.cos(a).toFloat()
        val s = kotlin.math.sin(a).toFloat()
        // Rx(20°), spaltenweise
        val m = floatArrayOf(1f, 0f, 0f, 0f, 0f, c, s, 0f, 0f, -s, c, 0f, 0f, 0f, 0f, 1f)
        assertEquals(20f, HeadPose.fromMatrix(m).pitch, 0.01f)
    }
}
