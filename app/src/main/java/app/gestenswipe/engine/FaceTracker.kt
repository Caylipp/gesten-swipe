package app.gestenswipe.engine

import android.content.Context
import android.graphics.Bitmap
import app.gestenswipe.gesture.FaceFrame
import app.gestenswipe.gesture.HeadPose
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.Closeable

/**
 * Dünne Hülle um den MediaPipe Face Landmarker im Live-Stream-Modus.
 * [onResult] bekommt ein [FaceFrame] oder null, wenn kein Gesicht im Bild ist.
 */
class FaceTracker(
    context: Context,
    private val onResult: (frame: FaceFrame?, timestampMs: Long) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {

    private val landmarker: FaceLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(Delegate.CPU)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result, _ -> handle(result) }
            .setErrorListener { e -> onError(e.message ?: "MediaPipe-Fehler") }
            .build()
        landmarker = FaceLandmarker.createFromOptions(context, options)
    }

    /** [timestampMs] muss streng monoton steigen. */
    fun detect(bitmap: Bitmap, timestampMs: Long) {
        landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
    }

    private fun handle(result: FaceLandmarkerResult) {
        val ts = result.timestampMs()
        val matrix = result.facialTransformationMatrixes().orElse(null)?.firstOrNull()
        if (result.faceLandmarks().isEmpty() || matrix == null) {
            onResult(null, ts)
            return
        }
        val scores = result.faceBlendshapes().orElse(null)?.firstOrNull()
            ?.associate { it.categoryName() to it.score() }
            .orEmpty()
        val pose = HeadPose.fromMatrix(matrix)
        onResult(
            FaceFrame(
                timestampMs = ts,
                pitch = pose.pitch,
                yaw = pose.yaw,
                roll = pose.roll,
                blinkLeft = scores["eyeBlinkLeft"] ?: 0f,
                blinkRight = scores["eyeBlinkRight"] ?: 0f,
                jawOpen = scores["jawOpen"] ?: 0f,
            ),
            ts,
        )
    }

    override fun close() = landmarker.close()

    companion object {
        const val MODEL_ASSET = "face_landmarker.task"
    }
}
