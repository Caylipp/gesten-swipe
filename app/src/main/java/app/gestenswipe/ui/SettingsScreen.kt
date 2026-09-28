package app.gestenswipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gestenswipe.gesture.Gesture
import app.gestenswipe.gesture.SwipeAction
import app.gestenswipe.settings.AppSettings
import app.gestenswipe.settings.SettingsRepository
import app.gestenswipe.settings.TargetApp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val settings by repo.settings.collectAsStateWithLifecycle()

    PageScaffold(title = "Einstellungen", onBack = onBack) {
        SectionCard("Gesten → Aktionen") {
            Gesture.entries.forEach { gesture ->
                GestureMappingRow(gesture, settings.actionFor(gesture)) { action ->
                    repo.update { it.copy(mapping = it.mapping + (gesture to action)) }
                }
            }
            TextButton(onClick = { repo.update { it.copy(mapping = AppSettings.DEFAULT_MAPPING) } }) {
                Text("Standard wiederherstellen")
            }
        }

        SectionCard("Ziel-Apps") {
            Text(
                "Die Kamera läuft nur, wenn eine dieser Apps im Vordergrund ist (und in Gesten-Swipe selbst).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TargetApp.entries.forEach { app ->
                CheckRow(app.label, app in settings.targetApps, enabled = !settings.allApps) { checked ->
                    repo.update { s -> s.copy(targetApps = if (checked) s.targetApps + app else s.targetApps - app) }
                }
            }
            SwitchRow("In allen Apps aktiv", settings.allApps) { v -> repo.update { it.copy(allApps = v) } }
        }

        SectionCard("Erkennung") {
            Text("Empfindlichkeit: ${"%.1f".format(settings.sensitivity)}×", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.sensitivity,
                onValueChange = { v -> repo.update { it.copy(sensitivity = (v * 10).roundToInt() / 10f) } },
                valueRange = 0.5f..2f,
            )
            Text("Pause nach Geste: ${settings.cooldownMs} ms", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.cooldownMs.toFloat(),
                onValueChange = { v -> repo.update { it.copy(cooldownMs = ((v / 100).roundToInt() * 100).toLong()) } },
                valueRange = 400f..2500f,
            )
            Text("Bildrate: ${settings.fps} fps (weniger = sparsamer)", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.fps.toFloat(),
                onValueChange = { v -> repo.update { it.copy(fps = v.roundToInt()) } },
                valueRange = 8f..30f,
            )
            Text(
                "Für Doppelblinzeln sind 15 fps oder mehr empfehlenswert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("Links/Rechts tauschen", settings.swapLeftRight) { v -> repo.update { it.copy(swapLeftRight = v) } }
        }

        SectionCard("Kalibrierung") {
            val c = settings.calibration
            if (c == null) {
                Text("Nicht kalibriert – es werden Standardwerte benutzt.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Nicken ab %.0f°, hoch ab %.0f°, Drehen ab %.0f°\nAugen zu ab %.2f, Mund auf ab %.2f".format(
                        c.nodThresholdDeg, c.upThresholdDeg, c.turnThresholdDeg, c.blinkThreshold, c.jawThreshold,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { repo.update { it.copy(calibration = null) } }) {
                    Text("Kalibrierung zurücksetzen")
                }
            }
        }
    }
}

@Composable
private fun GestureMappingRow(gesture: Gesture, action: SwipeAction, onSelect: (SwipeAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(gesture.label, style = MaterialTheme.typography.bodyMedium)
            Text(gesture.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(action.label.substringBefore(" ("), color = if (action == SwipeAction.NONE) MaterialTheme.colorScheme.onSurfaceVariant else Accent)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SwipeAction.entries.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.label) },
                        onClick = {
                            onSelect(a)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
