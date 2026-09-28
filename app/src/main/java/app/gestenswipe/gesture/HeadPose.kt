package app.gestenswipe.gesture

import kotlin.math.asin
import kotlin.math.atan2

data class EulerAngles(val pitch: Float, val yaw: Float, val roll: Float)

object HeadPose {
    private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    /**
     * Wandelt die 4x4-Gesichtstransformationsmatrix von MediaPipe (spaltenweise, column-major)
     * in Euler-Winkel in Grad um. Das Vorzeichen von Pitch/Yaw wird über die Kalibrierung
     * bestimmt, deshalb reicht hier eine konsistente Zerlegung.
     */
    fun fromMatrix(m: FloatArray): EulerAngles {
        require(m.size >= 16) { "Matrix braucht 16 Werte, hat ${m.size}" }
        fun r(row: Int, col: Int) = m[col * 4 + row]
        val pitch = atan2(r(2, 1), r(2, 2))
        val yaw = asin((-r(2, 0)).coerceIn(-1f, 1f))
        val roll = atan2(r(1, 0), r(0, 0))
        return EulerAngles(pitch * RAD_TO_DEG, yaw * RAD_TO_DEG, roll * RAD_TO_DEG)
    }
}
