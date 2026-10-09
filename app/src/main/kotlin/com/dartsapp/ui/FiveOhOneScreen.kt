package com.dartsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.dartsapp.logic.Announcer
import com.dartsapp.logic.TimingPresets
import androidx.compose.ui.platform.LocalContext
import com.dartsapp.logic.AccuracyModel
import com.dartsapp.logic.Banter
import com.dartsapp.logic.Settings
import kotlinx.coroutines.delay

private class PlayerState(
    val name: String,
    var remaining: Int,
    var legs: Int = 0,
    var sets: Int = 0,
    var dartsThisLeg: Int = 0,
    var scoredThisLeg: Int = 0,
    var dartsMatch: Int = 0,
    var scoredMatch: Int = 0,
    var hotStreak: Int = 0,          // consecutive 100+ visits
    val visits: MutableList<Int> = mutableListOf()
) {
    val average: Float get() = if (dartsMatch == 0) 0f else scoredMatch * 3f / dartsMatch
}

/**
 * Two-player 501 (or 301) with legs and sets. Players enter their visit score on the number pad.
 * Each player's power bar runs down from the starting score to zero.
 *
 * With [vsBot] you play your real board against one of the game's characters: you enter your
 * visits, and he throws his on screen at his own pace, with his usual post-match word.
 */
@Composable
fun FiveOhOneScreen(navController: NavHostController, vsBot: Boolean = false) {
    val context = LocalContext.current
    val difficulty = remember { Settings.difficulty(context) }
    var opponent by remember {
        mutableStateOf(Opponent.values()[Settings.opponentIndex(context).coerceIn(0, Opponent.values().size - 1)].let { if (it == Opponent.COACH) Opponent.MULLET else it })
    }
    var botKey by remember { mutableStateOf(0) }          // bumps when it is the character's turn
    var botThrowing by remember { mutableStateOf(false) }
    var botDarts by remember { mutableStateOf("") }
    var banterOpen by remember { mutableStateOf(false) }
    var bioOpen by remember { mutableStateOf(false) }
    var banterText by remember { mutableStateOf("") }
    val model = remember { AccuracyModel() }
    val presets = remember { TimingPresets.load(context) }
    var metronomeOn by remember { mutableStateOf(false) }
    val presetNames = remember { mutableStateListOf<String?>(TimingPresets.selectedOrDefault(context, presets), TimingPresets.selectedOrDefault(context, presets)) }
    val turnKeys = remember { mutableStateListOf(0, 0) }

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
    var popTrigger by remember { mutableStateOf(0) }
    var popText by remember { mutableStateOf("") }
    var popHuge by remember { mutableStateOf(false) }
    var coachOpen by remember { mutableStateOf(false) }
    val barPos = remember { mutableStateListOf(Offset.Zero, Offset.Zero) }
    val barSize = remember { mutableStateListOf(IntSize.Zero, IntSize.Zero) }

    fun panelCentre(i: Int) = Offset(barPos[i].x + barSize[i].width / 2f, barPos[i].y + barSize[i].height / 2f)

    /** Celebrate a visit: ton-plus gets a pop, 140+ a bigger one, 180 and ton-plus finishes go huge. */
    fun celebrate(i: Int, score: Int, finished: Boolean) {
        val text = when {
            finished && score >= 100 -> "$score OUT!"
            score == 180 -> "180!"
            score >= 140 -> "$score"
            score >= 100 -> "TON ${if (score == 100) "" else (score - 100).toString()}".trim()
            else -> return
        }
        popText = text
        popHuge = score == 180 || (finished && score >= 100)
        burstOrigin = panelCentre(i)
        popTrigger++
        if (popHuge) Sounds.playCheckoutJingle()
        Sounds.cheer(big = popHuge)
        if (finished) Announcer.checkout(score) else Announcer.score(score)
    }

    fun cueTurn() {
        if (metronomeOn && !matchOver) turnKeys[current] = turnKeys[current] + 1
        if (vsBot && current == 1 && !matchOver) botKey++
    }

    fun legsNeeded() = legsPerSet / 2 + 1
    fun setsNeeded() = setsToWin

    fun startMatch() {
        players = listOf(
            PlayerState(name1.ifBlank { if (vsBot) "YOU" else "P1" }, startScore),
            PlayerState(if (vsBot) opponent.displayName else name2.ifBlank { "P2" }, startScore)
        )
        current = 0
        legStarter = 0
        input = ""
        matchOver = false
        message = "${players[0].name} to throw"
        setupOpen = false
        version++
        cueTurn()
        Announcer.gameOn()
    }

    fun newLeg() {
        for (p in players) { p.remaining = startScore; p.dartsThisLeg = 0; p.scoredThisLeg = 0; p.visits.clear() }
        legStarter = 1 - legStarter
        current = legStarter
        version++
        cueTurn()
    }

    fun applyVisit(score: Int, forceBust: Boolean) {
        val p = players[current]
        val other = players[1 - current]
        val newRem = if (forceBust) -1 else p.remaining - score
        p.dartsMatch += 3
        p.dartsThisLeg += 3
        when {
            newRem == 0 -> {
                p.scoredMatch += score; p.scoredThisLeg += score
                p.remaining = 0
                p.visits.add(score)
                p.hotStreak = if (score >= 100) p.hotStreak + 1 else 0
                celebrate(current, score, finished = true)
                if (score < 100) { Announcer.gameShot(); Sounds.cheer(big = true) }
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
                        burstOrigin = panelCentre(i)
                        burstTrigger++
                        if (score < 100) Sounds.playCheckoutJingle()
                        if (vsBot) { banterText = Banter.line(opponent, botWon = i == 1); banterOpen = true }
                    }
                }
                message = text
                version++
                if (!matchOver) newLeg()
            }
            newRem < 0 || newRem == 1 -> {
                p.visits.add(0)
                p.hotStreak = 0
                message = "${p.name} bust — stays on ${p.remaining}"
                Sounds.groan()
                current = 1 - current
                version++
                cueTurn()
            }
            else -> {
                p.scoredMatch += score; p.scoredThisLeg += score
                p.remaining = newRem
                p.visits.add(score)
                p.hotStreak = if (score >= 100) p.hotStreak + 1 else 0
                celebrate(current, score, finished = false)
                message = "${other.name} to throw"
                current = 1 - current
                version++
                cueTurn()
            }
        }
    }

    fun submit() {
        if (matchOver) return
        val score = input.toIntOrNull() ?: return
        input = ""
        if (score > 180) { message = "Max 180 per visit"; return }
        applyVisit(score, forceBust = false)
    }

    fun undo() {
        if (vsBot) {
            // Against a character: take back his last visit and yours, so it is your throw again
            if (botThrowing || current != 0) return
            for (who in intArrayOf(1, 0)) {
                val pl = players[who]
                val last = pl.visits.removeLastOrNull() ?: continue
                pl.remaining += last; pl.scoredMatch -= last; pl.scoredThisLeg -= last
                pl.dartsMatch -= 3; pl.dartsThisLeg -= 3
            }
            current = 0; message = "${players[0].name} to throw"; version++
            return
        }
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

    // The character's visit: three darts on screen at his pace, then scored like any other visit
    LaunchedEffect(botKey) {
        if (!vsBot || botKey == 0 || matchOver || current != 1) return@LaunchedEffect
        botThrowing = true
        botDarts = ""
        val bot = players[1]
        val accuracy = botBaseAccuracy(opponent, difficulty)
        val form = 1f + (kotlin.random.Random.nextFloat() * 2f - 1f) * opponent.jitter
        delay(900)
        var rem = bot.remaining
        var visit = 0
        var bust = false
        for (d in 1..3) {
            val hit = botThrow(opponent, rem, accuracy, form, model)
            Sounds.thud()
            botDarts = (botDarts + " " + hit.label).trim()
            val after = rem - hit.score
            when {
                after == 0 && hit.isDoubleOut -> { visit += hit.score; rem = 0 }
                after < 2 -> bust = true
                else -> { visit += hit.score; rem = after }
            }
            message = "${bot.name}: $botDarts"
            if (rem == 0 || bust) break
            delay(opponent.paceMs)
        }
        delay(700)
        botThrowing = false
        if (!matchOver && current == 1) applyVisit(visit, forceBust = bust)
    }
    // IRL has no on-screen board, so his super power does not come into play here
    if (bioOpen) OpponentBioDialog(opponent = opponent, showPower = false, onDismiss = { bioOpen = false })
    if (banterOpen) {
        BanterDialog(opponent = opponent, text = banterText, youWon = players[0].sets >= setsToWin, onDismiss = { banterOpen = false })
    }

    if (coachOpen) {
        CoachTipDialog(remaining = players[current].remaining, playerName = players[current].name, onDismiss = { coachOpen = false })
    }

    if (setupOpen) {
        MatchSetupDialog(
            name1 = name1, name2 = name2, startScore = startScore, legsPerSet = legsPerSet, setsToWin = setsToWin,
            onName1 = { name1 = it }, onName2 = { name2 = it },
            onStart = { startScore = it }, onLegs = { legsPerSet = it }, onSets = { setsToWin = it },
            opponent = if (vsBot) opponent else null,
            onOpponent = { opponent = it; Settings.setOpponentIndex(context, it.ordinal) },
            onBegin = { startMatch() },
            onCancel = { if (players[0].dartsMatch == 0 && players[1].dartsMatch == 0) navController.popBackStack() else setupOpen = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader((if (startScore == 301) "301" else "501") + (if (vsBot) " vs ${opponent.displayName.removePrefix("THE ").lowercase().replaceFirstChar { it.uppercase() }}" else ""), navController) {
            IconButton(onClick = { coachOpen = true }, modifier = Modifier.size(40.dp)) { CoachHead(modifier = Modifier.size(34.dp)) }
            TextButton(onClick = { metronomeOn = !metronomeOn; if (metronomeOn) cueTurn() }) {
                Text("Metro", color = if (metronomeOn) Gold else Grey)
            }
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
                    .background(if (active) Charcoal else Night)
                    .onGloballyPositioned { barPos[i] = it.positionInRoot(); barSize[i] = it.size }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vsBot && i == 1) OpponentHead(opponent, modifier = Modifier.size(34.dp).clickable { bioOpen = true }.padding(end = 6.dp))
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
                        Box(modifier = Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.BottomCenter) {
                            if (p.hotStreak >= 3) {
                                FlameFrame(modifier = Modifier.fillMaxWidth().height(34.dp))
                            }
                            PowerBar(
                                value = p.remaining.toFloat() / startScore,
                                enabled = false,
                                onChange = {},
                                modifier = Modifier.fillMaxWidth().height(22.dp),
                                segmentColor = { frac -> if (frac * startScore < 170f) Color(0xFFFF7A1A) else BoardCream }
                            )
                        }
                        if (p.hotStreak >= 3) {
                            Text("ON FIRE  ×${p.hotStreak}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Color(0xFFF08A1E))
                        }
                        Text(
                            "avg ${"%.1f".format(p.average)}   darts ${p.dartsThisLeg}" +
                                (if (p.remaining in 2..170 && CheckoutLogic.isFinishable(p.remaining)) "   ${CheckoutLogic.tip(p.remaining)}" else ""),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (active) PaleGold else Grey,
                            maxLines = 1, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (metronomeOn) {
                    MiniMetronome(
                        presets = presets,
                        preset = presets.firstOrNull { it.name == presetNames[i] },
                        onPresetChange = { presetNames[i] = it.name },
                        turnKey = turnKeys[i],
                        active = active,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
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
            if (vsBot && current == 1) "${players[1].name} throwing…" else if (input.isEmpty()) "Enter ${players[current].name}'s visit" else input,
            fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
            color = if (input.isEmpty()) Grey else OffWhite,
            modifier = Modifier.fillMaxWidth().padding(4.dp)
        )

        NumberPad(
            enabled = !matchOver && !(vsBot && current == 1),
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
    BigPop(trigger = popTrigger, text = popText, huge = popHuge, origin = burstOrigin, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun MatchSetupDialog(
    name1: String, name2: String, startScore: Int, legsPerSet: Int, setsToWin: Int,
    onName1: (String) -> Unit, onName2: (String) -> Unit,
    onStart: (Int) -> Unit, onLegs: (Int) -> Unit, onSets: (Int) -> Unit,
    onBegin: () -> Unit, onCancel: () -> Unit,
    opponent: Opponent? = null, onOpponent: (Opponent) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("MATCH SETUP", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold) },
        text = {
            Column {
                if (opponent == null) {
                    Row {
                        OutlinedTextField(value = name1, onValueChange = onName1, label = { Text("Player 1") }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = name2, onValueChange = onName2, label = { Text("Player 2") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                } else {
                    // You on your real board against one of the characters
                    Text("Opponent", fontSize = 12.sp, color = Grey)
                    for (row in Opponent.values().filter { it != Opponent.COACH }.chunked(3)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (o in row) {
                                val sel = o == opponent
                                Box(
                                    modifier = Modifier.size(58.dp)
                                        .background(if (sel) DarkRed else Charcoal)
                                        .border(2.dp, if (sel) Gold else Color.Transparent)
                                        .clickable { onOpponent(o) },
                                    contentAlignment = Alignment.Center
                                ) { OpponentHead(o, modifier = Modifier.size(52.dp)) }
                            }
                        }
                    }
                    Text(opponent.displayName + " — " + opponent.blurb, fontSize = 11.sp, color = PaleGold, maxLines = 3)
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
internal fun OptionRow(label: String, options: List<Int>, selected: Int, text: (Int) -> String, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label.uppercase(), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Grey)
        Row(modifier = Modifier.fillMaxWidth()) {
            for (o in options) {
                val sel = o == selected
                OutlinedButton(
                    onClick = { onSelect(o) },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    border = BorderStroke(1.5.dp, if (sel) Gold else Charcoal),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) DarkRed else Night, contentColor = if (sel) OffWhite else Grey),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp).height(40.dp)
                ) { Text(text(o), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
