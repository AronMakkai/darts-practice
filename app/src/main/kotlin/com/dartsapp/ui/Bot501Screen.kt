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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.dartsapp.logic.Announcer
import com.dartsapp.logic.Banter
import com.dartsapp.logic.TimingPreset
import com.dartsapp.logic.TimingPresets
import com.dartsapp.logic.Tournament
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val Geo = BoardGeometry.PRACTICE
private const val HOT_TAPS = 3      // perfect darts in a row to light the first HOT STREAK (one more each time)
private const val HOT_DARTS = 4     // darts the fat board lasts

private class Side(val name: String, var remaining: Int, var legs: Int = 0, var sets: Int = 0, var darts: Int = 0, var scored: Int = 0) {
    val average: Float get() = if (darts == 0) 0f else scored * 3f / darts
}

/** A flashy finish: ends on the bull, or has two doubles in it. */
private fun isFlashy(route: List<Hit>) = route.last().ring == Ring.BULL || route.count { it.ring == Ring.DOUBLE } >= 2

/**
 * Where the bot aims for a given remaining score: the book route when in range, otherwise T20.
 * Returns the dart and whether it is a finishing-route dart. A [flair] player picks the showiest
 * route the book offers.
 */
private fun botTarget(remaining: Int, flair: Boolean): Pair<Hit, Boolean> {
    if (remaining <= 170) {
        val routes = CheckoutLogic.allFinishes(remaining)
        val route = (if (flair) routes.firstOrNull { isFlashy(it) } else null) ?: routes.firstOrNull()
        if (route != null) return route.first() to true
        // Bogey: take the single that leaves an even double
        return Hit(if (remaining - 20 >= 2) 20 else 1, Ring.SINGLE) to false
    }
    // Above 170: follow the setup route's first dart (keeps the bot off the bogeys)
    val advice = CheckoutLogic.setupAdvice(remaining)
    val first = advice?.route?.split(" ")?.firstOrNull() ?: "T20"
    return parseDart(first) to false
}

private fun parseDart(label: String): Hit = when {
    label == "25" -> Hit(25, Ring.OUTER_BULL)
    label == "Bull" -> Hit(25, Ring.BULL)
    label.startsWith("T") -> Hit(label.drop(1).toIntOrNull() ?: 20, Ring.TREBLE)
    label.startsWith("D") -> Hit(label.drop(1).toIntOrNull() ?: 20, Ring.DOUBLE)
    label.startsWith("S") -> Hit(label.drop(1).toIntOrNull() ?: 20, Ring.SINGLE)
    else -> Hit(20, Ring.TREBLE)
}

/** A character's accuracy for a match, before visit-to-visit form: base skill scaled by the difficulty setting. */
internal fun botBaseAccuracy(opponent: Opponent, difficulty: Difficulty, multiplier: Float = 1f): Float =
    (opponent.skill * multiplier * when (difficulty) { Difficulty.EASY -> 0.78f; Difficulty.NORMAL -> 1.0f; Difficulty.HARD -> 1.1f }).coerceIn(0.3f, 0.96f)

/** One dart from [opponent] with [remaining] left: where he aims, and where it lands (same rules as 501 vs bot). */
internal fun botThrow(opponent: Opponent, remaining: Int, accuracy: Float, form: Float, model: AccuracyModel): Hit {
    val (aim, finishingDart) = botTarget(remaining, opponent.flair)
    val tp = targetPoint(aim)
    val dartAcc = (accuracy * form * (if (finishingDart) opponent.finishing else opponent.scoring)).coerceIn(0.25f, 0.97f)
    val (lx, ly) = model.land(tp.x, tp.y, dartAcc, opponent.scatter)
    return Board.hitTest(lx, ly, Geo)
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
 * 501 against the machine. You throw the Checkout Game way — swipe to pick up, tap the target on the
 * beat — and the bot throws with an accuracy set by the difficulty. Legs and sets as in 2-player.
 */
@Composable
fun Bot501Screen(navController: NavHostController, tournament: Boolean = false) {
    val context = LocalContext.current
    val difficulty = remember { Settings.difficulty(context) }
    val aimOpacity = remember { Settings.aimOpacity(context) }
    var perfectTrigger by remember { mutableStateOf(0) }
    var hotThrows by remember { mutableStateOf(0) }      // consecutive on-pace throws -> ring heat
    var hotDartsLeft by remember { mutableStateOf(0) }   // > 0 while HOT STREAK is on (fat beds)
    var hotNeeded by remember { mutableStateOf(HOT_TAPS) } // perfect darts needed for the next streak; grows each time
    var banterKey by remember { mutableStateOf(0) }       // bumps when a match ends -> the opponent has a word
    var banterText by remember { mutableStateOf("") }
    var banterOpen by remember { mutableStateOf(false) }
    val hot = hotDartsLeft > 0
    // Opponent's special power: a splash screen, then its effect for the rest of the leg
    var powerSplash by remember { mutableStateOf(false) }
    var powerActive by remember { mutableStateOf(false) }
    var powerUsed by remember { mutableStateOf(false) }       // once per leg
    var bioOpen by remember { mutableStateOf(false) }          // the opponent's bio card
    var pickerBio by remember { mutableStateOf<Opponent?>(null) } // bio opened from the opponent picker
    var lizardEpoch by remember { mutableStateOf(0L) }        // when the Lizzard let his lizards loose
    var camTrigger by remember { mutableStateOf(0) }          // game-shot replay: zoom and slow-motion dart
    var camPoint by remember { mutableStateOf(Offset.Zero) }
    var camBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var coachAngle by remember { mutableStateOf(180f) }       // the Coach's board rotation; re-rolled after every dart of yours
    val boardBrightness = 0.11f                               // how much light is left under the Viking's LIGHTS OUT
    val glareStrength = 1f                                    // the Bling's glare, full strength
    // The Cockney's trembling aim ring: full shake on a double, less higher up
    val nervesBase = 0.75f
    val nervesSpeed = 1.5f
    // The Jockey's drunk board: tilt/slide/stretch, ripple distortion, and how fast it moves
    val drunkSway = 0.5f
    val drunkWave = 0.5f
    val drunkSpeed = 1.1f
    // Opponent: a character with a base skill, scaled by the difficulty setting
    val tourRound = if (tournament) Tournament.round else 0
    var opponent by remember {
        mutableStateOf(
            if (tournament) (Tournament.currentOpponent() ?: Opponent.MULLET)
            else Opponent.values()[Settings.opponentIndex(context).coerceIn(0, Opponent.values().size - 1)]
                .let { if (it == Opponent.COACH) Opponent.MULLET else it }   // the Coach only plays in tournaments
        )
    }
    val tourMul = if (tournament) Tournament.skillMultiplier(tourRound) else 1f
    val botAccuracy = (opponent.skill * tourMul * when (difficulty) { Difficulty.EASY -> 0.78f; Difficulty.NORMAL -> 1.0f; Difficulty.HARD -> 1.1f }).coerceIn(0.3f, 0.96f)
    val botName = opponent.displayName
    val presets = remember { TimingPresets.load(context) }
    val presetName = remember { TimingPresets.selectedOrDefault(context, presets) }   // the pace from Settings
    val preset: TimingPreset? = presets.firstOrNull { it.name == presetName }
    val model = remember { AccuracyModel() }

    var setupOpen by remember { mutableStateOf(!tournament) }
    var startScore by remember { mutableStateOf(501) }
    var legsPerSet by remember { mutableStateOf(if (tournament) Tournament.legsPerSet(tourRound) else 3) }
    var setsToWin by remember { mutableStateOf(1) }
    var tourReported by remember { mutableStateOf(false) }

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
    var popScore by remember { mutableStateOf(100) }
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
        popText = text; popHuge = score == 180 || (finished && score >= 100); popScore = score
        // The bubble blows up where the last dart of the visit landed
        popOrigin = marks.lastOrNull()?.let { boardPoint(it.x, it.y) } ?: panelCentre(i)
        popTrigger++
        if (popHuge) Sounds.playCheckoutJingle()
        Sounds.cheer(big = popHuge)
        if (finished) Announcer.checkout(score) else Announcer.score(score)
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
        powerActive = false; powerUsed = false
        current = legStarter
        resetVisit()
        if (current == 1) botTurnKey++
        message = if (current == 0) "Your leg to start — swipe to pick up" else "$botName starts the leg"
        version++
    }

    fun startMatch() {
        sides = listOf(Side("YOU", startScore), Side(botName, startScore))
        current = 0; legStarter = 0; matchOver = false
        powerActive = false; powerUsed = false; powerSplash = false
        resetVisit()
        message = "Game on — swipe up from the arrow to pick up a dart"
        setupOpen = false
        Announcer.gameOn()
        version++
    }
    if (tournament) LaunchedEffect(Unit) { if (sides[0].darts == 0 && sides[1].darts == 0 && !matchOver) startMatch() }

    /** Handles a leg won by side [i]; returns true if the match is over. */
    fun legWon(i: Int, visitScore: Int, dartsThisLeg: Int) {
        val s = sides[i]
        val o = sides[1 - i]
        s.legs++
        celebrate(i, visitScore, finished = true)
        if (visitScore < 100) { Announcer.gameShot(); Sounds.cheer(big = true) }
        var text = "${s.name} takes the leg in $dartsThisLeg darts"
        if (s.legs >= legsNeeded()) {
            s.sets++; s.legs = 0; o.legs = 0
            text = "${s.name} wins the set!"
            if (s.sets >= setsToWin) {
                matchOver = true
                text = if (i == 0) "YOU WIN THE MATCH!" else "$botName wins the match"
                if (tournament && !tourReported) {
                    tourReported = true; Tournament.recordUserResult(i == 0)
                    if (Tournament.youWon) Settings.setCoachUnlocked(context, true)    // winning a tournament brings the Coach out to play
                }
                banterText = Banter.line(opponent, botWon = i == 1)
                banterKey++
                popText = if (i == 0) "WINNER" else "LOST"; popHuge = true; popScore = if (i == 0) 180 else 100; popOrigin = panelCentre(i); popTrigger++
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

    // Cue tones for an armed throw, exactly as in Checkout Game: a tick when the ring reaches the
    // edge, a beep when it is back at the centre (the ideal moment).
    LaunchedEffect(throwStartMs) {
        val period = preset?.dart ?: return@LaunchedEffect
        if (throwStartMs == 0L) return@LaunchedEffect
        Sounds.tick()
        val half = (period * 500).toLong()
        delay(half)
        Sounds.tick()
        delay(half)
        Sounds.beep()
    }

    fun armThrow() {
        if (matchOver || current != 0 || preset == null || camBusy) return
        val now = System.currentTimeMillis()
        pauseSec = if (lastTapMs != 0L) (now - lastTapMs) / 1000f else -1f
        throwStartMs = now
        message = "Tap your target when the ring is back at the centre"
    }

    fun userThrow(aim: Offset) {
        if (matchOver || current != 0 || camBusy) return
        val hotNow = hotDartsLeft > 0          // read the live state: the tap handler may hold a stale `hot`
        val p = preset ?: run { message = "Pick a game pace in Settings first"; return }
        if (throwStartMs == 0L) { message = "Swipe up from the arrow first"; return }
        val now = System.currentTimeMillis()
        if (dartsInVisit >= 3) { dartsInVisit = 0; marks.clear(); thrown.clear(); visitStart = sides[0].remaining }
        // Pace -> accuracy (same rules as Checkout Game)
        val elapsed = (now - throwStartMs) / 1000f
        val throwAcc = throwAccuracy(elapsed, p.dart, difficulty)
        val rhythm = if (pauseSec >= 0f) pauseFactor(pauseSec, p.dart, difficulty) else 1f
        accuracy = (throwAcc * rhythm).coerceIn(0.2f, 1f)
        val off = kotlin.math.abs(elapsed - p.dart)
        val perfect = off <= paceWindow(p.dart, difficulty)
        if (perfect) { perfectTrigger++; Sounds.starPop() }
        timingNote = (if (hotNow) "🔥 HOT — ${hotDartsLeft} left" else if (perfect) "★ PERFECT" else if (elapsed < p.dart) "${formatSec(off)} s early" else "${formatSec(off)} s late") +
            (if (pauseSec >= 0f && rhythm < 0.999f) " · hesitated" else "") + "  →  ${(accuracy * 100).toInt()}%"
        throwStartMs = 0L
        lastTapMs = now

        // HOT STREAK: the beds are fat (HOT geometry) for a few darts; the throw itself is unchanged
        // THE PRESSURE (the Taylor): thinner trebles and doubles on your throws, for the rest of the leg
        val geo = if (hotNow) BoardGeometry.HOT else if (powerActive && opponent == Opponent.TACHE) BoardGeometry.TIGHT else Geo
        val (lx, ly) = model.land(aim.x, aim.y, accuracy, difficulty.scatterScale)
        val hit = Board.hitTest(lx, ly, geo)
        if (hotNow) {
            hotDartsLeft--
            if (hotDartsLeft == 0) { hotThrows = 0; hotNeeded++ }      // harder to light next time
        } else {
            // A "perfect dart" = on the beat AND in a treble, double or the bull. Counted in a row.
            val bigBed = hit.ring == Ring.TREBLE || hit.ring == Ring.DOUBLE || hit.ring == Ring.BULL
            hotThrows = if (perfect && bigBed) hotThrows + 1 else 0
            if (hotThrows >= hotNeeded) {
                hotDartsLeft = HOT_DARTS
                popText = "HOT STREAK!"; popHuge = true; popScore = 160; popOrigin = panelCentre(0); popTrigger++
                Sounds.playCheckoutJingle()
            }
        }
        Sounds.thud(); marks.add(Offset(lx, ly)); thrown.add(hit)
        coachAngle = 40f + kotlin.random.Random.nextFloat() * 280f   // never close to upright
        dartsInVisit++
        val me = sides[0]
        me.darts++
        // CREEPY CRAWLIES: a dart through a lizard still sticks in the board, but the visit is over — no score
        if (powerActive && opponent == Opponent.GOATEE && lizardHit(lizardEpoch, lx, ly, RIM_SCALE)) {
            me.remaining = visitStart
            me.scored -= thrown.dropLast(1).sumOf { it.score }
            message = "${hit.label} — straight through a lizard! NO SCORE"
            Announcer.say("No score!")
            Sounds.groan()
            endUserVisit()
            return
        }
        val newRem = me.remaining - hit.score
        when {
            newRem == 0 && hit.isDoubleOut -> {
                me.scored += hit.score; me.remaining = 0
                val visit = thrown.sumOf { it.score }
                // GAME SHOT: replay the winning dart — zoom in, slow motion — then take the leg
                camPoint = Offset(lx, ly); camTrigger++; camBusy = true
                message = "${hit.label} — GAME SHOT!"
                version++
                scope.launch {
                    delay((GAME_SHOT_SECONDS * 1000).toLong())
                    camBusy = false
                    legWon(0, visit, me.darts)
                }
            }
            newRem < 0 || newRem == 1 || newRem == 0 -> {
                me.remaining = visitStart
                // undo the scoring of this visit's earlier darts
                me.scored -= thrown.dropLast(1).sumOf { it.score }
                message = "${hit.label} — BUST, back to $visitStart"
                bustOrigin = boardPoint(lx, ly); bustTrigger++
                Sounds.playBust(); Sounds.groan()
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
        // Form for this visit: steady players barely swing, others drift a little either way
        val form = 1f + (kotlin.random.Random.nextFloat() * 2f - 1f) * opponent.jitter
        for (d in 1..3) {
            val (aim, finishingDart) = botTarget(bot.remaining, opponent.flair)
            val tp = targetPoint(aim)
            val dartAcc = (botAccuracy * form * (if (finishingDart) opponent.finishing else opponent.scoring)).coerceIn(0.25f, 0.97f)
            val (lx, ly) = model.land(tp.x, tp.y, dartAcc, opponent.scatter)
            val hit = Board.hitTest(lx, ly, Geo)
            Sounds.thud(); marks.add(Offset(lx, ly)); thrown.add(hit)
            bot.darts++
            val newRem = bot.remaining - hit.score
            when {
                newRem == 0 && hit.isDoubleOut -> {
                    bot.scored += hit.score; bot.remaining = 0; visit += hit.score
                    message = "$botName: ${hit.label} — game shot"
                    version++
                    camPoint = Offset(lx, ly); camTrigger++
                    delay((GAME_SHOT_SECONDS * 1000).toLong() + 200)
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
                    delay(opponent.paceMs)
                }
            }
            if (done) break
        }
        // The Cockney is the crowd's favourite: they roar his last dart whatever it scored (a ton gets its own cheer)
        if (opponent == Opponent.GRIN && visit < 100) Sounds.cheer(big = false)
        if (!matchOver && current == 1 && sides[1].remaining > 0) {
            if (!done) { celebrate(1, visit, finished = false); message = "$botName scores $visit — your throw, swipe to pick up" }
            else message += "  ·  your throw"
            current = 0
            resetVisit()
            version++
            // Being beaten with some margin: you are on a finish and he is 100+ behind -> special power
            if (opponent.powerName != null && !powerUsed && sides[0].remaining <= 170 && sides[1].remaining - sides[0].remaining >= 100) {
                powerUsed = true; powerSplash = true
            }
        }
    }

    if (coachOpen) {
        CoachTipDialog(remaining = sides[0].remaining, playerName = "You", onDismiss = { coachOpen = false })
    }
    // After the match: the opponent appears for a word
    LaunchedEffect(banterKey) {
        if (banterKey == 0) return@LaunchedEffect
        delay(1600)
        banterOpen = true
    }
    if (banterOpen) {
        BanterDialog(opponent = opponent, text = banterText, youWon = sides[0].sets >= setsToWin, onDismiss = { banterOpen = false })
    }

    if (bioOpen) OpponentBioDialog(opponent = opponent, onDismiss = { bioOpen = false })
    pickerBio?.let { OpponentBioDialog(opponent = it, onDismiss = { pickerBio = null }) }

    if (powerSplash) {
        PowerSplash(opponent = opponent, onDismiss = { powerSplash = false; powerActive = true; lizardEpoch = System.currentTimeMillis(); coachAngle = 40f + kotlin.random.Random.nextFloat() * 280f })
    }

    if (setupOpen) {
        AlertDialog(
            onDismissRequest = { if (sides[0].darts == 0 && sides[1].darts == 0) navController.popBackStack() else setupOpen = false },
            title = { Text("501 vs $botName", fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Gold) },
            text = {
                Column {
                    Text("Opponent", fontSize = 12.sp, color = Grey)
                    for (row in Opponent.values().filter { it != Opponent.COACH }.chunked(3)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (o in row) {
                                val sel = o == opponent
                                Box(
                                    modifier = Modifier.size(58.dp)
                                        .background(if (sel) DarkRed else Charcoal)
                                        .border(2.dp, if (sel) Gold else Color.Transparent)
                                        // tap to pick him; tap him again for his bio
                                        .clickable { if (sel) pickerBio = o else { opponent = o; Settings.setOpponentIndex(context, o.ordinal) } },
                                    contentAlignment = Alignment.Center
                                ) { OpponentHead(o, modifier = Modifier.size(52.dp)) }
                            }
                        }
                    }
                    Text(opponent.blurb + "  (tap again for his bio)", fontSize = 11.sp, color = PaleGold, maxLines = 3, modifier = Modifier.padding(bottom = 6.dp))
                    OptionRow("Game", listOf(301, 501), startScore, { it.toString() }) { startScore = it }
                    OptionRow("Legs per set", listOf(1, 3, 5, 7), legsPerSet, { "$it" }) { legsPerSet = it }
                    OptionRow("Sets to win", listOf(1, 2, 3, 5), setsToWin, { "$it" }) { setsToWin = it }
                    Text(
                        "Opponent skill is scaled by the difficulty in Settings (${difficulty.label}). You throw with the swipe-and-tap rhythm from Checkout Game.",
                        fontSize = 12.sp, color = Grey, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { Button(onClick = { startMatch() }) { Text("Game on") } },
            dismissButton = { TextButton(onClick = { navController.popBackStack() }) { Text("Cancel", color = Grey) } }
        )
    }

    // The swipe takes a dart out of the row: while a throw is armed, one fewer is shown resting in the hand
    val armedNow = throwStartMs != 0L
    val inHand = when {
        matchOver || current != 0 -> 0
        dartsInVisit >= 3 -> if (armedNow) 2 else 0
        else -> (3 - dartsInVisit) - (if (armedNow) 1 else 0)
    }.coerceAtLeast(0)

    val camT = rememberGameShotClock(camTrigger)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 130.dp)) {
            ScreenHeader(
                if (tournament) "${Tournament.rounds[tourRound.coerceIn(0, 2)]} · first to ${legsPerSet / 2 + 1}" else if (startScore == 301) "301 · 1 Player" else "501 · 1 Player",
                navController
            ) {
                IconButton(onClick = { coachOpen = true }, modifier = Modifier.size(40.dp)) { CoachHead(modifier = Modifier.size(34.dp)) }
                if (!tournament) TextButton(onClick = { setupOpen = true }) { Text("Match", color = Gold) }
            }
            if (version < 0) Text("")

            // Two compact panels
            for (i in 0..1) {
                val s = sides[i]
                val active = i == current && !matchOver
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                        .background(if (active) Charcoal else Night)
                        .onGloballyPositioned { panelPos[i] = it.positionInRoot(); panelSize[i] = it.size }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (i == 1) OpponentHead(opponent, modifier = Modifier.size(38.dp).clickable { bioOpen = true }.padding(end = 4.dp))
                    Column(modifier = Modifier.width(if (i == 1) 74.dp else 88.dp)) {
                        Text(s.name, fontSize = 9.sp, lineHeight = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = if (active) Gold else Grey, maxLines = 1)
                        Text(s.remaining.toString(), fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = if (active) Gold else OffWhite)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        PowerBar(value = s.remaining.toFloat() / startScore, enabled = false, onChange = {}, modifier = Modifier.fillMaxWidth().height(14.dp), segmentColor = { frac -> if (frac * startScore < 170f) Color(0xFFFF7A1A) else BoardCream })
                        Text(
                            "S ${s.sets}  L ${s.legs}   avg ${"%.1f".format(s.average)}" +
                                (if (s.remaining in 2..170 && CheckoutLogic.isFinishable(s.remaining)) "   ${CheckoutLogic.tip(s.remaining)}" else ""),
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = if (active) PaleGold else Grey, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Accuracy + preset
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (hot) "HOT!" else "ACCURACY", fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = if (hot) Color(0xFFFF7A1A) else Grey, fontWeight = if (hot) FontWeight.Black else FontWeight.Normal, modifier = Modifier.width(72.dp))
                HotMeter(value = accuracy, hot = hot, modifier = Modifier.weight(1f).height(46.dp))
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

            Box(
                // A square that fits whatever height is left, so the board never spills up over the text
                modifier = Modifier.weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .align(Alignment.CenterHorizontally).padding(top = 6.dp, start = 4.dp, end = 4.dp).onGloballyPositioned {
                    boardPos = it.positionInRoot(); boardSize = it.size
                }.gameShotCamera(camT, camPoint.x, camPoint.y),
                contentAlignment = Alignment.Center
            ) {
                // ONE FOR THE ROAD: the Jockey gets you drunk — the board sways and ripples on your turn
                val drunkNow = powerActive && current == 0 && opponent == Opponent.MULLET
                // LOOK OVER THERE: the Coach spins the board to a new random angle after every dart you throw
                val flip by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (powerActive && current == 0 && opponent == Opponent.COACH) coachAngle else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500), label = "flip"
                )
                Dartboard(modifier = Modifier.drunk(drunkNow, drunkSway, drunkWave, drunkSpeed).graphicsLayer { rotationZ = flip }, geometry = if (hot && current == 0) BoardGeometry.HOT else if (powerActive && current == 0 && opponent == Opponent.TACHE) BoardGeometry.TIGHT else Geo, marks = marks, onTap = { userThrow(it) })
                // LIGHTS OUT: the Viking stands in the light on your turn. Drawn under the aim ring; taps pass through.
                val shade by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (powerActive && current == 0 && opponent == Opponent.BEARD) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500), label = "shade"
                )
                if (shade > 0f) {
                    // His shadow slides across the board from the left, soft at the leading edge
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        val dark = Color.Black.copy(alpha = 1f - boardBrightness)
                        val soft = size.width * 0.3f
                        val x = shade * (size.width + soft) - soft
                        if (x > 0f) drawRect(dark, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(x, size.height))
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(dark, Color.Transparent), startX = x, endX = x + soft),
                            topLeft = Offset(x.coerceAtLeast(0f), 0f),
                            size = androidx.compose.ui.geometry.Size((x + soft - x.coerceAtLeast(0f)).coerceAtLeast(0f), size.height)
                        )
                    }
                }
                // BLINDED BY THE BLING: glare off his jewellery washes the board out on your turn
                if (powerActive && current == 0 && opponent == Opponent.BLING) {
                    BlingGlare(strength = glareStrength, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                }
                // THE WIND-UP: the Cockney's crowd heckles you on your turn
                val windUp = powerActive && current == 0 && opponent == Opponent.GRIN
                if (windUp) HeckleBubbles(modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                // ...and the aim ring trembles: worse on a finish, worst when you are on a double
                val nervesNow = nervesBase * when { sides[0].remaining <= 40 -> 1f; sides[0].remaining <= 170 -> 0.65f; else -> 0.35f }
                // CREEPY CRAWLIES: the Lizzard's lizards wander over the board on your turn
                if (powerActive && current == 0 && opponent == Opponent.GOATEE) {
                    LizardSwarm(epochMs = lizardEpoch, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                }
                ThrowRing(
                    startMs = if (current == 0 && !matchOver) throwStartMs else 0L, periodSec = preset?.dart ?: 0f,
                    accuracy = accuracy, opacity = aimOpacity.opacity, heat = if (hot) 1f else hotThrows.toFloat() / hotNeeded,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).nerves(windUp, nervesNow, nervesSpeed)
                )
                PerfectPop(trigger = perfectTrigger, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                GameShotDart(camT, camPoint.x, camPoint.y, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }

        SwipeToThrowZone(
            armed = throwStartMs != 0L,
            enabled = current == 0 && !matchOver && preset != null,
            onSwipe = { armThrow() },
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(120.dp)
        )
        DartsInHand(inHand = inHand, modifier = Modifier.align(Alignment.BottomStart).padding(start = 132.dp, bottom = 22.dp).size(width = 132.dp, height = 90.dp))
        // TEMP test control: fire the opponent's special power by hand
        if (!matchOver && opponent.powerName != null) {
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(
                    onClick = { if (powerActive) powerActive = false else powerSplash = true },
                    border = BorderStroke(2.dp, Red), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Night, contentColor = OffWhite),
                    modifier = Modifier.height(34.dp)
                ) { Text(if (powerActive) "POWER OFF" else "POWER", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            }
        }
        if (matchOver) {
            if (tournament) Button(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Text("Back to the draw") }
            else Button(onClick = { setupOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Text("New match") }
        }

        BigPop(trigger = popTrigger, text = popText, huge = popHuge, origin = popOrigin, modifier = Modifier.fillMaxSize(), score = popScore)
        BustOverlay(trigger = bustTrigger, origin = bustOrigin, modifier = Modifier.fillMaxSize())
    }
}


/** Special-power splash: the opponent, furious, fills the screen and announces what he is about to do. */
@Composable
internal fun PowerSplash(opponent: Opponent, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xF0200000))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("SPECIAL POWER", fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, fontSize = 14.sp, color = PaleGold)
            Text(opponent.powerName ?: "", fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, fontWeight = FontWeight.Black, fontSize = 40.sp, color = Color(0xFFFF3B1F), textAlign = TextAlign.Center)
            OpponentHead(opponent, modifier = Modifier.padding(vertical = 12.dp).size(300.dp), angry = true)
            Box(modifier = Modifier.background(DarkRed).border(2.dp, Gold).padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(opponent.displayName, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, fontWeight = FontWeight.Black, color = Gold, fontSize = 18.sp)
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    .background(BubblePurple, CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                    .border(BorderStroke(3.dp, NeonPink), CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                    .padding(16.dp)
            ) {
                Text("\u201C" + opponent.powerLine + "\u201D", color = BubbleText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Text("tap to continue", fontSize = 11.sp, color = Grey, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** A character portrait that opens his bio card when tapped. */
@Composable
internal fun OpponentHeadWithBio(opponent: Opponent, modifier: Modifier = Modifier, showPower: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    OpponentHead(opponent, modifier = modifier.clickable { open = true })
    if (open) OpponentBioDialog(opponent = opponent, showPower = showPower, onDismiss = { open = false })
}

/** The opponent's bio card: portrait, hometown, his game and his special power. Tap anywhere to close. */
@Composable
internal fun OpponentBioDialog(opponent: Opponent, showPower: Boolean = true, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(NearBlack, CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .border(BorderStroke(2.dp, Gold), CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() }
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OpponentHead(opponent, modifier = Modifier.size(150.dp))
            Text(opponent.displayName, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, fontWeight = FontWeight.Black, color = Gold, fontSize = 20.sp)
            opponent.hometown?.let { Text("📍 $it", fontSize = 13.sp, color = PaleGold, modifier = Modifier.padding(top = 2.dp)) }
            Text(opponent.blurb, fontSize = 12.sp, color = Grey, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            Text("SPECIAL MOVE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Grey, modifier = Modifier.fillMaxWidth())
            Text(opponent.specialMove, fontSize = 14.sp, color = OffWhite, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            if (showPower && opponent.powerName != null) {
                Spacer(Modifier.height(10.dp))
                Text("SUPER POWER", fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Grey, modifier = Modifier.fillMaxWidth())
                Text(opponent.powerName, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Red, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                Text(opponent.powerInfo, fontSize = 14.sp, color = OffWhite, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                Text("Once a leg, when you're on a finish and he's 100 or more behind. Lasts the rest of the leg.", fontSize = 11.sp, color = Grey, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Text("tap to close", fontSize = 11.sp, color = Grey.copy(alpha = 0.7f), modifier = Modifier.padding(top = 14.dp))
        }
    }
}

/** The opponent's post-match word: big portrait, name plate and a speech bubble. Tap anywhere to close. */
@Composable
internal fun BanterDialog(opponent: Opponent, text: String, youWon: Boolean, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Speech bubble
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    .background(BubblePurple, CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                    .border(BorderStroke(3.dp, NeonPink), CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                    .padding(16.dp)
            ) {
                Text(text, color = BubbleText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            // Bubble tail
            androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 30.dp, height = 18.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width * 0.3f, size.height); close() }
                drawPath(path, NeonPink)
                val inner = androidx.compose.ui.graphics.Path().apply { moveTo(4f, 0f); lineTo(size.width - 6f, 0f); lineTo(size.width * 0.32f, size.height - 6f); close() }
                drawPath(inner, BubblePurple)
            }
            OpponentHead(opponent, modifier = Modifier.size(190.dp))
            Box(modifier = Modifier.background(if (youWon) Charcoal else DarkRed).border(2.dp, Gold).padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(opponent.displayName, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, fontWeight = FontWeight.Black, color = Gold, fontSize = 16.sp)
            }
            Text(if (youWon) "sore loser" else "winner, apparently", fontSize = 11.sp, color = Grey, modifier = Modifier.padding(top = 6.dp))
            Text("tap to continue", fontSize = 11.sp, color = Grey.copy(alpha = 0.7f), modifier = Modifier.padding(top = 14.dp))
        }
    }
}
