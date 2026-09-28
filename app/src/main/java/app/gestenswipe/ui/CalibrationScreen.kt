package app.gestenswipe.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gestenswipe.engine.CameraGestureService
import app.gestenswipe.engine.EngineState
import app.gestenswipe.gesture.CalibrationOutcome
import app.gestenswipe.gesture.CalibrationStep
import app.gestenswipe.gesture.Calibrator
import app.gestenswipe.gesture.FaceFrame
import app.gestenswipe.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val PREPARE_MS = 2000L
private const val CAPTURE_MS = 2000L

private sealed interface CalState {
    data object Idle : CalState
    data class Running(val step: CalibrationStep, val capturing: Boolean) : CalState
    data class Done(val outcome: CalibrationOutcome) : CalState
}

@Composable
fun CalibrationScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val face by EngineState.faceVisible.collectAsStateWithLifecycle()
    val cameraActive by EngineState.cameraActive.collectAsStateWithLifecycle()
    val cameraGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    // Engine für die Kalibrierung starten, Aktionen solange unterdrücken.
    DisposableEffect(Unit) {
        val startedHere = cameraGranted && !EngineState.running.value
        EngineState.suppressActions.value = true
        if (startedHere) CameraGestureService.start(context)
        onDispose {
            EngineState.suppressActions.value = false
            if (startedHere) CameraGestureService.stop(context)
        }
    }

    var state by remember { mutableStateOf<CalState>(CalState.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun startCalibration() {
        job?.cancel()
        job = scope.launch {
            val samples = mutableMapOf<CalibrationStep, List<FaceFrame>>()
            for (step in CalibrationStep.entries) {
                state = CalState.Running(step, capturing = false)
                animateProgress(PREPARE_MS) { progress = it }
                state = CalState.Running(step, capturing = true)
                val collected = mutableListOf<FaceFrame>()
                val collector = launch { EngineState.frames.collect { collected += it } }
                animateProgress(CAPTURE_MS) { progress = it }
                collector.cancel()
                samples[step] = collected.toList()
            }
            val outcome = Calibrator.compute(samples)
            if (outcome is CalibrationOutcome.Success) {
                repo.update { it.copy(calibration = outcome.calibration) }
            }
            state = CalState.Done(outcome)
        }
    }

    PageScaffold(title = "Kalibrierung", onBack = onBack) {
        if (!cameraGranted) {
            Text("Bitte zuerst auf der Startseite den Kamera-Zugriff erlauben.", color = Warn)
            return@PageScaffold
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(face)
                Text(
                    when {
                        !cameraActive -> "Kamera startet …"
                        face -> "Gesicht erkannt"
                        else -> "Kein Gesicht – halte das Handy vor dein Gesicht"
                    },
                )
            }
        }

        when (val s = state) {
            CalState.Idle -> SectionCard("So geht's") {
                Text(
                    "Halte das Handy wie beim Videos schauen. Die App führt dich durch ${CalibrationStep.entries.size} " +
                        "kurze Schritte (je ca. 4 Sekunden). Jeweils zuerst Position einnehmen, dann halten.",
                )
                CalibrationStep.entries.forEachIndexed { i, step ->
                    Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { startCalibration() }, enabled = cameraActive, modifier = Modifier.fillMaxWidth()) {
                    Text("Kalibrierung starten")
                }
            }

            is CalState.Running -> SectionCard {
                val index = CalibrationStep.entries.indexOf(s.step) + 1
                Text("Schritt $index / ${CalibrationStep.entries.size}", style = MaterialTheme.typography.labelMedium)
                Text(s.step.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(s.step.instruction, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (s.capturing) "Halten … wird gemessen" else "Position einnehmen …",
                    color = if (s.capturing) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (s.capturing) Accent else MaterialTheme.colorScheme.secondary,
                )
                OutlinedButton(onClick = { job?.cancel(); state = CalState.Idle }) { Text("Abbrechen") }
            }

            is CalState.Done -> SectionCard("Ergebnis") {
                when (val o = s.outcome) {
                    is CalibrationOutcome.Success -> {
                        val c = o.calibration
                        Text("Kalibrierung gespeichert ✓", color = Ok, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Nicken ab %.0f°, Kopf hoch ab %.0f°, Drehen ab %.0f°\nAugen zu ab %.2f, Mund auf ab %.2f".format(
                                c.nodThresholdDeg, c.upThresholdDeg, c.turnThresholdDeg, c.blinkThreshold, c.jawThreshold,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
                    }
                    is CalibrationOutcome.Failure -> {
                        Text("Schritt „${o.step.title}“ fehlgeschlagen", color = Warn, fontWeight = FontWeight.SemiBold)
                        Text(o.message)
                    }
                }
                OutlinedButton(onClick = { startCalibration() }, modifier = Modifier.fillMaxWidth()) { Text("Nochmal kalibrieren") }
            }
        }
    }
}

private suspend fun animateProgress(durationMs: Long, onProgress: (Float) -> Unit) {
    val start = System.currentTimeMillis()
    withTimeoutOrNull(durationMs + 100) {
        while (true) {
            val p = (System.currentTimeMillis() - start).toFloat() / durationMs
            onProgress(p.coerceAtMost(1f))
            if (p >= 1f) break
            delay(50)
        }
    }
}
