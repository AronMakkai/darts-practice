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

/** Window (seconds) around the ideal moment that counts as "on pace". */
internal fun paceWindow(period: Float): Float = minOf(maxOf(0.2f * period, 0.3f), period / 4f)

/**
 * Accuracy from how far the tap was from the ideal moment (the ring back at the centre, [period]
 * seconds after the swipe). Inside the window = 100 %; falls linearly to 20 % at half a period off.
 */
internal fun paceAccuracy(offSec: Float, period: Float): Float {
    val tol = paceWindow(period)
    if (offSec <= tol) return 1f
    val worst = period / 2f
    return (1f - 0.8f * (offSec - tol) / (worst - tol)).coerceIn(0.2f, 1f)
}

/**
 * Dartless checkout: tap the board where you would aim. The accuracy slider adds random
 * scatter to where the dart actually lands, so at low accuracy T20 might become S1 or S5.
 *
 * Metronome mode: pick a timing preset learned in the Metronome screen. Swipe diagonally up from the
 * bottom-left corner to start a throw: a ring grows from the centre of the board to its edge and
 * shrinks back over the preset's dart time. Tap your target — ideally the moment the ring is back at
 * the centre. The closer to that moment, the more accurate the throw; the slider sets itself.
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
    var throwStartMs by remember { mutableStateOf(0L) }   // 0 = no throw armed
    var allOnBeat by remember { mutableStateOf(true) }     // no judged throw off the beat so far this checkout
    var judgedThrows by remember { mutableStateOf(0) }
    var starTrigger by remember { mutableStateOf(0) }
    val presets = remember { TimingPresets.load(context) }
    var presetName by remember { mutableStateOf(TimingPresets.selectedName(context) ?: presets.firstOrNull()?.name) }
    val preset: TimingPreset? = presets.firstOrNull { it.name == presetName }
    var presetMenuOpen by remember { mutableStateOf(false) }

    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    DisposableEffect(Unit) { onDispose { toneGen.release() } }

    // Cue tones for an armed throw: a low tick when the ring reaches the edge, a high beep when it is
    // back at the centre (the ideal moment).
    LaunchedEffect(throwStartMs) {
        val period = preset?.dart ?: return@LaunchedEffect
        if (throwStartMs == 0L) return@LaunchedEffect
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 60)
        val half = (period * 500).toLong()
        delay(half)
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 60)
        delay(half)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
    }

    fun armThrow() {
        if (finished || !metronomeMode || preset == null) return
        throwStartMs = System.currentTimeMillis()
        message = "Tap your target when the ring is back at the centre"
    }

    fun idleMessage() = if (metronomeMode) "Swipe up from the bottom-left corner to start a throw" else "Tap the board to throw"

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
        throwStartMs = 0L
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

        // Metronome mode: a throw must be armed by the swipe. The tap is judged by how far it is from
        // the ideal moment — the ring back at the centre, one dart time after the swipe.
        if (metronomeMode && preset != null) {
            if (throwStartMs == 0L) { message = "Swipe up from the bottom-left corner first"; return }
            val elapsed = (now - throwStartMs) / 1000f
            val off = kotlin.math.abs(elapsed - preset.dart)
            accuracy = paceAccuracy(off, preset.dart)
            val onPace = off <= paceWindow(preset.dart)
            judgedThrows++
            if (!onPace) allOnBeat = false
            timingNote = (if (onPace) "On pace" else if (elapsed < preset.dart) "${formatSec(off)} s early" else "${formatSec(off)} s late") +
                "  →  accuracy ${(accuracy * 100).toInt()}%"
            throwStartMs = 0L
        }

        val target = Board.hitTest(aim.x, aim.y, BoardGeo)
        val (lx, ly) = model.land(aim.x, aim.y, accuracy)
        val hit = Board.hitTest(lx, ly, BoardGeo)
        marks.add(Offset(lx, ly))
        thrown.add(hit)
        dartsInVisit++
        dartsTotal++

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
        if (metronomeMode && !finished && dartsInVisit < 3) message += "  ·  Swipe for the next dart"
    }

    val tip = remember(remaining) { if (remaining > 1) CheckoutLogic.tip(remaining) else "" }
    val dartNo = if (dartsInVisit >= 3) 3 else dartsInVisit

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = if (metronomeMode) 130.dp else 16.dp)
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
                        throwStartMs = 0L
                        timingNote = ""
                        if (!finished) message = idleMessage()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = DarkRed)
                )
            }
            Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Checkout $start", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(remaining.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Gold)
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

        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Dartboard(
                geometry = BoardGeo,
                marks = marks,
                onTap = { throwAt(it) }
            )
            if (metronomeMode) {
                ThrowRing(
                    startMs = if (finished) 0L else throwStartMs,
                    periodSec = preset?.dart ?: 0f,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            }
        }

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
        SwipeToThrowZone(
            armed = throwStartMs != 0L,
            enabled = !finished && preset != null,
            onSwipe = { armThrow() },
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(120.dp)
        )
    }
    StarRain(trigger = starTrigger, modifier = Modifier.fillMaxSize())
    }
}
