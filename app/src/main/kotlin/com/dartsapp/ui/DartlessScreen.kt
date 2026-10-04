package com.dartsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import com.dartsapp.data.Hit
import com.dartsapp.logic.AccuracyModel
import com.dartsapp.logic.CheckoutLogic

/**
 * Dartless checkout: tap the board where you would aim. The accuracy slider adds random
 * scatter to where the dart actually lands, so at low accuracy T20 might become S1 or S5.
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

    fun newCheckout() {
        start = CheckoutLogic.randomCheckout()
        remaining = start
        visitStart = start
        dartsInVisit = 0
        dartsTotal = 0
        finished = false
        message = "Tap the board to throw"
        marks.clear()
        thrown.clear()
    }

    fun throwAt(aim: Offset) {
        if (finished) return
        // Start a fresh visit if the previous one is complete.
        if (dartsInVisit >= 3) {
            dartsInVisit = 0
            visitStart = remaining
            marks.clear()
            thrown.clear()
        }
        val aimed = Board.hitTest(aim.x, aim.y, BoardGeometry.WIDE)
        val (lx, ly) = model.land(aim.x, aim.y, accuracy)
        val hit = Board.hitTest(lx, ly, BoardGeometry.WIDE)
        marks.add(Offset(lx, ly))
        thrown.add(hit)
        dartsInVisit++
        dartsTotal++

        val hitText = if (aimed == hit) "Hit ${hit.label} (${hit.score})" else "Aimed ${aimed.label}, hit ${hit.label} (${hit.score})"
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
    }

    val tip = remember(remaining) { if (remaining > 1) CheckoutLogic.tip(remaining) else "" }
    val dartNo = if (dartsInVisit >= 3) 3 else dartsInVisit

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
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
            geometry = BoardGeometry.WIDE,
            marks = marks,
            onTap = { throwAt(it) }
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Accuracy: ${(accuracy * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Slider(value = accuracy, onValueChange = { accuracy = it }, valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Red, inactiveTrackColor = Charcoal))
        }

        if (finished) {
            Button(onClick = { newCheckout() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Next checkout") }
        }
    }
}
