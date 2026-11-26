package com.dartsapp.ui

import androidx.compose.foundation.clickable
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
import com.dartsapp.logic.Announcer

private val FastGeo = BoardGeometry.WIDE

/**
 * Fast 501: a solo leg of 501 played by poking the board. Every double and treble value is written
 * on the board, three darts a visit, double out. The power bar counts down from 501, and the
 * checkout coach reviews the finish when you get there.
 *
 * Fast Checkout (checkoutMode): the same board, but every leg starts on a random checkout (2–170)
 * instead of 501. Finish it and the coach reviews it, then the next one comes up.
 */
@Composable
fun ValueCheckerScreen(navController: NavHostController, checkoutMode: Boolean = false) {
    fun pickStart() = if (checkoutMode) CheckoutLogic.randomCheckout() else 501
    var startScore by remember { mutableStateOf(pickStart()) }
    var checkoutsDone by remember { mutableStateOf(0) }
    var remaining by remember { mutableStateOf(startScore) }
    var visitStart by remember { mutableStateOf(startScore) }
    var dartsInVisit by remember { mutableStateOf(0) }
    var dartsTotal by remember { mutableStateOf(0) }
    var scored by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Poke the board — three darts a visit, double to finish") }
    var finished by remember { mutableStateOf(false) }
    var coachOpen by remember { mutableStateOf(false) }
    var tipOpen by remember { mutableStateOf(false) }      // the Coach's advice on the current score
    val thrown = remember { mutableStateListOf<Hit>() }          // this visit
    val checkoutThrown = remember { mutableStateListOf<Hit>() }  // darts since we came within 170
    var checkoutStart by remember { mutableStateOf(0) }
    var checkoutBusts by remember { mutableStateOf(0) }
    var burstTrigger by remember { mutableStateOf(0) }
    var starOrigin by remember { mutableStateOf(Offset.Zero) }

    fun reset() {
        startScore = pickStart()
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
        Sounds.thud()
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
                if (checkoutMode) checkoutsDone++
                message = if (checkoutMode) "Checked out $startScore in $dartsTotal darts!" else "Game shot! 501 in $dartsTotal darts  ·  New to play again"
                burstTrigger++
                Sounds.playCheckoutJingle()
                Sounds.cheer(big = true)
                Announcer.gameShot()
            }
            newRem < 0 || newRem == 1 || newRem == 0 -> {
                remaining = visitStart
                dartsInVisit = 3
                if (checkoutStart != 0) checkoutBusts++
                message = "${hit.label} — BUST, back to $visitStart"
                Sounds.playBust(); Sounds.groan()
            }
            else -> {
                scored += hit.score
                remaining = newRem
                message = "${hit.label} (${hit.score})" + if (dartsInVisit == 3) "  ·  visit ${thrown.sumOf { it.score }}" else ""
                if (dartsInVisit == 3) { val v = thrown.sumOf { it.score }; Announcer.score(v); if (v >= 100) Sounds.cheer(big = v == 180) }
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
            onNext = { coachOpen = false; if (checkoutMode) reset() },
            nextLabel = if (checkoutMode) "Next checkout" else "Close"
        )
    }

    if (tipOpen) {
        CoachTipDialog(remaining = remaining, playerName = "You", onDismiss = { tipOpen = false })
    }

    val avg = if (dartsTotal == 0) 0f else scored * 3f / dartsTotal
    val inHand = if (finished) 0 else if (dartsInVisit >= 3) 0 else 3 - dartsInVisit

    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = conf.screenWidthDp > conf.screenHeightDp      // sideways: stats left, board right
    @Composable fun stats() {
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
                        (if (checkoutMode) "checkout $startScore   done $checkoutsDone   darts $dartsTotal" else "avg ${"%.1f".format(avg)}   darts $dartsTotal") +
                            (if (!checkoutMode && remaining in 2..170 && CheckoutLogic.isFinishable(remaining)) "   ${CheckoutLogic.tip(remaining)}" else ""),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = PaleGold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Fixed-height status block so the board never moves
            Column(modifier = Modifier.fillMaxWidth().height(if (checkoutMode) 72.dp else 48.dp).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(24.dp))
                Text(
                    "Dart ${if (dartsInVisit >= 3) 3 else dartsInVisit}/3   Visit: " + thrown.joinToString(" ") { it.label }.ifEmpty { "—" },
                    fontSize = 13.sp, color = Grey, maxLines = 1, modifier = Modifier.height(20.dp)
                )
                if (checkoutMode) {
                    // The Coach's route for what's left, always on show; tap it for his full advice
                    val route = remember(remaining) { if (remaining in 2..170) CheckoutLogic.suggest(remaining)?.let { CheckoutLogic.routeLabel(it.best) } else null }
                    Row(
                        modifier = Modifier.height(24.dp).clickable { tipOpen = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoachHead(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (finished) "Nice one." else route?.let { "Coach: $it" } ?: "Coach: ${CheckoutLogic.tip(remaining)}",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

    }
    @Composable fun hint() {
            Text(
                "Inner numbers = trebles, outer numbers = doubles",
                fontSize = 12.sp, color = Grey, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(if (checkoutMode) "Fast Checkout" else "Fast 501", navController) {
                // The Coach: tap for his route on the current score
                IconButton(onClick = { tipOpen = true }, modifier = Modifier.size(40.dp)) { CoachHead(modifier = Modifier.size(34.dp)) }
                TextButton(onClick = { reset() }) { Text("New", color = Gold) }
            }
            if (!wide) {
                stats()
                Dartboard(
                    modifier = Modifier.padding(8.dp).onGloballyPositioned {
                        val p = it.positionInRoot()
                        starOrigin = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                    },
                    geometry = FastGeo,
                    showValues = true,
                    onTap = { poke(it) }
                )

                hint()
            } else {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        stats()
                        hint()
                        Spacer(Modifier.weight(1f))
                        DartsInHand(inHand = inHand, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp).size(width = 132.dp, height = 90.dp))
                    }
                    Box(
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).widthIn(max = landscapeBoardCap(conf.screenWidthDp.dp)).aspectRatio(1f, matchHeightConstraintsFirst = true),
                        contentAlignment = Alignment.Center
                    ) {
                        Dartboard(
                            modifier = Modifier.padding(8.dp).onGloballyPositioned {
                                val p = it.positionInRoot()
                                starOrigin = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                            },
                            geometry = FastGeo,
                            showValues = true,
                            onTap = { poke(it) }
                        )

                    }
                }
            }
        }

        if (!wide) DartsInHand(
            inHand = inHand,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp).size(width = 132.dp, height = 90.dp)
        )
        StarBurst(trigger = burstTrigger, origin = starOrigin, modifier = Modifier.fillMaxSize())
    }
}
