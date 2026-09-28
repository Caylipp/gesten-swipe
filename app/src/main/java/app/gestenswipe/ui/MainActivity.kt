package app.gestenswipe.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.gestenswipe.GestenSwipeApp

enum class Screen { HOME, SETTINGS, CALIBRATION, TEST }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as GestenSwipeApp).settings
        setContent {
            GestenSwipeTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                val back = { screen = Screen.HOME }
                when (screen) {
                    Screen.HOME -> HomeScreen(repo, onNavigate = { screen = it })
                    Screen.SETTINGS -> SettingsScreen(repo, onBack = back)
                    Screen.CALIBRATION -> CalibrationScreen(repo, onBack = back)
                    Screen.TEST -> TestScreen(onBack = back)
                }
            }
        }
    }
}

val Accent = Color(0xFFFF3B6B)
val Ok = Color(0xFF3DDC97)
val Warn = Color(0xFFFFB84D)

@Composable
fun GestenSwipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Color(0xFF7C5CFF),
            background = Color(0xFF0E0A1A),
            surface = Color(0xFF0E0A1A),
            surfaceVariant = Color(0xFF1E1633),
            surfaceContainer = Color(0xFF1B1430),
        ),
        content = content,
    )
}
