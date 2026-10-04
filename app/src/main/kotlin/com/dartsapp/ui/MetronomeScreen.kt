package com.dartsapp.ui

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One step in the turn cycle. */
enum class Step(val label: String, val doneLabel: String) {
    READY("Ready", ""),
    /** Learning only: the zero point. Pressing it starts the clock; it is never timed itself. */
    START("Opponent done", "Opponent done — start"),
    APPROACH("Approach oche", "In my stance, darts in hand"),
    DART1("Dart 1", "Thrown"),
    DART2("Dart 2", "Thrown"),
    DART3("Dart 3", "Thrown"),
    REMOVE("Remove darts", "Darts removed"),
    OPPONENT("Opponent throws", "");

    val isDart: Boolean get() = this == DART1 || this == DART2 || this == DART3
}

private val sequence = listOf(Step.APPROACH, Step.DART1, Step.DART2, Step.DART3, Step.REMOVE, Step.OPPONENT)
/** The part of the cycle that is learned from the player. The opponent's time is a constant from the slider. */
private val learnSequence = listOf(Step.START, Step.APPROACH, Step.DART1, Step.DART2, Step.DART3, Step.REMOVE)

/**
 * Timings learned from the player. All three darts share ONE average — the first, second and
 * third dart are never timed differently.
 */
data class Learned(
    val approachSum: Float = 0f, val approachN: Int = 0,
    val dartSum: Float = 0f, val dartN: Int = 0,
    val removeSum: Float = 0f, val removeN: Int = 0,
    val rounds: Int = 0
) {
    val approach: Float get() = if (approachN > 0) approachSum / approachN else 0f
    val dart: Float get() = if (dartN > 0) dartSum / dartN else 0f
    val remove: Float get() = if (removeN > 0) removeSum / removeN else 0f
    val hasData: Boolean get() = dartN > 0 && approachN > 0 && removeN > 0

    fun record(step: Step, seconds: Float): Learned = when {
        step == Step.APPROACH -> copy(approachSum = approachSum + seconds, approachN = approachN + 1)
        step.isDart -> copy(dartSum = dartSum + seconds, dartN = dartN + 1)
        step == Step.REMOVE -> copy(removeSum = removeSum + seconds, removeN = removeN + 1, rounds = rounds + 1)
        else -> this
    }

    /** Learned seconds for a step; the opponent step is not learned and returns null. */
    fun secondsFor(step: Step): Float? = when {
        step == Step.APPROACH -> approach
        step.isDart -> dart
        step == Step.REMOVE -> remove
        else -> null
    }

    fun save(p: SharedPreferences) = p.edit()
        .putFloat("aS", approachSum).putInt("aN", approachN)
        .putFloat("dS", dartSum).putInt("dN", dartN)
        .putFloat("rS", removeSum).putInt("rN", removeN)
        .putInt("rounds", rounds).apply()

    companion object {
        fun load(p: SharedPreferences) = Learned(
            p.getFloat("aS", 0f), p.getInt("aN", 0),
            p.getFloat("dS", 0f), p.getInt("dN", 0),
            p.getFloat("rS", 0f), p.getInt("rN", 0),
            p.getInt("rounds", 0)
        )
    }
}

private enum class Mode { IDLE, PLAYING, LEARNING }

@Composable
fun MetronomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metronome", Context.MODE_PRIVATE) }

    var intervalSec by remember { mutableStateOf(prefs.getFloat("interval", 3f)) }
    var opponentSec by remember { mutableStateOf(prefs.getFloat("opponent", 12f)) }
    var useLearned by remember { mutableStateOf(prefs.getBoolean("useLearned", false)) }
    var learned by remember { mutableStateOf(Learned.load(prefs)) }

    var mode by remember { mutableStateOf(Mode.IDLE) }
    var step by remember { mutableStateOf(Step.READY) }
    var turn by remember { mutableStateOf(0) }
    var stepStartMs by remember { mutableStateOf(0L) }
    var elapsedSec by remember { mutableStateOf(0f) }

    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
    DisposableEffect(Unit) { onDispose { toneGen.release() } }

    fun click(s: Step) {
        when {
            s == Step.APPROACH -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            s.isDart -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            s == Step.REMOVE -> toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            s == Step.OPPONENT -> toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 200)
        }
    }

    val playingLearned = useLearned && learned.hasData

    /** Duration of a step when the metronome drives itself. */
    fun durationSec(s: Step): Float = when {
        s == Step.OPPONENT -> opponentSec
        playingLearned -> (learned.secondsFor(s) ?: intervalSec).coerceAtLeast(0.3f)
        else -> intervalSec
    }

    // Automatic metronome
    LaunchedEffect(mode) {
        if (mode != Mode.PLAYING) return@LaunchedEffect
        turn = 0
        while (mode == Mode.PLAYING) {
            turn++
            for (s in sequence) {
                step = s
                click(s)
                delay((durationSec(s) * 1000).toLong())
            }
        }
    }

    // Learning mode: a live stopwatch for the current step
    LaunchedEffect(mode, step) {
        if (mode != Mode.LEARNING || step == Step.START) return@LaunchedEffect
        while (true) {
            elapsedSec = (System.currentTimeMillis() - stepStartMs) / 1000f
            delay(100)
        }
    }

    fun startLearning() {
        mode = Mode.LEARNING
        turn = 1
        step = Step.START
        elapsedSec = 0f
    }

    fun learningStepDone() {
        val now = System.currentTimeMillis()
        if (step != Step.START) {
            val seconds = (now - stepStartMs) / 1000f
            learned = learned.record(step, seconds).also { it.save(prefs) }
        }
        val idx = learnSequence.indexOf(step)
        val next = if (idx == learnSequence.lastIndex) { turn++; learnSequence.first() } else learnSequence[idx + 1]
        step = next
        stepStartMs = now
        elapsedSec = 0f
        click(next)
    }

    fun stop() {
        mode = Mode.IDLE
        step = Step.READY
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader("Metronome", navController)

        Spacer(Modifier.height(8.dp))

        // Current step
        val isYours = step != Step.READY && step != Step.OPPONENT
        Text(
            step.label.uppercase(),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                step == Step.READY -> Grey
                isYours -> Gold
                else -> Red
            },
            textAlign = TextAlign.Center
        )
        Text(
            when (mode) {
                Mode.IDLE -> if (playingLearned) "Using your learned timing" else "Using the sliders"
                Mode.PLAYING -> "Turn $turn" + if (playingLearned) "  ·  learned timing" else ""
                Mode.LEARNING -> if (step == Step.START) "Learning  ·  round $turn  ·  press when the opponent has finished"
                                 else "Learning  ·  round $turn  ·  ${formatSec(elapsedSec)} s"
            },
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )

        // Learning mode: the big "done" button
        if (mode == Mode.LEARNING) {
            Button(
                onClick = { learningStepDone() },
                modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 12.dp).height(88.dp)
            ) { Text(step.doneLabel, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Play a real turn. Press the button the moment each step is finished. The clock starts at the opponent's last dart.",
                fontSize = 13.sp, color = Grey, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Sequence list
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            for (s in if (mode == Mode.LEARNING) learnSequence else sequence) {
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
                        fontSize = 17.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Gold else Grey,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (s == Step.START) "0 s" else "${formatSec(durationSec(s))} s",
                        fontSize = 15.sp, color = if (active) Gold else Grey
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Learned timing summary + controls
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Charcoal)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your timing", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gold)
                        Text(
                            if (learned.rounds == 0) "Not learned yet — press Learn and play a few turns."
                            else "From ${learned.rounds} round${if (learned.rounds == 1) "" else "s"}:  " +
                                "approach ${formatSec(learned.approach)} s  ·  dart ${formatSec(learned.dart)} s  ·  " +
                                "remove ${formatSec(learned.remove)} s",
                            fontSize = 13.sp, color = Grey
                        )
                    }
                    Switch(
                        checked = useLearned,
                        onCheckedChange = { useLearned = it; prefs.edit().putBoolean("useLearned", it).apply() },
                        enabled = learned.hasData && mode == Mode.IDLE,
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = DarkRed)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (mode == Mode.LEARNING) {
                        Button(onClick = { stop() }) { Text("Finish learning") }
                    } else {
                        OutlinedButton(onClick = { startLearning() }, enabled = mode == Mode.IDLE) { Text("Learn my timing", color = Gold) }
                    }
                    TextButton(
                        onClick = { learned = Learned().also { it.save(prefs) }; useLearned = false },
                        enabled = learned.rounds > 0 && mode == Mode.IDLE
                    ) { Text("Reset", color = Grey) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            if (!playingLearned) {
                Text("Seconds between beats: ${formatSec(intervalSec)} s", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = intervalSec,
                    onValueChange = { intervalSec = (it * 2).roundToInt() / 2f },
                    onValueChangeFinished = { prefs.edit().putFloat("interval", intervalSec).apply() },
                    valueRange = 0.5f..10f,
                    enabled = mode == Mode.IDLE,
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal)
                )
            }
            Text("Opponent's turn: ${formatSec(opponentSec)} s", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = opponentSec,
                onValueChange = { opponentSec = it.roundToInt().toFloat() },
                onValueChangeFinished = { prefs.edit().putFloat("opponent", opponentSec).apply() },
                valueRange = 1f..40f,
                enabled = mode == Mode.IDLE,
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal)
            )
        }

        Spacer(Modifier.height(20.dp))

        if (mode != Mode.LEARNING) {
            Button(
                onClick = { if (mode == Mode.PLAYING) stop() else mode = Mode.PLAYING },
                modifier = Modifier.fillMaxWidth(0.7f).height(64.dp)
            ) { Text(if (mode == Mode.PLAYING) "Stop" else "Start", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun formatSec(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else String.format("%.1f", v)
