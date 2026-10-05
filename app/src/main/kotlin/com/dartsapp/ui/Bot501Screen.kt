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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import com.dartsapp.data.Hit
import com.dartsapp.data.Ring
import com.dartsapp.logic.AccuracyModel
import com.dartsapp.logic.CheckoutLogic
import com.dartsapp.logic.Difficulty
import com.dartsapp.logic.Settings
import com.dartsapp.logic.Sounds
import com.dartsapp.logic.TimingPreset
import com.dartsapp.logic.TimingPresets
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val Geo = BoardGeometry.PRACTICE

private class Side(val name: String, var remaining: Int, var legs: Int = 0, var sets: Int = 0, var darts: Int = 0, var scored: Int = 0) {
    val average: Float get() = if (darts == 0) 0f else scored * 3f / darts
}

/** Where the bot aims for a given remaining score: the book route when in range, otherwise T20. */
private fun botTarget(remaining: Int): Hit {
    if (remaining <= 170) CheckoutLogic.bestFinish(remaining)?.let { return it.first() }
    // Setting up: T20, unless a treble would bust or leave 1
    if (remaining - 60 >= 2) return Hit(20, Ring.TREBLE)
    if (remaining - 20 >= 2) return Hit(20, Ring.SINGLE)
    return Hit(remaining / 2, Ring.DOUBLE)
}

/** Board coordinates (normalised) for the centre of a sector. */
private fun targetPoint(hit: Hit): Offset {
    if (hit.ring == Ring.BULL) return Offset(0f, 0f)
    if (hit.ring == Ring.OUTER_BULL) return Offset(0f, -(Geo.bullR + Geo.outerBullR) / 2f)
    val idx = Board.segments.indexOf(hit.number)
    val a = Math.toRadians(Board.segmentAngle(idx).toDouble())
    val r = when (hit.ring) {
        Ring.TREBLE -> (Geo.trebleIn + Geo.trebleOut) / 2f
        Ring.DOUBLE -> (Geo.doubleIn + Geo.doubleOut) / 2f
        else -> (Geo.trebleOut + Geo.doubleIn) / 2f
    }
    return Offset(r * cos(a).toFloat(), r * sin(a).toFloat())
}

/**
 * 501 against the machine. You throw the Dartless way — swipe to pick up, tap the target on the
 * beat — and the bot throws with an accuracy set by the difficulty. Legs and sets as in 2-player.
 */
@Composable
fun Bot501Screen(navController: NavHostController) {
    val context = LocalContext.current
    val difficulty = remember { Settings.difficulty(context) }
    val botAccuracy = when (difficulty) { Difficulty.EASY -> 0.55f; Difficulty.NORMAL -> 0.72f; Difficulty.HARD -> 0.88f }
    val botName = when (difficulty) { Difficulty.EASY -> "ROOKIE BOT"; Difficulty.NORMAL -> "PUB BOT"; Difficulty.HARD -> "PRO BOT" }
    val presets = remember { TimingPresets.load(context) }
    var presetName by remember { mutableStateOf(TimingPresets.selectedName(context) ?: presets.firstOrNull()?.name) }
    val preset: TimingPreset? = presets.firstOrNull { it.name == presetName }
    var presetMenuOpen by remember { mutableStateOf(false) }
    val model = remember { AccuracyModel() }

    var setupOpen by remember { mutableStateOf(true) }
    var startScore by remember { mutableStateOf(501) }
    var legsPerSet by remember { mutableStateOf(3) }
    var setsToWin by remember { mutableStateOf(1) }

    var sides by remember { mutableStateOf(listOf(Side("YOU", 501), Side(botName, 501))) }
    var current by remember { mutableStateOf(0) }
    var legStarter by remember { mutableStateOf(0) }
    var matchOver by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var botTurnKey by remember { mutableStateOf(0) }

    // Your visit
    var dartsInVisit by remember { mutableStateOf(0) }
    var visitStart by remember { mutableStateOf(501) }
    val marks = remember { mutableStateListOf<Offset>() }
    val thrown = remember { mutableStateListOf<Hit>() }
    var accuracy by remember { mutableStateOf(0.8f) }
    var throwStartMs by remember { mutableStateOf(0L) }
    var lastTapMs by remember { mutableStateOf(0L) }
    var pauseSec by remember { mutableStateOf(-1f) }
    var timingNote by remember { mutableStateOf("") }

    // Effects
    var popTrigger by remember { mutableStateOf(0) }
    var popText by remember { mutableStateOf("") }
    var popHuge by remember { mutableStateOf(false) }
    var popOrigin by remember { mutableStateOf(Offset.Zero) }
    var bustTrigger by remember { mutableStateOf(0) }
    var bustOrigin by remember { mutableStateOf(Offset.Zero) }
    var coachOpen by remember { mutableStateOf(false) }
    var boardPos by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val panelPos = remember { mutableStateListOf(Offset.Zero, Offset.Zero) }
    val panelSize = remember { mutableStateListOf(IntSize.Zero, IntSize.Zero) }

    fun boardPoint(nx: Float, ny: Float): Offset {
        val cx = boardSize.width / 2f
        val cy = boardSize.height / 2f
        val r = minOf(boardSize.width, boardSize.height) / 2f / RIM_SCALE
        return Offset(boardPos.x + cx + nx * r, boardPos.y + cy + ny * r)
    }
    fun panelCentre(i: Int) = Offset(panelPos[i].x + panelSize[i].width / 2f, panelPos[i].y + panelSize[i].height / 2f)

    fun celebrate(i: Int, score: Int, finished: Boolean) {
        val text = when {
            finished && score >= 100 -> "$score OUT!"
            score == 180 -> "180!"
            score >= 140 -> "$score"
            score >= 100 -> "TON ${if (score == 100) "" else (score - 100).toString()}".trim()
            else -> return
        }
        popText = text; popHuge = score == 180 || (finished && score >= 100); popOrigin = panelCentre(i); popTrigger++
        if (popHuge) Sounds.playCheckoutJingle()
    }

    fun legsNeeded() = legsPerSet / 2 + 1

    fun resetVisit() {
        dartsInVisit = 0; visitStart = sides[0].remaining
        marks.clear(); thrown.clear()
        throwStartMs = 0L; lastTapMs = 0L; pauseSec = -1f
    }

    fun newLeg() {
        for (s in sides) { s.remaining = startScore; s.darts = 0; s.scored = 0 }
        legStarter = 1 - legStarter
        current = legStarter
        resetVisit()
        if (current == 1) botTurnKey++
        message = if (current == 0) "Your leg to start — swipe to pick up" else "$botName starts the leg"
        version++
    }

    fun startMatch() {
        sides = listOf(Side("YOU", startScore), Side(botName, startScore))
        current = 0; legStarter = 0; matchOver = false
        resetVisit()
        message = "Game on — swipe up from the arrow to pick up a dart"
        setupOpen = false
        version++
    }

    /** Handles a leg won by side [i]; returns true if the match is over. */
    fun legWon(i: Int, visitScore: Int, dartsThisLeg: Int) {
        val s = sides[i]
        val o = sides[1 - i]
        s.legs++
        celebrate(i, visitScore, finished = true)
        var text = "${s.name} takes the leg in $dartsThisLeg darts"
        if (s.legs >= legsNeeded()) {
            s.sets++; s.legs = 0; o.legs = 0
            text = "${s.name} wins the set!"
            if (s.sets >= setsToWin) {
                matchOver = true
                text = if (i == 0) "YOU WIN THE MATCH!" else "$botName wins the match"
                popText = if (i == 0) "WINNER" else "LOST"; popHuge = true; popOrigin = panelCentre(i); popTrigger++
                if (i == 0) Sounds.playCheckoutJingle() else Sounds.playBust()
            }
        }
        message = text
        version++
        if (!matchOver) newLeg()
    }

    fun endUserVisit() {
        dartsInVisit = 3
        throwStartMs = 0L; lastTapMs = 0L; pauseSec = -1f
        current = 1
        botTurnKey++
        version++
    }

    fun armThrow() {
        if (matchOver || current != 0 || preset == null) return
        val now = System.currentTimeMillis()
        pauseSec = if (lastTapMs != 0L) (now - lastTapMs) / 1000f else -1f
        throwStartMs = now
        message = "Tap your target when the ring is back at the centre"
    }

    fun userThrow(aim: Offset) {
        if (matchOver || current != 0) return
        val p = preset ?: run { message = "Pick a timing preset first"; return }
        if (throwStartMs == 0L) { message = "Swipe up from the arrow first"; return }
        val now = System.currentTimeMillis()
        if (dartsInVisit >= 3) { dartsInVisit = 0; marks.clear(); thrown.clear(); visitStart = sides[0].remaining }
        // Pace -> accuracy (same rules as Dartless Checkout)
        val elapsed = (now - throwStartMs) / 1000f
        val throwAcc = throwAccuracy(elapsed, p.dart, difficulty)
        val rhythm = if (pauseSec >= 0f) pauseFactor(pauseSec, p.dart, difficulty) else 1f
        accuracy = (throwAcc * rhythm).coerceIn(0.2f, 1f)
        val off = kotlin.math.abs(elapsed - p.dart)
        timingNote = (if (off <= paceWindow(p.dart, difficulty)) "on pace" else if (elapsed < p.dart) "${formatSec(off)} s early" else "${formatSec(off)} s late") +
            (if (pauseSec >= 0f && rhythm < 0.999f) " · hesitated" else "") + "  →  ${(accuracy * 100).toInt()}%"
        throwStartMs = 0L
        lastTapMs = now

        val (lx, ly) = model.land(aim.x, aim.y, accuracy, difficulty.scatterScale)
        val hit = Board.hitTest(lx, ly, Geo)
        marks.add(Offset(lx, ly)); thrown.add(hit)
        dartsInVisit++
        val me = sides[0]
        me.darts++
        val newRem = me.remaining - hit.score
        when {
            newRem == 0 && hit.isDoubleOut -> {
                me.scored += hit.score; me.remaining = 0
                val visit = thrown.sumOf { it.score }
                legWon(0, visit, me.darts)
            }
            newRem < 0 || newRem == 1 || newRem == 0 -> {
                me.remaining = visitStart
                // undo the scoring of this visit's earlier darts
                me.scored -= thrown.dropLast(1).sumOf { it.score }
                message = "${hit.label} — BUST, back to $visitStart"
                bustOrigin = boardPoint(lx, ly); bustTrigger++
                Sounds.playBust()
                endUserVisit()
            }
            else -> {
                me.scored += hit.score; me.remaining = newRem
                message = "${hit.label} (${hit.score})"
                if (dartsInVisit >= 3) {
                    val visit = thrown.sumOf { it.score }
                    message = "Visit $visit — $botName to throw"
                    celebrate(0, visit, finished = false)
                    endUserVisit()
                } else {
                    version++
                }
            }
        }
    }

    // Bot's visit: three darts, animated
    LaunchedEffect(botTurnKey) {
        if (botTurnKey == 0 || matchOver || current != 1) return@LaunchedEffect
        val bot = sides[1]
        val botMarks = mutableListOf<Offset>()
        marks.clear(); thrown.clear()
        delay(900)
        val start = bot.remaining
        var visit = 0
        var done = false
        for (d in 1..3) {
            val aim = botTarget(bot.remaining)
            val tp = targetPoint(aim)
            val (lx, ly) = model.land(tp.x, tp.y, botAccuracy, 1f)
            val hit = Board.hitTest(lx, ly, Geo)
            marks.add(Offset(lx, ly)); thrown.add(hit)
            bot.darts++
            val newRem = bot.remaining - hit.score
            when {
                newRem == 0 && hit.isDoubleOut -> {
                    bot.scored += hit.score; bot.remaining = 0; visit += hit.score
                    message = "$botName: ${hit.label} — game shot"
                    version++
                    delay(900)
                    legWon(1, visit, bot.darts)
                    done = true
                }
                newRem < 0 || newRem == 1 || newRem == 0 -> {
                    bot.remaining = start; bot.scored -= visit; visit = 0
                    message = "$botName: ${hit.label} — bust"
                    version++
                    delay(900)
                    done = true
                }
                else -> {
                    bot.scored += hit.score; bot.remaining = newRem; visit += hit.score
                    message = "$botName: ${hit.label} (${hit.score})"
                    version++
                    delay(850)
                }
            }
            if (done) break
        }
        if (!matchOver && current == 1 && sides[1].remaining > 0) {
            if (!done) { celebrate(1, visit, finished = false); message = "$botName scores $visit — your throw, swipe to pick up" }
            else message += "  ·  your throw"
            current = 0
            resetVisit()
            version++
        }
    }

    if (coachOpen) {
        CoachTipDialog(remaining = sides[0].remaining, playerName = "You", onDismiss = { coachOpen = false })
    }

    if (setupOpen) {
        AlertDialog(
            onDismissRequest = { if (sides[0].darts == 0 && sides[1].darts == 0) navController.popBackStack() else setupOpen = false },
            title = { Text("501 vs $botName", fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Gold) },
            text = {
                Column {
                    OptionRow("Game", listOf(301, 501), startScore, { it.toString() }) { startScore = it }
                    OptionRow("Legs per set", listOf(1, 3, 5, 7), legsPerSet, { "$it" }) { legsPerSet = it }
                    OptionRow("Sets to win", listOf(1, 2, 3, 5), setsToWin, { "$it" }) { setsToWin = it }
                    Text(
                        "Bot strength follows the difficulty in Settings (${difficulty.label}). You throw with the swipe-and-tap rhythm from Dartless Checkout.",
                        fontSize = 12.sp, color = Grey, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { Button(onClick = { startMatch() }) { Text("Game on") } },
            dismissButton = { TextButton(onClick = { navController.popBackStack() }) { Text("Cancel", color = Grey) } }
        )
    }

    val inHand = when {
        matchOver || current != 0 -> 0
        dartsInVisit >= 3 -> if (throwStartMs != 0L) 3 else 0
        else -> 3 - dartsInVisit
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 120.dp)) {
            ScreenHeader(if (startScore == 301) "301 · 1 Player" else "501 · 1 Player", navController) {
                IconButton(onClick = { coachOpen = true }, modifier = Modifier.size(40.dp)) { CoachHead(modifier = Modifier.size(34.dp)) }
                TextButton(onClick = { setupOpen = true }) { Text("Match", color = Gold) }
            }
            if (version < 0) Text("")

            // Two compact panels
            for (i in 0..1) {
                val s = sides[i]
                val active = i == current && !matchOver
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                        .background(if (active) Charcoal else Black)
                        .onGloballyPositioned { panelPos[i] = it.positionInRoot(); panelSize[i] = it.size }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(96.dp)) {
                        Text(s.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = if (active) Gold else Grey, maxLines = 1)
                        Text(s.remaining.toString(), fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = if (active) Gold else OffWhite)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        PowerBar(value = s.remaining.toFloat() / startScore, enabled = false, onChange = {}, modifier = Modifier.fillMaxWidth().height(18.dp))
                        Text(
                            "S ${s.sets}  L ${s.legs}   avg ${"%.1f".format(s.average)}" +
                                (if (s.remaining in 2..170 && CheckoutLogic.isFinishable(s.remaining)) "   ${CheckoutLogic.tip(s.remaining)}" else ""),
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = if (active) PaleGold else Grey, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            // Accuracy + preset
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ACCURACY", fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Grey, modifier = Modifier.width(72.dp))
                PowerBar(value = accuracy, enabled = false, onChange = {}, modifier = Modifier.weight(1f).height(20.dp))
                Box {
                    TextButton(onClick = { presetMenuOpen = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(preset?.name ?: "Preset", fontSize = 11.sp, color = if (preset != null) PaleGold else Gold, maxLines = 1)
                    }
                    DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                        for (p in presets) DropdownMenuItem(text = { Text(p.name) }, onClick = { presetName = p.name; TimingPresets.setSelected(context, p.name); presetMenuOpen = false })
                    }
                }
            }

            // Fixed-height status
            Column(modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message.ifEmpty { " " }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(22.dp))
                Text(
                    if (current == 0) "Dart ${if (dartsInVisit >= 3) 3 else dartsInVisit}/3   " + (timingNote.ifEmpty { "Visit: " + thrown.joinToString(" ") { it.label }.ifEmpty { "—" } })
                    else "$botName throwing…   " + thrown.joinToString(" ") { it.label },
                    fontSize = 12.sp, color = PaleGold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(20.dp)
                )
            }

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp).onGloballyPositioned {
                boardPos = it.positionInRoot(); boardSize = it.size
            }) {
                Dartboard(geometry = Geo, marks = marks, onTap = { userThrow(it) })
                ThrowRing(startMs = if (current == 0 && !matchOver) throwStartMs else 0L, periodSec = preset?.dart ?: 0f, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }

        SwipeToThrowZone(
            armed = throwStartMs != 0L,
            enabled = current == 0 && !matchOver && preset != null,
            onSwipe = { armThrow() },
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(110.dp)
        )
        DartsInHand(inHand = inHand, modifier = Modifier.align(Alignment.BottomStart).padding(start = 122.dp, bottom = 20.dp).size(width = 120.dp, height = 80.dp))
        if (matchOver) {
            Button(onClick = { setupOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Text("New match") }
        }

        BigPop(trigger = popTrigger, text = popText, huge = popHuge, origin = popOrigin, modifier = Modifier.fillMaxSize())
        BustOverlay(trigger = bustTrigger, origin = bustOrigin, modifier = Modifier.fillMaxSize())
    }
}
