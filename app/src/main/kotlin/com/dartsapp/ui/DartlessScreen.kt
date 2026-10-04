package com.dartsapp.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import kotlin.math.abs

private val Green = Color(0xFF1B9A3C)
private val BrightGreen = Color(0xFF4FD672)
private val BrightGold = Color(0xFFFFE27A)
private val BoardGeo = BoardGeometry.PRACTICE

/**
 * Accuracy from how close [measured] seconds is to the preset's [target].
 * A generous window counts as perfect: within 25 % of the target (or half a second, whichever
 * is larger) = 100 %. Beyond that, accuracy falls off linearly — 50 % off the target is still
 * about 75 % accuracy, double the target is about 25 %. Never below 20 %.
 */
internal fun timingAccuracy(measured: Float, target: Float): Float {
    if (target <= 0f) return 1f
    val tolerance = maxOf(0.25f * target, 0.5f)
    val excess = (abs(measured - target) - tolerance).coerceAtLeast(0f)
    return (1f - excess / target).coerceIn(0.2f, 1f)
}

/**
 * Dartless checkout: tap the board where you would aim. The accuracy slider adds random
 * scatter to where the dart actually lands, so at low accuracy T20 might become S1 or S5.
 *
 * Metronome mode: pick a timing preset learned in the Metronome screen. Every dart is Grab darts
 * -> Aim -> tap the board, and each of those moves is timed against the preset. The closer your
 * rhythm is to the preset, the more accurate the throw — the accuracy slider sets itself.
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
    var beatAnchorMs by remember { mutableStateOf(0L) }   // 0 = beat not running
    var lastThrowMs by remember { mutableStateOf(0L) }
    val presets = remember { TimingPresets.load(context) }
    var presetName by remember { mutableStateOf(TimingPresets.selectedName(context) ?: presets.firstOrNull()?.name) }
    val preset: TimingPreset? = presets.firstOrNull { it.name == presetName }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var aimedAtMs by remember { mutableStateOf(0L) }
    var pulseKind by remember { mutableStateOf(Kind.NONE) }   // which move the beat is on right now

    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    DisposableEffect(Unit) { onDispose { toneGen.release() } }

    // Metronome beat: follows the three moves of each dart — grab, aim, throw — using the preset's
    // times. Anchored to the first "Grab darts" press of a visit (that press IS the first grab beat),
    // so the clicks land where your moves should. Each move has its own tone; the matching button flashes.
    val beatPreset = if (metronomeMode && !finished && beatAnchorMs > 0L) preset else null
    LaunchedEffect(beatPreset, beatAnchorMs) {
        if (beatPreset == null) { pulseKind = Kind.NONE; return@LaunchedEffect }
        suspend fun beat(kind: Kind, tone: Int, waitSec: Float) {
            delay((waitSec.coerceAtLeast(0.25f) * 1000).toLong() - 120)
            toneGen.startTone(tone, 70)
            pulseKind = kind
            delay(120)
            pulseKind = Kind.NONE
        }
        // Anchor = grab done. Then aim, throw, grab, aim, throw, ...
        pulseKind = Kind.GRAB; delay(120); pulseKind = Kind.NONE
        while (true) {
            beat(Kind.AIM, ToneGenerator.TONE_PROP_BEEP, beatPreset.aim)
            beat(Kind.THROW, ToneGenerator.TONE_PROP_BEEP2, beatPreset.throwTime)
            beat(Kind.GRAB, ToneGenerator.TONE_PROP_ACK, beatPreset.grab)
        }
    }

    fun idleMessage() = if (metronomeMode) "Grab darts → Aim → tap the board. The beat starts on your first grab." else "Tap the board to throw"

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
        beatAnchorMs = 0L
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

        // Metronome mode: accuracy comes from your rhythm, not the slider. Each move is judged
        // against the preset: grab (previous throw -> grab press), aim (grab -> aim press),
        // throw (aim press -> this tap). The first dart of a visit has no grab reference.
        if (metronomeMode && preset != null) {
            val aimSec = (aimedAtMs - grabbedAtMs) / 1000f
            val throwSec = (now - aimedAtMs) / 1000f
            val parts = mutableListOf(
                timingAccuracy(aimSec, preset.aim),
                timingAccuracy(throwSec, preset.throwTime)
            )
            var note = "aim ${formatSec(aimSec)}/${formatSec(preset.aim)}  throw ${formatSec(throwSec)}/${formatSec(preset.throwTime)}"
            if (lastThrowMs != 0L && dartsInVisit > 0) {
                val grabSec = (grabbedAtMs - lastThrowMs) / 1000f
                parts.add(timingAccuracy(grabSec, preset.grab))
                note = "grab ${formatSec(grabSec)}/${formatSec(preset.grab)}  $note"
            }
            accuracy = parts.average().toFloat()
            timingNote = "$note  →  ${(accuracy * 100).toInt()}%"
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
        if (dartsInVisit >= 3 || finished) {
            lastThrowMs = 0L
            beatAnchorMs = 0L
            if (!finished) message += "  ·  Grab darts to start the next visit"
        }
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

            // Metronome toggle (left) · remaining (centre) · preset picker (right)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Metronome", fontSize = 12.sp, color = Grey)
                    Switch(
                        checked = metronomeMode,
                        onCheckedChange = {
                            metronomeMode = it
                            grabbed = false
                            aimed = false
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
                    if (pulseKind == Kind.THROW) "▲  THROW  ▲" else timingNote.ifEmpty { " " },
                    fontSize = if (pulseKind == Kind.THROW) 16.sp else 13.sp,
                    fontWeight = if (pulseKind == Kind.THROW) FontWeight.Bold else FontWeight.Normal,
                    color = if (pulseKind == Kind.THROW) BrightGold else PaleGold,
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

                if (metronomeMode) {
                    Text(
                        preset?.let { "Per dart: grab ${formatSec(it.grab)} s → aim ${formatSec(it.aim)} s → throw ${formatSec(it.throwTime)} s. Follow the clicks: low = grab, mid = aim, high = throw." }
                            ?: "Pick a timing preset (top right) — learn one in the Metronome screen.",
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
            val grabColor by animateColorAsState(
                targetValue = if (grabbed) (if (pulseKind == Kind.GRAB) BrightGreen else Green) else (if (pulseKind == Kind.GRAB) BrightGold else Gold),
                animationSpec = tween(if (pulseKind == Kind.GRAB) 40 else 220), label = "grab"
            )
            val aimColor by animateColorAsState(
                targetValue = if (aimed) (if (pulseKind == Kind.AIM) BrightGreen else Green) else (if (pulseKind == Kind.AIM) BrightGold else Gold),
                animationSpec = tween(if (pulseKind == Kind.AIM) 40 else 220), label = "aim"
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (!grabbed) {
                            grabbed = true
                            grabbedAtMs = System.currentTimeMillis()
                            if (beatAnchorMs == 0L || dartsInVisit == 0 || dartsInVisit >= 3) beatAnchorMs = grabbedAtMs
                            message = "Aim"
                        }
                    },
                    enabled = !finished,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = grabColor,
                        contentColor = if (grabbed) OffWhite else Black
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(64.dp)
                ) { Text("Grab darts", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1) }

                Button(
                    onClick = {
                        when {
                            !grabbed -> message = "Grab your darts first"
                            !aimed -> { aimed = true; aimedAtMs = System.currentTimeMillis(); message = "Throw — tap the board" }
                        }
                    },
                    enabled = !finished,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = aimColor,
                        contentColor = if (aimed) OffWhite else Black
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(64.dp)
                ) { Text("Aim", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

                Spacer(Modifier.weight(1f))
            }
        }
    }
}
