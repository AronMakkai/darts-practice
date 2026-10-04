package com.dartsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs

private val Green = Color(0xFF1B9A3C)
private val BoardGeo = BoardGeometry.PRACTICE

/**
 * Accuracy from how close [measured] seconds is to the preset's [target].
 * Within 10 % of the target = 100 %. Every further 10 % off costs 15 %. Never below 5 %.
 */
internal fun timingAccuracy(measured: Float, target: Float): Float {
    if (target <= 0f) return 1f
    val error = abs(measured - target) / target
    return (1f - (error - 0.10f).coerceAtLeast(0f) * 1.5f).coerceIn(0.05f, 1f)
}

/**
 * Dartless checkout: tap the board where you would aim. The accuracy slider adds random
 * scatter to where the dart actually lands, so at low accuracy T20 might become S1 or S5.
 *
 * Metronome mode: pick a timing preset learned in the Metronome screen. Before every dart press
 * "Grab darts" then "Aim", then tap the board. The closer your rhythm is to the preset's dart
 * time, the more accurate the throw — the accuracy slider sets itself.
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
    var grabbed by remember { mutableStateOf(false) }
    var aimed by remember { mutableStateOf(false) }
    var grabbedAtMs by remember { mutableStateOf(0L) }
    var lastThrowMs by remember { mutableStateOf(0L) }
    val presets = remember { TimingPresets.load(context) }
    var presetName by remember { mutableStateOf(TimingPresets.selectedName(context) ?: presets.firstOrNull()?.name) }
    val preset: TimingPreset? = presets.firstOrNull { it.name == presetName }
    var presetMenuOpen by remember { mutableStateOf(false) }

    fun idleMessage() = if (metronomeMode) "Grab your darts" else "Tap the board to throw"

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
        grabbed = false
        aimed = false
        lastThrowMs = 0L
    }

    fun throwAt(aim: Offset) {
        if (finished) return
        if (metronomeMode) {
            if (preset == null) { message = "Pick a timing preset first"; return }
            if (!grabbed) { message = "Grab your darts first"; return }
            if (!aimed) { message = "Aim first"; return }
        }
        val now = System.currentTimeMillis()
        val newVisit = dartsInVisit >= 3
        if (newVisit) {
            dartsInVisit = 0
            visitStart = remaining
            marks.clear()
            thrown.clear()
        }

        // Metronome mode: accuracy comes from your rhythm, not the slider.
        if (metronomeMode && preset != null) {
            val firstDart = dartsInVisit == 0 || lastThrowMs == 0L
            val measured = (now - (if (firstDart) grabbedAtMs else lastThrowMs)) / 1000f
            accuracy = timingAccuracy(measured, preset.dart)
            timingNote = "Timing ${formatSec(measured)} s vs ${formatSec(preset.dart)} s  →  accuracy ${(accuracy * 100).toInt()}%"
        }

        val target = Board.hitTest(aim.x, aim.y, BoardGeo)
        val (lx, ly) = model.land(aim.x, aim.y, accuracy)
        val hit = Board.hitTest(lx, ly, BoardGeo)
        marks.add(Offset(lx, ly))
        thrown.add(hit)
        dartsInVisit++
        dartsTotal++
        lastThrowMs = now
        aimed = false
        grabbed = false

        val hitText = if (target == hit) "Hit ${hit.label} (${hit.score})" else "Aimed ${target.label}, hit ${hit.label} (${hit.score})"
        val newRem = remaining - hit.score
        when {
            newRem == 0 && hit.isDoubleOut -> {
                remaining = 0
                finished = true
                message = "$hitText — Checked out in $dartsTotal darts!"
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
        if (dartsInVisit >= 3) lastThrowMs = 0L
    }

    val tip = remember(remaining) { if (remaining > 1) CheckoutLogic.tip(remaining) else "" }
    val dartNo = if (dartsInVisit >= 3) 3 else dartsInVisit

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (metronomeMode) 96.dp else 16.dp)
        ) {
            ScreenHeader("Dartless Checkout", navController) {
                TextButton(onClick = { newCheckout() }) { Text("New", color = Gold) }
            }

            RemainingDisplay(start, remaining, tip, message)

            Text(
                "Dart $dartNo/3   Visit: " + thrown.joinToString(" ") { it.label }.ifEmpty { "—" },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
            )
            if (metronomeMode && timingNote.isNotEmpty()) {
                Text(
                    timingNote, fontSize = 13.sp, color = PaleGold,
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

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Metronome mode", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Grab darts, aim, throw — your rhythm sets the accuracy", fontSize = 12.sp, color = Grey)
                    }
                    Switch(
                        checked = metronomeMode,
                        onCheckedChange = {
                            metronomeMode = it
                            grabbed = false
                            aimed = false
                            lastThrowMs = 0L
                            timingNote = ""
                            if (!finished) message = idleMessage()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = DarkRed)
                    )
                }

                if (metronomeMode) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { presetMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(preset?.name ?: "Choose a timing preset", color = if (preset != null) OffWhite else Gold)
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
                    Text(
                        preset?.let { "Target: ${formatSec(it.dart)} s per dart (from Grab darts to the throw for dart 1, then dart to dart)" }
                            ?: "Learn a preset in the Metronome screen first.",
                        fontSize = 12.sp, color = Grey, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (finished) {
                Button(onClick = { newCheckout() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) { Text("Next checkout") }
            }
        }

        // Metronome-mode action buttons pinned to the bottom of the screen.
        // Gold = waiting to be pressed, green = done. Both reset after every throw.
        if (metronomeMode) {
            Button(
                onClick = {
                    if (!grabbed) {
                        grabbed = true
                        grabbedAtMs = System.currentTimeMillis()
                        message = "Aim"
                    }
                },
                enabled = !finished,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (grabbed) Green else Gold,
                    contentColor = if (grabbed) OffWhite else Black
                ),
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).height(64.dp)
            ) { Text("Grab darts", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = {
                    when {
                        !grabbed -> message = "Grab your darts first"
                        !aimed -> { aimed = true; message = "Throw — tap the board" }
                    }
                },
                enabled = !finished,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (aimed) Green else Gold,
                    contentColor = if (aimed) OffWhite else Black
                ),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).height(64.dp).width(120.dp)
            ) { Text("Aim", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
