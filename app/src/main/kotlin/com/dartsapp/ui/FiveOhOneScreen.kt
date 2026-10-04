package com.dartsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.logic.CheckoutLogic
import com.dartsapp.logic.Sounds

private class PlayerState(
    val name: String,
    var remaining: Int,
    var legs: Int = 0,
    var sets: Int = 0,
    var dartsThisLeg: Int = 0,
    var scoredThisLeg: Int = 0,
    var dartsMatch: Int = 0,
    var scoredMatch: Int = 0,
    val visits: MutableList<Int> = mutableListOf()
) {
    val average: Float get() = if (dartsMatch == 0) 0f else scoredMatch * 3f / dartsMatch
}

/**
 * Two-player 501 (or 301) with legs and sets. Players enter their visit score on the number pad.
 * Each player's power bar runs down from the starting score to zero.
 */
@Composable
fun FiveOhOneScreen(navController: NavHostController) {
    var setupOpen by remember { mutableStateOf(true) }
    var startScore by remember { mutableStateOf(501) }
    var legsPerSet by remember { mutableStateOf(3) }
    var setsToWin by remember { mutableStateOf(1) }
    var name1 by remember { mutableStateOf("P1") }
    var name2 by remember { mutableStateOf("P2") }

    var players by remember { mutableStateOf(listOf(PlayerState("P1", 501), PlayerState("P2", 501))) }
    var current by remember { mutableStateOf(0) }
    var legStarter by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var matchOver by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }   // bump to recompose after mutating player state
    var burstTrigger by remember { mutableStateOf(0) }
    var burstOrigin by remember { mutableStateOf(Offset.Zero) }
    val barPos = remember { mutableStateListOf(Offset.Zero, Offset.Zero) }
    val barSize = remember { mutableStateListOf(IntSize.Zero, IntSize.Zero) }

    fun legsNeeded() = legsPerSet / 2 + 1
    fun setsNeeded() = setsToWin

    fun startMatch() {
        players = listOf(PlayerState(name1.ifBlank { "P1" }, startScore), PlayerState(name2.ifBlank { "P2" }, startScore))
        current = 0
        legStarter = 0
        input = ""
        matchOver = false
        message = "${players[0].name} to throw"
        setupOpen = false
        version++
    }

    fun newLeg() {
        for (p in players) { p.remaining = startScore; p.dartsThisLeg = 0; p.scoredThisLeg = 0; p.visits.clear() }
        legStarter = 1 - legStarter
        current = legStarter
        version++
    }

    fun submit() {
        if (matchOver) return
        val score = input.toIntOrNull() ?: return
        input = ""
        if (score > 180) { message = "Max 180 per visit"; return }
        val p = players[current]
        val other = players[1 - current]
        val newRem = p.remaining - score
        p.dartsMatch += 3
        p.dartsThisLeg += 3
        when {
            newRem == 0 -> {
                p.scoredMatch += score; p.scoredThisLeg += score
                p.remaining = 0
                p.visits.add(score)
                p.legs++
                var text = "${p.name} takes the leg in ${p.dartsThisLeg} darts!"
                if (p.legs >= legsNeeded()) {
                    p.sets++
                    p.legs = 0; other.legs = 0
                    text = "${p.name} wins the set!"
                    if (p.sets >= setsNeeded()) {
                        matchOver = true
                        text = "${p.name} WINS THE MATCH!"
                        val i = current
                        burstOrigin = Offset(barPos[i].x + barSize[i].width / 2f, barPos[i].y + barSize[i].height / 2f)
                        burstTrigger++
                        Sounds.playCheckoutJingle()
                    }
                }
                message = text
                version++
                if (!matchOver) newLeg()
            }
            newRem < 0 || newRem == 1 -> {
                p.visits.add(0)
                message = "${p.name} bust — stays on ${p.remaining}"
                current = 1 - current
                version++
            }
            else -> {
                p.scoredMatch += score; p.scoredThisLeg += score
                p.remaining = newRem
                p.visits.add(score)
                message = "${other.name} to throw"
                current = 1 - current
                version++
            }
        }
    }

    fun undo() {
        // Simple undo: step back one visit for the previous player (no undo across a leg end)
        val prev = 1 - current
        val p = players[prev]
        val last = p.visits.removeLastOrNull() ?: return
        p.remaining += last
        p.scoredMatch -= last; p.scoredThisLeg -= last
        p.dartsMatch -= 3; p.dartsThisLeg -= 3
        current = prev
        message = "${p.name} to throw"
        version++
    }

    if (setupOpen) {
        MatchSetupDialog(
            name1 = name1, name2 = name2, startScore = startScore, legsPerSet = legsPerSet, setsToWin = setsToWin,
            onName1 = { name1 = it }, onName2 = { name2 = it },
            onStart = { startScore = it }, onLegs = { legsPerSet = it }, onSets = { setsToWin = it },
            onBegin = { startMatch() },
            onCancel = { if (players[0].dartsMatch == 0 && players[1].dartsMatch == 0) navController.popBackStack() else setupOpen = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(if (startScore == 301) "301" else "501", navController) {
            TextButton(onClick = { setupOpen = true }) { Text("Match", color = Gold) }
        }

        // Player state is plain mutable data; reading `version` here re-draws the panels after each visit.
        if (version < 0) Text("")

        // Two player panels
        for (i in 0..1) {
            val p = players[i]
            val active = i == current && !matchOver
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .background(if (active) Charcoal else Black)
                    .onGloballyPositioned { barPos[i] = it.positionInRoot(); barSize[i] = it.size }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name.uppercase(), fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp, color = if (active) Gold else Grey, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "SETS ${p.sets}  LEGS ${p.legs}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Grey
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.remaining.toString(), fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                        color = if (active) Gold else OffWhite, modifier = Modifier.width(110.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        PowerBar(
                            value = p.remaining.toFloat() / startScore,
                            enabled = false,
                            onChange = {},
                            modifier = Modifier.fillMaxWidth().height(22.dp)
                        )
                        Text(
                            "avg ${"%.1f".format(p.average)}   darts ${p.dartsThisLeg}" +
                                (if (p.remaining in 2..170 && CheckoutLogic.isFinishable(p.remaining)) "   ${CheckoutLogic.tip(p.remaining)}" else ""),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (active) PaleGold else Grey,
                            maxLines = 1, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Text(
            message.ifEmpty { " " },
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gold, textAlign = TextAlign.Center,
            maxLines = 1, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )

        Spacer(Modifier.weight(1f))

        Text(
            if (input.isEmpty()) "Enter ${players[current].name}'s visit" else input,
            fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
            color = if (input.isEmpty()) Grey else OffWhite,
            modifier = Modifier.fillMaxWidth().padding(4.dp)
        )

        NumberPad(
            enabled = !matchOver,
            onDigit = { d -> if (input.length < 3) input += d },
            onBackspace = { input = input.dropLast(1) },
            onEnter = { submit() }
        )

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { undo() }, enabled = !matchOver && players[1 - current].visits.isNotEmpty()) { Text("Undo") }
            if (matchOver) Button(onClick = { setupOpen = true }) { Text("New match") }
        }
    }
    StarBurst(trigger = burstTrigger, origin = burstOrigin, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun MatchSetupDialog(
    name1: String, name2: String, startScore: Int, legsPerSet: Int, setsToWin: Int,
    onName1: (String) -> Unit, onName2: (String) -> Unit,
    onStart: (Int) -> Unit, onLegs: (Int) -> Unit, onSets: (Int) -> Unit,
    onBegin: () -> Unit, onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("MATCH SETUP", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold) },
        text = {
            Column {
                Row {
                    OutlinedTextField(value = name1, onValueChange = onName1, label = { Text("Player 1") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = name2, onValueChange = onName2, label = { Text("Player 2") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                OptionRow("Game", listOf(301, 501), startScore, { it.toString() }, onStart)
                OptionRow("Legs per set", listOf(1, 3, 5, 7), legsPerSet, { "$it" }, onLegs)
                OptionRow("Sets to win", listOf(1, 2, 3, 5), setsToWin, { "$it" }, onSets)
                Text(
                    "First to ${legsPerSet / 2 + 1} leg${if (legsPerSet / 2 + 1 == 1) "" else "s"} takes a set; " +
                        "first to $setsToWin set${if (setsToWin == 1) "" else "s"} wins. Players alternate who starts each leg.",
                    fontSize = 12.sp, color = Grey, modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { Button(onClick = onBegin) { Text("Game on") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", color = Grey) } }
    )
}

@Composable
private fun OptionRow(label: String, options: List<Int>, selected: Int, text: (Int) -> String, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label.uppercase(), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Grey)
        Row(modifier = Modifier.fillMaxWidth()) {
            for (o in options) {
                val sel = o == selected
                OutlinedButton(
                    onClick = { onSelect(o) },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    border = BorderStroke(1.5.dp, if (sel) Gold else Charcoal),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) DarkRed else Black, contentColor = if (sel) OffWhite else Grey),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp).height(40.dp)
                ) { Text(text(o), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
