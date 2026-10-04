package com.dartsapp.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import com.dartsapp.data.Hit
import com.dartsapp.logic.AccuracyModel
import com.dartsapp.logic.CheckoutLogic
import com.dartsapp.logic.TimingPreset
import com.dartsapp.logic.TimingPresets
import kotlinx.coroutines.delay

private val BoardGeo = BoardGeometry.PRACTICE

/** Seconds from [sinceAnchor] to the nearest beat of a [period]-second beat. */
internal fun beatDistance(sinceAnchor: Float, period: Float): Float {
    val phase = ((sinceAnchor / period) % 1f + 1f) % 1f
    return minOf(phase, 1f - phase) * period
}

/** Window (seconds) around a beat that counts as "on the beat". */
internal fun beatWindow(period: Float): Float = minOf(maxOf(0.2f * period, 0.3f), period / 4f)

/**
 * Accuracy from how far a throw is from the nearest beat. Inside the window = 100 %; from there it
 * falls linearly to 20 % halfway between beats. Missing a beat entirely costs nothing extra: wait
 * for the next swing and throw on that one.
 */
internal fun beatAccuracy(distance: Float, period: Float): Float {
    val tol = beatWindow(period)
    if (distance <= tol) return 1f
    val worst = period / 2f
    return (1f - 0.8f * (distance - tol) / (worst - tol)).coerceIn(0.2f, 1f)
}

/**
 * Dartless checkout: tap the board where you would aim. The accuracy slider adds random
 * scatter to where the dart actually lands, so at low accuracy T20 might become S1 or S5.
 *
 * Metronome mode: pick a timing preset learned in the Metronome screen. The first dart of a visit
 * starts the beat; darts 2 and 3 are judged by the interval since the previous throw against the
 * preset's dart time. The closer your rhythm, the more accurate the throw — the slider sets itself.
 */
@Composable
fun DartlessScreen(navController: NavHostController) {
    val context = LocalContext.current

    var start by remember { mutableStateOf(CheckoutLogic.randomCheckout()) }
    var remaining by remember { mutableStateOf(start) }
    var visitStart by remember { mutableStateOf(start) }
    var dartsInVisit by remember { mutableStateOf(0) }
    var dartsTotal by remember { mutableStateOf(0) }
    var accuracy by remember { mutableStateOf(0.8f) }
    var message by remember { mutableStateOf("Tap the board to throw") }
    var timingNote by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val marks = remember { mutableStateListOf<Offset>() }
    val thrown = remember { mutableStateListOf<Hit>() }
    val model = remember { AccuracyModel() }

    // Metronome mode
    var metronomeMode by remember { mutableStateOf(false) }
    var lastThrowMs by remember { mutableStateOf(0L) }
    var beatAnchorMs by remember { mutableStateOf(0L) }   // 0 = beat not running
    var pulse by remember { mutableStateOf(false) }
    var allOnBeat by remember { mutableStateOf(true) }     // no judged throw off the beat so far this checkout
    var judgedThrows by remember { mutableStateOf(0) }
    var starTrigger by remember { mutableStateOf(0) }
    val presets = remember { TimingPresets.load(context) }
    var presetName by remember { mutableStateOf(TimingPresets.selectedName(context) ?: presets.firstOrNull()?.name) }
    val preset: TimingPreset? = presets.firstOrNull { it.name == presetName }
    var presetMenuOpen by remember { mutableStateOf(false) }

    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    DisposableEffect(Unit) { onDispose { toneGen.release() } }

    // Beat: starts on the first throw of a visit and clicks once per preset dart time — the clicks
    // are where the next throws should land. Aligned to the anchor so it never drifts.
    val beatSec = if (metronomeMode && !finished && beatAnchorMs > 0L) preset?.dart else null
    LaunchedEffect(beatSec, beatAnchorMs) {
        if (beatSec == null) { pulse = false; return@LaunchedEffect }
        val periodMs = (beatSec.coerceAtLeast(0.3f) * 1000).toLong()
        var k = 0L
        pulse = true; delay(120); pulse = false      // the anchoring throw is beat zero
        while (true) {
            k++
            val wait = beatAnchorMs + k * periodMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
            pulse = true
            delay(120)
            pulse = false
        }
    }

    fun idleMessage() = if (metronomeMode) "Throw dart 1 to start the pendulum, then throw as it passes upright" else "Tap the board to throw"

    fun newCheckout() {
        start = CheckoutLogic.randomCheckout()
        remaining = start
        visitStart = start
        dartsInVisit = 0
        dartsTotal = 0
        finished = false
        message = idleMessage()
        timingNote = ""
        marks.clear()
        thrown.clear()
        lastThrowMs = 0L
        beatAnchorMs = 0L
        allOnBeat = true
        judgedThrows = 0
    }

    fun throwAt(aim: Offset) {
        if (finished) return
        if (metronomeMode && preset == null) { message = "Pick a timing preset first (top right)"; return }
        val now = System.currentTimeMillis()
        if (dartsInVisit >= 3) {
            dartsInVisit = 0
            visitStart = remaining
            marks.clear()
            thrown.clear()
        }

        // Metronome mode: darts 2 and 3 are judged on how close they are to the nearest beat.
        // You may let a beat pass and throw on the next swing — only the distance to a beat counts.
        if (metronomeMode && preset != null) {
            if (dartsInVisit == 0 || beatAnchorMs == 0L) {
                beatAnchorMs = now
                timingNote = "Dart 1 sets the beat — throw as the pendulum passes upright"
            } else {
                val dist = beatDistance((now - beatAnchorMs) / 1000f, preset.dart)
                accuracy = beatAccuracy(dist, preset.dart)
                val onBeat = dist <= beatWindow(preset.dart)
                judgedThrows++
                if (!onBeat) allOnBeat = false
                timingNote = (if (onBeat) "On the beat" else "${formatSec(dist)} s off the beat") + "  →  accuracy ${(accuracy * 100).toInt()}%"
            }
        }

        val target = Board.hitTest(aim.x, aim.y, BoardGeo)
        val (lx, ly) = model.land(aim.x, aim.y, accuracy)
        val hit = Board.hitTest(lx, ly, BoardGeo)
        marks.add(Offset(lx, ly))
        thrown.add(hit)
        dartsInVisit++
        dartsTotal++
        lastThrowMs = now

        val hitText = if (target == hit) "Hit ${hit.label} (${hit.score})" else "Aimed ${target.label}, hit ${hit.label} (${hit.score})"
        val newRem = remaining - hit.score
        when {
            newRem == 0 && hit.isDoubleOut -> {
                remaining = 0
                finished = true
                message = "$hitText — Checked out in $dartsTotal darts!"
                if (metronomeMode && allOnBeat && judgedThrows > 0) {
                    message += "  Never missed a beat!"
                    starTrigger++
                }
            }
            newRem < 0 || newRem == 1 || newRem == 0 -> {
                remaining = visitStart
                dartsInVisit = 3
                message = "$hitText — Bust! Back to $visitStart"
            }
            else -> {
                remaining = newRem
                message = hitText
            }
        }
        if (dartsInVisit >= 3 || finished) {
            lastThrowMs = 0L
            beatAnchorMs = 0L
        }
    }

    val tip = remember(remaining) { if (remaining > 1) CheckoutLogic.tip(remaining) else "" }
    val dartNo = if (dartsInVisit >= 3) 3 else dartsInVisit

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = if (metronomeMode) 110.dp else 16.dp)
    ) {
        ScreenHeader("Dartless Checkout", navController) {
            TextButton(onClick = { newCheckout() }) { Text("New", color = Gold) }
        }

        // Metronome toggle (left) · remaining (centre) · preset picker (right)
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Metronome", fontSize = 12.sp, color = Grey)
                Switch(
                    checked = metronomeMode,
                    onCheckedChange = {
                        metronomeMode = it
                        lastThrowMs = 0L
                        beatAnchorMs = 0L
                        timingNote = ""
                        if (!finished) message = idleMessage()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = DarkRed)
                )
            }
            Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Checkout $start", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(remaining.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if (pulse) BrightGold else Gold)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Preset", fontSize = 12.sp, color = Grey)
                    OutlinedButton(
                        onClick = { presetMenuOpen = true },
                        enabled = metronomeMode,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            preset?.name ?: "Choose",
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = if (metronomeMode) (if (preset != null) OffWhite else Gold) else Grey
                        )
                    }
                }
                DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                    if (presets.isEmpty()) {
                        DropdownMenuItem(text = { Text("No presets — learn one in Metronome") }, onClick = { presetMenuOpen = false })
                    }
                    for (p in presets) {
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = { presetName = p.name; TimingPresets.setSelected(context, p.name); presetMenuOpen = false }
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (tip.isNotEmpty()) Text(tip, fontSize = 18.sp, color = PaleGold, textAlign = TextAlign.Center)
            if (message.isNotEmpty()) Text(message, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
        }

        Text(
            "Dart $dartNo/3   Visit: " + thrown.joinToString(" ") { it.label }.ifEmpty { "—" },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
        )
        if (metronomeMode) {
            Text(
                timingNote.ifEmpty { " " },
                fontSize = 13.sp,
                color = PaleGold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
            )
        }

        Dartboard(
            modifier = Modifier.padding(8.dp),
            geometry = BoardGeo,
            marks = marks,
            onTap = { throwAt(it) }
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "Accuracy: ${(accuracy * 100).toInt()}%" + if (metronomeMode) "  (set by your timing)" else "",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = accuracy, onValueChange = { accuracy = it }, valueRange = 0f..1f,
                enabled = !metronomeMode,
                colors = SliderDefaults.colors(
                    thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal,
                    disabledThumbColor = Gold, disabledActiveTrackColor = DarkRed, disabledInactiveTrackColor = Charcoal
                )
            )
        }

        if (finished) {
            Button(onClick = { newCheckout() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) { Text("Next checkout") }
        }
    }

    if (metronomeMode) {
        MetronomePendulum(
            anchorMs = if (finished) 0L else beatAnchorMs,
            periodSec = preset?.dart ?: 0f,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(96.dp)
        )
    }
    StarRain(trigger = starTrigger, modifier = Modifier.fillMaxSize())
    }
}
