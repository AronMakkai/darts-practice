package com.dartsapp.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One step in the turn cycle. */
enum class Step(val label: String) {
    READY("Ready"),
    APPROACH("Approach oche"),
    DART1("Dart 1"),
    DART2("Dart 2"),
    DART3("Dart 3"),
    REMOVE("Remove darts"),
    OPPONENT("Opponent throws")
}

/** Steps shown in the sequence list (everything except READY). */
private val sequence = listOf(Step.APPROACH, Step.DART1, Step.DART2, Step.DART3, Step.REMOVE, Step.OPPONENT)

/**
 * Metronome: simulates playing against an opponent. Each cycle is
 * Approach oche -> Dart 1 -> Dart 2 -> Dart 3 -> Remove darts -> Opponent throws -> repeat.
 * Every step lasts [intervalSec] seconds except the opponent's turn, which has its own length.
 * Each step is announced with a click; darts get the sharp beep, the others a softer tone.
 */
@Composable
fun MetronomeScreen(navController: NavHostController) {
    var intervalSec by remember { mutableStateOf(3f) }
    var opponentSec by remember { mutableStateOf(12f) }
    var running by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(Step.READY) }
    var turn by remember { mutableStateOf(0) }

    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
    DisposableEffect(Unit) { onDispose { toneGen.release() } }

    LaunchedEffect(running) {
        if (!running) { step = Step.READY; return@LaunchedEffect }
        turn = 0
        val stepMs = { (intervalSec * 1000).toLong() }
        while (running) {
            turn++
            step = Step.APPROACH
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            delay(stepMs())

            for (s in listOf(Step.DART1, Step.DART2, Step.DART3)) {
                step = s
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                delay(stepMs())
            }

            step = Step.REMOVE
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            delay(stepMs())

            step = Step.OPPONENT
            toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            delay((opponentSec * 1000).toLong())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader("Metronome", navController)

        Spacer(Modifier.height(12.dp))

        // Current step, big
        val isYours = step in setOf(Step.APPROACH, Step.DART1, Step.DART2, Step.DART3, Step.REMOVE)
        Text(
            step.label.uppercase(),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                step == Step.READY -> Grey
                isYours -> Gold
                else -> Red
            },
            textAlign = TextAlign.Center
        )
        Text(
            if (running) "Turn $turn" else "Press Start",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Sequence list with the active step highlighted
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            for (s in sequence) {
                val active = s == step
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            if (active) (if (s == Step.OPPONENT) DarkRed else Charcoal) else Black,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.label,
                        fontSize = 18.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Gold else Grey,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (s == Step.OPPONENT) "${formatSec(opponentSec)} s" else "${formatSec(intervalSec)} s",
                        fontSize = 16.sp,
                        color = if (active) Gold else Grey
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("Seconds between beats: ${formatSec(intervalSec)} s", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = intervalSec,
                onValueChange = { intervalSec = (it * 2).roundToInt() / 2f },
                valueRange = 0.5f..10f,
                enabled = !running,
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal)
            )
            Text("Opponent's turn: ${formatSec(opponentSec)} s", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = opponentSec,
                onValueChange = { opponentSec = it.roundToInt().toFloat() },
                valueRange = 1f..40f,
                enabled = !running,
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal)
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { running = !running },
            modifier = Modifier.fillMaxWidth(0.7f).height(64.dp)
        ) { Text(if (running) "Stop" else "Start", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun formatSec(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else String.format("%.1f", v)
