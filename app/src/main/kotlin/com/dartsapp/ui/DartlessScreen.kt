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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import com.dartsapp.data.Hit
import com.dartsapp.logic.AccuracyModel
import com.dartsapp.logic.CheckoutLogic

private val Green = Color(0xFF1B9A3C)
private val BoardGeo = BoardGeometry.PRACTICE

/**
 * Dartless checkout: tap the board where you would aim. The accuracy slider adds random
 * scatter to where the dart actually lands, so at low accuracy T20 might become S1 or S5.
 *
 * Metronome mode adds the physical rhythm: press "Grab darts" at the start of each visit and
 * "Aim" before every dart; only then does a tap on the board count as a throw.
 */
@Composable
fun DartlessScreen(navController: NavHostController) {
    var start by remember { mutableStateOf(CheckoutLogic.randomCheckout()) }
    var remaining by remember { mutableStateOf(start) }
    var visitStart by remember { mutableStateOf(start) }
    var dartsInVisit by remember { mutableStateOf(0) }
    var dartsTotal by remember { mutableStateOf(0) }
    var accuracy by remember { mutableStateOf(0.8f) }
    var message by remember { mutableStateOf("Tap the board to throw") }
    var finished by remember { mutableStateOf(false) }
    val marks = remember { mutableStateListOf<Offset>() }
    val thrown = remember { mutableStateListOf<Hit>() }
    val model = remember { AccuracyModel() }

    // Metronome mode gating
    var metronomeMode by remember { mutableStateOf(false) }
    var grabbed by remember { mutableStateOf(false) }
    var aimed by remember { mutableStateOf(false) }

    fun newCheckout() {
        start = CheckoutLogic.randomCheckout()
        remaining = start
        visitStart = start
        dartsInVisit = 0
        dartsTotal = 0
        finished = false
        message = if (metronomeMode) "Grab your darts" else "Tap the board to throw"
        marks.clear()
        thrown.clear()
        grabbed = false
        aimed = false
    }

    fun throwAt(aim: Offset) {
        if (finished) return
        if (metronomeMode) {
            if (!grabbed) { message = "Grab your darts first"; return }
            if (!aimed) { message = "Aim first"; return }
        }
        // Start a fresh visit if the previous one is complete.
        if (dartsInVisit >= 3) {
            dartsInVisit = 0
            visitStart = remaining
            marks.clear()
            thrown.clear()
        }
        val target = Board.hitTest(aim.x, aim.y, BoardGeo)
        val (lx, ly) = model.land(aim.x, aim.y, accuracy)
        val hit = Board.hitTest(lx, ly, BoardGeo)
        marks.add(Offset(lx, ly))
        thrown.add(hit)
        dartsInVisit++
        dartsTotal++
        aimed = false

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
        // Both buttons reset after every throw.
        grabbed = false
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

            Dartboard(
                modifier = Modifier.padding(8.dp),
                geometry = BoardGeo,
                marks = marks,
                onTap = { throwAt(it) }
            )

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Accuracy: ${(accuracy * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = accuracy, onValueChange = { accuracy = it }, valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Metronome mode", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Grab darts, aim, then throw — before every dart", fontSize = 12.sp, color = Grey)
                    }
                    Switch(
                        checked = metronomeMode,
                        onCheckedChange = {
                            metronomeMode = it
                            grabbed = false
                            aimed = false
                            if (!finished) message = if (it) "Grab your darts" else "Tap the board to throw"
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = DarkRed)
                    )
                }
            }

            if (finished) {
                Button(onClick = { newCheckout() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) { Text("Next checkout") }
            }
        }

        // Metronome-mode action buttons pinned to the bottom of the screen
        if (metronomeMode) {
            // Gold = waiting to be pressed, green = done. Both reset after every throw.
            Button(
                onClick = { if (!grabbed) { grabbed = true; message = "Aim" } },
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
