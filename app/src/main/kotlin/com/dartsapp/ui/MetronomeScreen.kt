package com.dartsapp.ui

import android.content.Context
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
import com.dartsapp.logic.Sounds
import com.dartsapp.logic.TimingPreset
import com.dartsapp.logic.TimingPresets
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What kind of action a step is. The three dart steps share one timing. */
enum class Kind { NONE, START, APPROACH, DART, REMOVE, OPPONENT }

/** One step in the turn cycle. */
enum class Step(val label: String, val doneLabel: String, val kind: Kind, val dart: Int = 0) {
    READY("Ready", "", Kind.NONE),
    /** Learning only: the zero point. Pressing it starts the clock; it is never timed itself. */
    START("Opponent done", "Opponent done — start", Kind.START),
    APPROACH("Approaching oche", "In my stance, darts in hand", Kind.APPROACH),
    DART1("Dart 1", "Thrown", Kind.DART, 1),
    DART2("Dart 2", "Thrown", Kind.DART, 2),
    DART3("Dart 3", "Thrown", Kind.DART, 3),
    REMOVE("Clearing board and oche", "Board and oche cleared", Kind.REMOVE),
    OPPONENT("Opponent throws", "", Kind.OPPONENT);

    val isDartStep: Boolean get() = kind == Kind.DART
}

/** The playback cycle. */
private val sequence = listOf(Step.APPROACH, Step.DART1, Step.DART2, Step.DART3, Step.REMOVE, Step.OPPONENT)
/** The part that is learned from the player: the zero point plus everything except the opponent. */
private val learnSequence = listOf(Step.START) + sequence.dropLast(1)

/** Running totals for one learning session. The three darts are pooled into one dart time. */
private data class Learning(
    val sums: Map<Kind, Float> = emptyMap(),
    val counts: Map<Kind, Int> = emptyMap(),
    val rounds: Int = 0
) {
    fun avg(k: Kind): Float = counts[k]?.takeIf { it > 0 }?.let { sums.getValue(k) / it } ?: 0f
    val complete: Boolean get() = rounds > 0

    fun record(step: Step, seconds: Float): Learning {
        val k = step.kind
        if (k == Kind.NONE || k == Kind.START || k == Kind.OPPONENT) return this
        return copy(
            sums = sums + (k to (sums[k] ?: 0f) + seconds),
            counts = counts + (k to (counts[k] ?: 0) + 1),
            rounds = if (k == Kind.REMOVE) rounds + 1 else rounds
        )
    }

    fun toPreset(name: String) =
        TimingPreset(name, avg(Kind.APPROACH), avg(Kind.DART), avg(Kind.REMOVE), rounds)
}

private enum class Mode { IDLE, PLAYING, LEARNING }

/** Sound for the moment a step is COMPLETED (in stance, released, cleared, opponent done...). */
internal fun playStepDoneTone(done: Step) {
    when (done.kind) {
        Kind.START, Kind.OPPONENT -> Sounds.buzz()
        Kind.APPROACH -> Sounds.tick()
        Kind.DART -> Sounds.beep()
        Kind.REMOVE -> Sounds.tick()
        else -> {}
    }
}

/** Seconds a step takes for a preset, or from the manual beat interval when there is no preset. */
internal fun stepSeconds(kind: Kind, preset: TimingPreset?, interval: Float, opponent: Float): Float = when (kind) {
    Kind.OPPONENT -> opponent
    Kind.APPROACH -> preset?.approach ?: interval
    Kind.DART -> preset?.dart ?: interval
    Kind.REMOVE -> preset?.remove ?: interval
    else -> 0f
}.coerceAtLeast(0.25f)

@Composable
fun MetronomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metronome", Context.MODE_PRIVATE) }

    var intervalSec by remember { mutableStateOf(prefs.getFloat("interval", 3f)) }
    var opponentSec by remember { mutableStateOf(prefs.getFloat("opponent", 12f)) }

    var presets by remember { mutableStateOf(TimingPresets.load(context)) }
    var selectedName by remember { mutableStateOf(TimingPresets.selectedName(context)) }
    val preset: TimingPreset? = presets.firstOrNull { it.name == selectedName }

    var mode by remember { mutableStateOf(Mode.IDLE) }
    var step by remember { mutableStateOf(Step.READY) }
    var turn by remember { mutableStateOf(0) }
    var stepStartMs by remember { mutableStateOf(0L) }
    var elapsedSec by remember { mutableStateOf(0f) }
    var learning by remember { mutableStateOf(Learning()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetMenuOpen by remember { mutableStateOf(false) }

    // Show the info box automatically the first time the screen is opened.
    var showInfo by remember { mutableStateOf(!prefs.getBoolean("infoSeen", false)) }
    fun dismissInfo() {
        showInfo = false
        prefs.edit().putBoolean("infoSeen", true).apply()
    }


    fun clickDone(done: Step) = playStepDoneTone(done)

    fun durationSec(s: Step): Float = stepSeconds(s.kind, preset, intervalSec, opponentSec)

    // Automatic metronome
    LaunchedEffect(mode) {
        if (mode != Mode.PLAYING) return@LaunchedEffect
        turn = 0
        while (mode == Mode.PLAYING) {
            turn++
            for ((i, s) in sequence.withIndex()) {
                step = s
                clickDone(if (i == 0) Step.OPPONENT else sequence[i - 1])
                delay((durationSec(s) * 1000).toLong())
            }
        }
    }

    // Learning mode: a live stopwatch once the clock has started
    LaunchedEffect(mode, step) {
        if (mode != Mode.LEARNING || step == Step.START) return@LaunchedEffect
        while (true) {
            elapsedSec = (System.currentTimeMillis() - stepStartMs) / 1000f
            delay(100)
        }
    }

    fun startLearning() {
        learning = Learning()
        mode = Mode.LEARNING
        turn = 1
        step = Step.START
        elapsedSec = 0f
    }

    fun learningStepDone() {
        val now = System.currentTimeMillis()
        if (step != Step.START) {
            learning = learning.record(step, (now - stepStartMs) / 1000f)
        }
        clickDone(step)
        val idx = learnSequence.indexOf(step)
        val next = if (idx == learnSequence.lastIndex) { turn++; learnSequence.first() } else learnSequence[idx + 1]
        step = next
        stepStartMs = now
        elapsedSec = 0f
    }

    fun stop() {
        mode = Mode.IDLE
        step = Step.READY
    }

    fun finishLearning() {
        if (learning.complete) showSaveDialog = true else stop()
    }

    /** Zero out the averages but keep learning from the zero point. */
    fun resetLearning() {
        learning = Learning()
        turn = 1
        step = Step.START
        elapsedSec = 0f
    }

    fun selectPreset(name: String?) {
        selectedName = name
        TimingPresets.setSelected(context, name)
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { dismissInfo() },
            title = { Text("Routine and rhythm") },
            text = {
                Text(
                    "Darts is all about routine and rhythm. Either video yourself playing and set the timing from " +
                        "that video, or ask a friend to time you while you are playing, and save that.\n\n" +
                        "Once saved, you can use the metronome when you practice, or use it in the Checkout Game.\n\n" +
                        "Learn my timing walks you through a full turn: press the button the moment each step is done " +
                        "(in your stance, each dart thrown, board cleared), and the averages build up round by round. " +
                        "The three darts always share one time.",
                    fontSize = 15.sp
                )
            },
            confirmButton = { Button(onClick = { dismissInfo() }) { Text("Got it") } }
        )
    }

    if (showSaveDialog) {
        SavePresetDialog(
            defaultName = "Preset ${presets.count { !it.builtIn } + 1}",
            summary = learning.toPreset("").summary(),
            onSave = { name ->
                presets = TimingPresets.add(context, learning.toPreset(name))
                selectPreset(TimingPresets.sanitize(name))
                showSaveDialog = false
                stop()
            },
            onDiscard = { showSaveDialog = false; stop() }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader("Metronome", navController) {
            TextButton(onClick = { showInfo = true }) { Text("Info", color = Gold) }
        }

        // Stick figure
        StickFigure(step = step, modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp))

        // Current step
        val isYours = step.kind != Kind.NONE && step.kind != Kind.OPPONENT
        Text(
            step.label.uppercase(),
            fontSize = 30.sp,
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
                Mode.IDLE -> if (preset != null) "Timing: ${preset.name}" else "Timing: sliders"
                Mode.PLAYING -> "Turn $turn" + if (preset != null) "  ·  ${preset.name}" else ""
                Mode.LEARNING -> {
                    if (step == Step.START) "Learning  ·  round $turn  ·  press when the opponent has finished"
                    else "Learning  ·  round $turn  ·  ${formatSec(elapsedSec)} s"
                }
            },
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        // Learning mode: the big "done" button
        if (mode == Mode.LEARNING) {
            Button(
                onClick = { learningStepDone() },
                modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 10.dp).height(84.dp)
            ) { Text(step.doneLabel, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
            Text(
                "Play a real turn. Press the button the moment each step is finished. The clock starts at the opponent's last dart.",
                fontSize = 13.sp, color = Grey, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Sequence list
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            for (s in if (mode == Mode.LEARNING) learnSequence else sequence) {
                val active = s == step
                val sub = false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .background(
                            if (active) (if (s.kind == Kind.OPPONENT) DarkRed else Charcoal) else Black,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(start = if (sub) 28.dp else 14.dp, end = 14.dp, top = if (sub) 4.dp else 7.dp, bottom = if (sub) 4.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.label,
                        fontSize = if (sub) 14.sp else 16.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Gold else Grey,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        when {
                            s == Step.START -> "0 s"
                            mode == Mode.LEARNING -> {
                                val n = learning.counts[s.kind] ?: 0
                                if (n == 0) "—" else "${formatSec(learning.avg(s.kind))} s  (${n})"
                            }
                            else -> "${formatSec(durationSec(s))} s"
                        },
                        fontSize = if (sub) 13.sp else 15.sp, color = if (active) Gold else Grey
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Timing presets card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Charcoal)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Timing preset", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gold)

                Box {
                    OutlinedButton(
                        onClick = { presetMenuOpen = true },
                        enabled = mode == Mode.IDLE,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) { Text(preset?.name ?: "Sliders (manual)", color = OffWhite) }
                    DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Sliders (manual)") }, onClick = { selectPreset(null); presetMenuOpen = false })
                        for (p in presets) {
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { selectPreset(p.name); presetMenuOpen = false })
                        }
                    }
                }

                Text(
                    if (mode == Mode.LEARNING) {
                        if (learning.counts.isEmpty()) "Averages update after every press."
                        else "So far: " + learning.toPreset("").summary()
                    } else preset?.summary()
                        ?: if (presets.isEmpty()) "No presets yet — press Learn my timing and play a few turns." else "Using the sliders below.",
                    fontSize = 13.sp, color = if (mode == Mode.LEARNING) PaleGold else Grey, modifier = Modifier.padding(top = 6.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (mode == Mode.LEARNING) {
                        Button(onClick = { finishLearning() }) { Text(if (learning.complete) "Finish & save" else "Cancel") }
                        TextButton(onClick = { resetLearning() }, enabled = learning.counts.isNotEmpty()) { Text("Reset averages", color = Grey) }
                    } else {
                        OutlinedButton(onClick = { startLearning() }, enabled = mode == Mode.IDLE) { Text("Learn my timing", color = Gold) }
                    }
                    if (preset != null && !preset.builtIn && mode == Mode.IDLE) {
                        TextButton(onClick = {
                            presets = TimingPresets.delete(context, preset.name)
                            selectPreset(null)
                        }) { Text("Delete", color = Grey) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            if (preset == null) {
                Text("Seconds per dart: ${formatSec(intervalSec)} s", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = intervalSec,
                    onValueChange = { intervalSec = (it * 2).roundToInt() / 2f },
                    onValueChangeFinished = { prefs.edit().putFloat("interval", intervalSec).apply() },
                    valueRange = 1f..12f,
                    enabled = mode == Mode.IDLE,
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal)
                )
            }
            Text("Opponent's turn: ${formatSec(opponentSec)} s", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun SavePresetDialog(defaultName: String, summary: String, onSave: (String) -> Unit, onDiscard: () -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDiscard,
        title = { Text("Save timing preset") },
        text = {
            Column {
                Text(summary, fontSize = 13.sp, color = Grey, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    singleLine = true
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Discard", color = Grey) } }
    )
}

internal fun formatSec(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else String.format("%.1f", v)
