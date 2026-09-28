package app.gestenswipe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gestenswipe.engine.EngineState
import kotlinx.coroutines.delay

private val palettes = listOf(
    Color(0xFFFF3B6B) to Color(0xFF7C5CFF),
    Color(0xFF00C2FF) to Color(0xFF0057FF),
    Color(0xFFFFB84D) to Color(0xFFFF4D4D),
    Color(0xFF3DDC97) to Color(0xFF0B7A75),
    Color(0xFFB45CFF) to Color(0xFF2A0E61),
)

/**
 * Fake-Videofeed zum Ausprobieren: verhält sich wie TikTok (hoch/runter swipen,
 * Tipp = Pause, Doppeltipp = Like). Die Gesten kommen über den Accessibility-Service
 * genauso an wie in den echten Apps.
 */
@Composable
fun TestScreen(onBack: () -> Unit) {
    val pager = rememberPagerState { 100 }
    val liked = remember { mutableStateMapOf<Int, Boolean>() }
    var paused by remember { mutableStateOf(false) }
    var heartBurst by remember { mutableIntStateOf(0) }
    val running by EngineState.running.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val (a, b) = palettes[page % palettes.size]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(a, b)))
                    .pointerInput(page) {
                        detectTapGestures(
                            onTap = { paused = !paused },
                            onDoubleTap = {
                                liked[page] = true
                                heartBurst++
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Video #${page + 1}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Nicke, um weiterzuswipen", color = Color.White.copy(alpha = 0.8f))
                }
                Icon(
                    if (liked[page] == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (liked[page] == true) Color(0xFFFF2D55) else Color.White,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(40.dp),
                )
            }
        }

        AnimatedVisibility(visible = paused, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Pausiert", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(96.dp))
        }

        HeartBurst(heartBurst, Modifier.align(Alignment.Center))

        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Color.White)
            }
            if (!running) {
                Text(
                    "Gestensteuerung ist aus – starte sie auf der Startseite.",
                    color = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small).padding(8.dp),
                )
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()) {
            Box(Modifier.background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.medium)) {
                LastGestureBanner()
            }
        }
    }
}

@Composable
private fun HeartBurst(trigger: Int, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        delay(700)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = scaleIn() + fadeIn(), exit = fadeOut(), modifier = modifier) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF2D55), modifier = Modifier.size(140.dp))
    }
}
