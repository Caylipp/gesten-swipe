package app.gestenswipe.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gestenswipe.access.SwipeAccessibilityService
import app.gestenswipe.engine.CameraGestureService
import app.gestenswipe.engine.EngineState
import app.gestenswipe.gesture.SwipeAction
import app.gestenswipe.settings.SettingsRepository
import app.gestenswipe.settings.AppSettings
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(repo: SettingsRepository, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val settings by repo.settings.collectAsStateWithLifecycle()
    val running by EngineState.running.collectAsStateWithLifecycle()
    val error by EngineState.error.collectAsStateWithLifecycle()

    // Berechtigungen beim Zurückkehren (z. B. aus den Systemeinstellungen) neu prüfen.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val cameraGranted = remember(refresh) { hasPermission(context, Manifest.permission.CAMERA) }
    val notificationsGranted = remember(refresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    val accessibilityEnabled = remember(refresh) { SwipeAccessibilityService.isEnabled(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    PageScaffold(title = "Gesten-Swipe", onBack = null) {
        Text(
            "Swipe durch TikTok, Reels und Shorts mit Kopf, Augen und Mund – ohne Hände.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard("Einrichtung") {
            SetupRow("Kamera-Zugriff", cameraGranted, "Erlauben") { requestPermissions() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupRow("Benachrichtigungen", notificationsGranted, "Erlauben") { requestPermissions() }
            }
            SetupRow("Bedienungshilfe „Gesten-Swipe“", accessibilityEnabled, "Öffnen") {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            if (!accessibilityEnabled) {
                Text(
                    "Falls der Schalter ausgegraut ist (Android 13+): App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text("App-Info öffnen") }
            }
            SetupRow("Kalibrierung", settings.calibration != null, "Starten") { onNavigate(Screen.CALIBRATION) }
        }

        Button(
            onClick = {
                if (running) {
                    CameraGestureService.stop(context)
                } else if (!cameraGranted) {
                    requestPermissions()
                } else {
                    CameraGestureService.start(context)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (running) MaterialTheme.colorScheme.surfaceVariant else Accent),
        ) {
            Text(if (running) "Gestensteuerung stoppen" else "Gestensteuerung starten", fontWeight = FontWeight.SemiBold)
        }

        error?.let {
            Text(it, color = Warn, style = MaterialTheme.typography.bodySmall)
        }

        if (running) LiveStatus(settings)

        SectionCard("Aktive Gesten") {
            settings.mapping.filterValues { it != SwipeAction.NONE }.forEach { (gesture, action) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(gesture.label, style = MaterialTheme.typography.bodyMedium)
                    Text(action.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onNavigate(Screen.TEST) }, modifier = Modifier.weight(1f)) { Text("Testbereich") }
            OutlinedButton(onClick = { onNavigate(Screen.SETTINGS) }, modifier = Modifier.weight(1f)) { Text("Einstellungen") }
        }
        OutlinedButton(onClick = { onNavigate(Screen.CALIBRATION) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (settings.calibration == null) "Kalibrieren" else "Neu kalibrieren")
        }

        Text(
            "Kamerabilder werden nur auf dem Gerät ausgewertet und nie gespeichert. " +
                "Die Kamera läuft nur, während eine Ziel-App im Vordergrund ist.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupRow(label: String, done: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(done)
        Text(label, Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
        if (!done) TextButton(onClick = onAction) { Text(actionLabel) } else Text("✓", color = Ok)
    }
}

@Composable
fun StatusDot(ok: Boolean) {
    Box(Modifier.size(10.dp).background(if (ok) Ok else Warn, CircleShape))
}

@Composable
private fun LiveStatus(settings: AppSettings) {
    val config = settings.detectorConfig()
    val s = config.sensitivity.coerceIn(0.3f, 3f)
    val cameraActive by EngineState.cameraActive.collectAsStateWithLifecycle()
    val paused by EngineState.paused.collectAsStateWithLifecycle()
    val face by EngineState.faceVisible.collectAsStateWithLifecycle()
    val fps by EngineState.fps.collectAsStateWithLifecycle()
    val fg by EngineState.foregroundPackage.collectAsStateWithLifecycle()
    val frame by EngineState.lastFrame.collectAsStateWithLifecycle()
    val down by EngineState.headDown.collectAsStateWithLifecycle()
    val left by EngineState.headLeft.collectAsStateWithLifecycle()

    SectionCard("Live") {
        StatusLine("Kamera", when {
            paused -> "pausiert"
            cameraActive -> "aktiv (%.0f fps)".format(fps)
            else -> "wartet auf Ziel-App"
        }, cameraActive && !paused)
        StatusLine("Gesicht", if (face) "erkannt" else "nicht erkannt", face)
        StatusLine("Vordergrund", fg ?: "unbekannt", true)
        Text(
            if (settings.allApps) "Ziel: alle Apps" else "Ziel: " + settings.targetApps.joinToString { it.label },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cameraActive && frame != null) {
            DeflectionBar("Neigung (+ = runter)", down, config.nodThresholdDeg / s, config.upThresholdDeg / s)
            DeflectionBar("Drehung (+ = links)", left, config.turnThresholdDeg / s, config.turnThresholdDeg / s)
            val f = frame!!
            LevelBar("Augen zu", minOf(f.blinkLeft, f.blinkRight), config.blinkThreshold, "%.2f".format(minOf(f.blinkLeft, f.blinkRight)))
            LevelBar("Mund auf", f.jawOpen, config.jawThreshold, "%.2f".format(f.jawOpen))
        }
        LastGestureBanner()
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(ok)
        Text("$label: ", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Zeigt die zuletzt erkannte Geste für ein paar Sekunden an. */
@Composable
fun LastGestureBanner() {
    val event by EngineState.lastGesture.collectAsStateWithLifecycle()
    var visibleFor by remember { mutableIntStateOf(0) }
    LaunchedEffect(event) {
        if (event == null) return@LaunchedEffect
        visibleFor = 1
        delay(2500)
        visibleFor = 0
    }
    val e = event
    if (e != null && visibleFor > 0) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Accent.copy(alpha = 0.18f), MaterialTheme.shapes.medium)
                .padding(12.dp),
        ) {
            Text("Erkannt: ${e.gesture.label}", fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                if (e.dispatched) "→ ${e.action.label}" else "→ (nicht ausgeführt)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
