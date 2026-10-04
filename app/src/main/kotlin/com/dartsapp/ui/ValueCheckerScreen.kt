package com.dartsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import com.dartsapp.data.Hit
import com.dartsapp.logic.CheckoutLogic
import com.dartsapp.logic.Sounds

private val FastGeo = BoardGeometry.WIDE

/**
 * Fast 501: a solo leg of 501 played by poking the board. Every double and treble value is written
 * on the board, three darts a visit, double out. The power bar counts down from 501, and the
 * checkout coach reviews the finish when you get there.
 */
@Composable
fun ValueCheckerScreen(navController: NavHostController) {
    val startScore = 501
    var remaining by remember { mutableStateOf(startScore) }
    var visitStart by remember { mutableStateOf(startScore) }
    var dartsInVisit by remember { mutableStateOf(0) }
    var dartsTotal by remember { mutableStateOf(0) }
    var scored by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Poke the board — three darts a visit, double to finish") }
    var finished by remember { mutableStateOf(false) }
    var coachOpen by remember { mutableStateOf(false) }
    val thrown = remember { mutableStateListOf<Hit>() }          // this visit
    val checkoutThrown = remember { mutableStateListOf<Hit>() }  // darts since we came within 170
    var checkoutStart by remember { mutableStateOf(0) }
    var checkoutBusts by remember { mutableStateOf(0) }
    var burstTrigger by remember { mutableStateOf(0) }
    var starOrigin by remember { mutableStateOf(Offset.Zero) }

    fun reset() {
        remaining = startScore; visitStart = startScore
        dartsInVisit = 0; dartsTotal = 0; scored = 0
        finished = false; coachOpen = false
        thrown.clear(); checkoutThrown.clear()
        checkoutStart = 0; checkoutBusts = 0
        message = "Poke the board — three darts a visit, double to finish"
    }

    fun poke(p: Offset) {
        if (finished) return
        if (dartsInVisit >= 3) { dartsInVisit = 0; visitStart = remaining; thrown.clear() }
        val hit = Board.hitTest(p.x, p.y, FastGeo)
        // Start tracking the checkout phase the first time we throw from 170 or less
        if (checkoutStart == 0 && remaining <= 170) checkoutStart = remaining
        if (checkoutStart != 0) checkoutThrown.add(hit)
        thrown.add(hit)
        dartsInVisit++
        dartsTotal++
        val newRem = remaining - hit.score
        when {
            newRem == 0 && hit.isDoubleOut -> {
                scored += hit.score
                remaining = 0
                finished = true
                coachOpen = true
                message = "Game shot! 501 in $dartsTotal darts"
                burstTrigger++
                Sounds.playCheckoutJingle()
            }
            newRem < 0 || newRem == 1 || newRem == 0 -> {
                remaining = visitStart
                dartsInVisit = 3
                if (checkoutStart != 0) checkoutBusts++
                message = "${hit.label} — BUST, back to $visitStart"
                Sounds.playBust()
            }
            else -> {
                scored += hit.score
                remaining = newRem
                message = "${hit.label} (${hit.score})" + if (dartsInVisit == 3) "  ·  visit ${thrown.sumOf { it.score }}" else ""
            }
        }
    }

    if (coachOpen) {
        CoachDialog(
            start = if (checkoutStart != 0) checkoutStart else startScore,
            thrown = checkoutThrown.toList(),
            aimed = checkoutThrown.toList(),
            busts = checkoutBusts,
            onDismiss = { coachOpen = false },
            onNext = { reset() },
            nextLabel = "Play again"
        )
    }

    val avg = if (dartsTotal == 0) 0f else scored * 3f / dartsTotal
    val inHand = if (finished) 0 else if (dartsInVisit >= 3) 0 else 3 - dartsInVisit

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader("Fast 501", navController) {
                TextButton(onClick = { reset() }) { Text("New", color = Gold) }
            }

            // Score + power bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    remaining.toString(), fontSize = 48.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                    color = Gold, modifier = Modifier.width(120.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    PowerBar(
                        value = remaining.toFloat() / startScore,
                        enabled = false, onChange = {},
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                    Text(
                        "avg ${"%.1f".format(avg)}   darts $dartsTotal" +
                            (if (remaining in 2..170 && CheckoutLogic.isFinishable(remaining)) "   ${CheckoutLogic.tip(remaining)}" else ""),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = PaleGold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Fixed-height status block so the board never moves
            Column(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(24.dp))
                Text(
                    "Dart ${if (dartsInVisit >= 3) 3 else dartsInVisit}/3   Visit: " + thrown.joinToString(" ") { it.label }.ifEmpty { "—" },
                    fontSize = 13.sp, color = Grey, maxLines = 1, modifier = Modifier.height(20.dp)
                )
            }

            Dartboard(
                modifier = Modifier.padding(8.dp).onGloballyPositioned {
                    val p = it.positionInRoot()
                    starOrigin = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                },
                geometry = FastGeo,
                showValues = true,
                onTap = { poke(it) }
            )

            Text(
                "Inner numbers = trebles, outer numbers = doubles",
                fontSize = 12.sp, color = Grey, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        DartsInHand(
            inHand = inHand,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp).size(width = 132.dp, height = 90.dp)
        )
        StarBurst(trigger = burstTrigger, origin = starOrigin, modifier = Modifier.fillMaxSize())
    }
}
