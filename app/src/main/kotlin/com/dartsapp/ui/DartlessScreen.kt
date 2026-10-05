package com.dartsapp.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
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
import com.dartsapp.data.Ring
import com.dartsapp.logic.AccuracyModel
import com.dartsapp.logic.CheckoutLogic
import com.dartsapp.logic.Sounds
import com.dartsapp.logic.Settings
import com.dartsapp.logic.Difficulty
import com.dartsapp.logic.TimingPreset
import com.dartsapp.logic.TimingPresets
import kotlinx.coroutines.delay

private val BoardGeo = BoardGeometry.PRACTICE

/** Window (seconds) around the ideal moment that counts as "on pace". */
internal fun paceWindow(period: Float, d: Difficulty = Difficulty.NORMAL): Float =
    minOf(maxOf(0.2f * period, 0.3f), period / 4f) * d.windowScale

/**
 * Accuracy of the throw itself: swipe (pick up) to tap (throw) compared with the preset's dart
 * time. Inside the window = 100 %. Early falls off gently (20 % at half a period early); late
 * plummets — holding the dart and dithering costs you fast (20 % at a quarter period late).
 */
internal fun throwAccuracy(elapsed: Float, period: Float, d: Difficulty = Difficulty.NORMAL): Float {
    val tol = paceWindow(period, d)
    val off = kotlin.math.abs(elapsed - period)
    if (off <= tol) return 1f
    val excess = off - tol
    return if (elapsed < period) (1f - 0.8f * excess / (period / 2f - tol).coerceAtLeast(0.05f)).coerceIn(0.2f, 1f)
    else (1f - 0.8f * excess / (period / 4f * d.lateScale)).coerceIn(0.2f, 1f)
}

/** Free pause allowed between a throw and the next pick-up before it counts as losing the rhythm. */
internal fun pauseAllowance(period: Float, d: Difficulty = Difficulty.NORMAL): Float = maxOf(0.25f * period, 0.4f) * d.pauseScale

/**
 * Rhythm factor from the pause between the previous tap and this swipe. Swipe-tap-swipe-tap with
 * no hesitation = 1.0. Stopping to think breaks the pace: the factor eases down to 0.4 over one
 * dart time of hesitation.
 */
internal fun pauseFactor(pauseSec: Float, period: Float, d: Difficulty = Difficulty.NORMAL): Float {
    val excess = (pauseSec - pauseAllowance(period, d)).coerceAtLeast(0f)
    return (1f - 0.6f * excess / period).coerceIn(0.4f, 1f)
}

/**
 * Dartless checkout: tap the board where you would aim. The accuracy slider adds random
 * scatter to where the dart actually lands, so at low accuracy T20 might become S1 or S5.
 *
 * Metronome mode: pick a timing preset learned in the Metronome screen. Swipe diagonally up from the
 * bottom-left corner to start a throw: a ring grows from the centre of the board to its edge and
 * shrinks back over the preset's dart time. Tap your target — ideally the moment the ring is back at
 * the centre. The closer to that moment, the more accurate the throw; the slider sets itself.
 */
@Composable
fun DartlessScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dartless", android.content.Context.MODE_PRIVATE) }
    val difficulty: Difficulty = remember { Settings.difficulty(context) }

    // Tutorial: shown automatically the first time, and from the "?" button
    var showTutorial by remember { mutableStateOf(!prefs.getBoolean("tutorialSeen", false)) }
    fun closeTutorial() { showTutorial = false; prefs.edit().putBoolean("tutorialSeen", true).apply() }

    // Perfect-rhythm streak (gold bars) and the all-time best
    var streak by remember { mutableStateOf(prefs.getInt("streak", 0)) }
    var best by remember { mutableStateOf(prefs.getInt("best", 0)) }
    fun saveStreak() { prefs.edit().putInt("streak", streak).putInt("best", best).apply() }

    var start by remember { mutableStateOf(CheckoutLogic.randomCheckout()) }
    var remaining by remember { mutableStateOf(start) }
    var visitStart by remember { mutableStateOf(start) }
    var dartsInVisit by remember { mutableStateOf(0) }
    var dartsTotal by remember { mutableStateOf(0) }
    var accuracy by remember { mutableStateOf(0.8f) }
    var message by remember { mutableStateOf("Swipe up from the bottom-left corner to start a throw") }
    var timingNote by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val marks = remember { mutableStateListOf<Offset>() }
    val thrown = remember { mutableStateListOf<Hit>() }
    val allThrown = remember { mutableStateListOf<Hit>() }     // every dart of this checkout, for the coach
    val allAimed = remember { mutableStateListOf<Hit>() }
    var busts by remember { mutableStateOf(0) }
    var coachOpen by remember { mutableStateOf(false) }
    val model = remember { AccuracyModel() }

    // Metronome mode
    var metronomeMode by remember { mutableStateOf(true) }   // Metronome is the default; Simple is the alternative
    var showTip by remember { mutableStateOf(false) }
    var throwStartMs by remember { mutableStateOf(0L) }   // 0 = no throw armed
    var lastTapMs by remember { mutableStateOf(0L) }      // previous throw in this visit, 0 = none
    var pauseSec by remember { mutableStateOf(-1f) }      // tap -> swipe pause for the armed throw, -1 = n/a
    var allOnBeat by remember { mutableStateOf(true) }     // no judged throw off the beat so far this checkout
    var judgedThrows by remember { mutableStateOf(0) }
    var starTrigger by remember { mutableStateOf(0) }
    var burstOrigin by remember { mutableStateOf(Offset.Zero) }      // screen px of the winning dart
    var bustTrigger by remember { mutableStateOf(0) }
    var bustOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardPos by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val presets = remember { TimingPresets.load(context) }
    var presetName by remember { mutableStateOf(TimingPresets.selectedName(context) ?: presets.firstOrNull()?.name) }
    val preset: TimingPreset? = presets.firstOrNull { it.name == presetName }
    var presetMenuOpen by remember { mutableStateOf(false) }

    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    DisposableEffect(Unit) { onDispose { toneGen.release() } }

    // Cue tones for an armed throw: a low tick when the ring reaches the edge, a high beep when it is
    // back at the centre (the ideal moment).
    LaunchedEffect(throwStartMs) {
        val period = preset?.dart ?: return@LaunchedEffect
        if (throwStartMs == 0L) return@LaunchedEffect
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 60)
        val half = (period * 500).toLong()
        delay(half)
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 60)
        delay(half)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
    }

    fun armThrow() {
        if (finished || !metronomeMode || preset == null) return
        val now = System.currentTimeMillis()
        pauseSec = if (lastTapMs != 0L) (now - lastTapMs) / 1000f else -1f
        throwStartMs = now
        message = "Tap your target when the ring is back at the centre"
    }

    fun idleMessage() = if (metronomeMode) "Swipe up from the bottom-left corner to start a throw" else "Simple mode — tap the board to throw"

    fun newCheckout() {
        if (!finished && dartsTotal > 3 && streak > 0) { streak /= 2; saveStreak() }
        start = if (metronomeMode) CheckoutLogic.randomCheckoutForStreak(streak, difficulty.checkoutShift) else CheckoutLogic.randomCheckout()
        remaining = start
        visitStart = start
        dartsInVisit = 0
        dartsTotal = 0
        finished = false
        message = idleMessage()
        timingNote = ""
        marks.clear()
        thrown.clear()
        allThrown.clear()
        allAimed.clear()
        busts = 0
        coachOpen = false
        throwStartMs = 0L
        lastTapMs = 0L
        pauseSec = -1f
        allOnBeat = true
        judgedThrows = 0
    }

    fun throwAt(aim: Offset) {
        if (finished) return
        if (metronomeMode && preset == null) { message = "Pick a timing preset first (top right)"; return }
        val now = System.currentTimeMillis()
        if (dartsInVisit >= 3) {
            dartsInVisit = 0
            visitStart = remaining
            marks.clear()
            thrown.clear()
        }

        // Metronome mode: a throw must be armed by the swipe. Two things are judged:
        //  - the throw: swipe -> tap against the preset's dart time (late is punished hard)
        //  - the rhythm: the pause between the previous tap and this swipe (hesitating costs)
        if (metronomeMode && preset != null) {
            if (throwStartMs == 0L) { message = "Swipe up from the bottom-left corner first"; return }
            val elapsed = (now - throwStartMs) / 1000f
            val off = kotlin.math.abs(elapsed - preset.dart)
            val throwAcc = throwAccuracy(elapsed, preset.dart, difficulty)
            val rhythm = if (pauseSec >= 0f) pauseFactor(pauseSec, preset.dart, difficulty) else 1f
            accuracy = (throwAcc * rhythm).coerceIn(0.2f, 1f)
            val onPace = off <= paceWindow(preset.dart, difficulty) && rhythm >= 0.999f
            // Star criteria are more forgiving than the accuracy curve: roughly on the beat
            // (twice the window) and no long think between throws (2.5x the free pause).
            val starPace = off <= minOf(paceWindow(preset.dart, difficulty) * 2f, preset.dart / 2f)
            val starRhythm = pauseSec < 0f || pauseSec <= pauseAllowance(preset.dart, difficulty) * 2.5f
            judgedThrows++
            if (!(starPace && starRhythm)) allOnBeat = false
            val throwNote = if (off <= paceWindow(preset.dart, difficulty)) "on pace" else if (elapsed < preset.dart) "${formatSec(off)} s early" else "${formatSec(off)} s late"
            val pauseNote = if (pauseSec >= 0f && rhythm < 0.999f) "  ·  hesitated ${formatSec(pauseSec)} s" else ""
            timingNote = "Throw $throwNote$pauseNote  →  accuracy ${(accuracy * 100).toInt()}%"
            throwStartMs = 0L
            lastTapMs = now
        }

        val target = Board.hitTest(aim.x, aim.y, BoardGeo)
        val (lx, ly) = model.land(aim.x, aim.y, accuracy, difficulty.scatterScale)
        val hit = Board.hitTest(lx, ly, BoardGeo)
        marks.add(Offset(lx, ly))
        thrown.add(hit)
        allThrown.add(hit)
        allAimed.add(target)
        dartsInVisit++
        dartsTotal++

        val hitText = if (target == hit) "Hit ${hit.label} (${hit.score})" else "Aimed ${target.label}, hit ${hit.label} (${hit.score})"
        val newRem = remaining - hit.score
        when {
            newRem == 0 && hit.isDoubleOut -> {
                remaining = 0
                finished = true
                coachOpen = true
                message = "$hitText — Checked out in $dartsTotal darts!"
                // Star: a clean checkout (no bust, done inside one visit) in metronome mode, with every
                // dart roughly on the beat and no long pause between throws.
                val perfect = metronomeMode && allOnBeat && judgedThrows == dartsTotal && busts == 0 && dartsTotal <= 3
                if (perfect) {
                    streak++
                    if (streak > best) best = streak
                    saveStreak()
                    message += "  Clean checkout!  ×$streak"
                    val cx = boardSize.width / 2f
                    val cy = boardSize.height / 2f
                    val r = minOf(boardSize.width, boardSize.height) / 2f / RIM_SCALE
                    burstOrigin = Offset(boardPos.x + cx + lx * r, boardPos.y + cy + ly * r)
                    starTrigger++
                    Sounds.playCheckoutJingle()
                } else if (streak > 0 && dartsTotal > 3) {
                    // A messy checkout (more than one visit) halves the pile; a merely imperfect one keeps it.
                    streak /= 2
                    saveStreak()
                    message += "  Gold halved."
                }
            }
            newRem < 0 || newRem == 1 || newRem == 0 -> {
                remaining = visitStart
                dartsInVisit = 3
                busts++
                message = "$hitText — BUST! Back to $visitStart"
                val cx = boardSize.width / 2f
                val cy = boardSize.height / 2f
                val r = minOf(boardSize.width, boardSize.height) / 2f / RIM_SCALE
                bustOrigin = Offset(boardPos.x + cx + lx * r, boardPos.y + cy + ly * r)
                bustTrigger++
                throwStartMs = 0L
                lastTapMs = 0L
                pauseSec = -1f
                Sounds.playBust()
            }
            else -> {
                remaining = newRem
                message = hitText
            }
        }
        if (dartsInVisit >= 3 || finished) lastTapMs = 0L
        if (metronomeMode && !finished && dartsInVisit < 3) message += "  ·  Swipe for the next dart"
    }

    val tip = remember(remaining) { if (remaining > 1) CheckoutLogic.tip(remaining) else "" }
    val dartNo = if (dartsInVisit >= 3) 3 else dartsInVisit

    if (showTutorial) DartlessTutorial(onClose = { closeTutorial() })

    if (coachOpen) {
        CoachDialog(
            start = start,
            thrown = allThrown.toList(),
            aimed = allAimed.toList(),
            busts = busts,
            onDismiss = { coachOpen = false; newCheckout() },
            onNext = { coachOpen = false; newCheckout() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = if (metronomeMode) 130.dp else 100.dp)
    ) {
        ScreenHeader("Dartless Checkout", navController) {
            TextButton(onClick = { showTutorial = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("?", color = Gold, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { showTip = !showTip }, enabled = !metronomeMode) { Text("Tip", color = if (showTip) Gold else Grey) }
            TextButton(onClick = { newCheckout() }) { Text("New", color = Gold) }
        }

        // Accuracy power bar — set by your timing in Metronome mode, draggable in Simple mode
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ACCURACY", fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Grey, modifier = Modifier.width(78.dp))
            PowerBar(
                value = accuracy,
                enabled = !metronomeMode,
                onChange = { accuracy = it },
                modifier = Modifier.weight(1f).height(26.dp)
            )
            Text(
                "${(accuracy * 100).toInt()}%",
                fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Gold,
                textAlign = TextAlign.End, modifier = Modifier.width(48.dp)
            )
        }

        // Mode toggle (left) · remaining (centre) · preset picker (right)
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Mode", fontSize = 12.sp, color = Grey)
                OutlinedButton(
                    onClick = {
                        metronomeMode = !metronomeMode
                        throwStartMs = 0L
                        lastTapMs = 0L
                        pauseSec = -1f
                        timingNote = ""
                        if (!finished) message = idleMessage()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (metronomeMode) "Metronome" else "Simple", fontSize = 13.sp, maxLines = 1, color = if (metronomeMode) Gold else OffWhite)
                }
            }
            Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Checkout $start", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(remaining.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (metronomeMode) "×$streak  ·  best $best" else "Preset", fontSize = 12.sp, color = if (metronomeMode) Gold else Grey, maxLines = 1)
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
        // Fixed-height text block so the board never moves, whatever is written above it.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp)
        ) {
            Text(
                message.ifEmpty { " " },
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(46.dp)
            )
            Text(
                "Dart $dartNo/3   Visit: " + thrown.joinToString(" ") { it.label }.ifEmpty { "—" },
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(22.dp)
            )
            Text(
                if (metronomeMode) timingNote.ifEmpty { " " } else if (showTip) tip else " ",
                fontSize = 13.sp, color = PaleGold, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(20.dp)
            )
        }

        Box(modifier = Modifier.fillMaxWidth().padding(8.dp).onGloballyPositioned {
            boardPos = it.positionInRoot()
            boardSize = it.size
        }) {
            Dartboard(
                geometry = BoardGeo,
                marks = marks,
                onTap = { throwAt(it) }
            )
            if (metronomeMode) {
                ThrowRing(
                    startMs = if (finished) 0L else throwStartMs,
                    periodSec = preset?.dart ?: 0f,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            }
        }

    }

    if (metronomeMode && streak > 0) {
        GoldBarStack(
            count = streak,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).width(44.dp).fillMaxHeight(0.62f)
        )
    }
    // Darts still in hand this visit. Between visits (all three thrown) they are shown as outlines
    // until you pick up again — the swipe in metronome mode, or the next tap otherwise.
    val inHand = when {
        finished -> 0
        dartsInVisit >= 3 -> if (throwStartMs != 0L) 3 else 0
        else -> 3 - dartsInVisit
    }
    if (metronomeMode) {
        SwipeToThrowZone(
            armed = throwStartMs != 0L,
            enabled = !finished && preset != null,
            onSwipe = { armThrow() },
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(120.dp)
        )
        DartsInHand(
            inHand = inHand,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 132.dp, bottom = 22.dp).size(width = 132.dp, height = 90.dp)
        )
    } else {
        DartsInHand(
            inHand = inHand,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp).size(width = 132.dp, height = 90.dp)
        )
    }
    StarBurst(trigger = starTrigger, origin = burstOrigin, modifier = Modifier.fillMaxSize())
    BustOverlay(trigger = bustTrigger, origin = bustOrigin, modifier = Modifier.fillMaxSize())
    }
}


/** Post-checkout coach: how you did it, how the book does it, and why. */
@Composable
internal fun CoachDialog(start: Int, thrown: List<Hit>, aimed: List<Hit>, busts: Int, onDismiss: () -> Unit, onNext: () -> Unit, nextLabel: String = "Next checkout") {
    val suggestion = remember(start) { CheckoutLogic.suggest(start) }
    val optimal = suggestion?.best
    val pointers = remember(start, thrown.size) { coachPointers(start, thrown, aimed, busts, suggestion) }
    Dialog(onDismissRequest = onDismiss) {
        // Tap anywhere or swipe down to close. The whole card is one big click target with no ripple.
        var dragY by remember { mutableStateOf(0f) }
        val interaction = remember { MutableInteractionSource() }
        Card(
            colors = CardDefaults.cardColors(containerColor = NearBlack),
            border = BorderStroke(2.dp, Gold),
            shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragY = 0f },
                        onVerticalDrag = { change, dy -> change.consume(); dragY += dy },
                        onDragEnd = { if (dragY > 60f) onDismiss() }
                    )
                }
                .clickable(interactionSource = interaction, indication = null) { onDismiss() }
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachHead(modifier = Modifier.size(84.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("COACH", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold, fontSize = 14.sp)
                        Text(
                            "Checkout $start in ${thrown.size} dart${if (thrown.size == 1) "" else "s"}" +
                                (optimal?.let { "  ·  book: ${it.size}" } ?: ""),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OffWhite
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("You threw", fontSize = 12.sp, color = Grey)
                Text(thrown.joinToString("  ") { it.label }, fontSize = 16.sp, color = OffWhite, fontFamily = FontFamily.Monospace)
                if (suggestion != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("The book", fontSize = 12.sp, color = Grey)
                    Text(CheckoutLogic.routeLabel(suggestion.best), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gold, fontFamily = FontFamily.Monospace)
                    Text(suggestion.bestWhy, fontSize = 13.sp, color = PaleGold, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("Pointers", fontSize = 12.sp, color = Grey)
                for (p in pointers) {
                    Text("•  $p", fontSize = 14.sp, color = OffWhite, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("tap anywhere to continue", fontSize = 11.sp, color = Grey, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun coachPointers(start: Int, thrown: List<Hit>, aimed: List<Hit>, busts: Int, s: CheckoutLogic.Suggestion?): List<String> {
    val out = mutableListOf<String>()
    if (s == null) return listOf("No three-dart finish exists from $start — getting it done at all is the job.")
    val optimal = s.best
    val extra = thrown.size - optimal.size
    if (extra <= 0 && busts == 0) {
        out.add("Textbook. ${optimal.size} dart${if (optimal.size == 1) "" else "s"}, no wasted throws — nothing to add.")
    } else {
        if (extra > 0) out.add("$extra dart${if (extra == 1) "" else "s"} more than the book route.")
    }
    if (busts > 0) out.add("You bust $busts time${if (busts == 1) "" else "s"}. When a treble would bust, take the single — it keeps you on a finish instead of resetting the visit.")
    val firstAim = aimed.firstOrNull()
    if (firstAim != null && firstAim != optimal.first()) {
        out.add("You opened on ${firstAim.label}; the book opens on ${optimal.first().label} so that one dart leaves ${start - optimal.first().score}.")
    }
    val finisher = thrown.lastOrNull()
    if (finisher != null && finisher.isDoubleOut) {
        val n = finisher.number
        if (finisher.ring == Ring.DOUBLE && n % 2 == 1) out.add("You finished on D$n, an odd double. Where you can, set up an even double (D16, D20, D8): a single there still leaves a double.")
        if (finisher != optimal.last() && s.alt != null && finisher == s.alt.last()) out.add("You took the alternative finish on ${finisher.label} — perfectly good, just a touch less forgiving than ${optimal.last().label}.")
    }
    val misses = thrown.zip(aimed).count { (hit, aim) -> hit != aim }
    if (misses > 0 && thrown.size > 1) out.add("$misses of ${thrown.size} darts landed off the target you aimed at. The rhythm sets the accuracy — settle before you throw.")
    if (out.isEmpty()) out.add("Good darts.")
    return out
}


/** The coach, mid-leg: what to throw from [remaining] and why. Tap anywhere to close. */
@Composable
internal fun CoachTipDialog(remaining: Int, playerName: String, onDismiss: () -> Unit) {
    val suggestion = remember(remaining) { if (remaining in 2..170) CheckoutLogic.suggest(remaining) else null }
    val setup = remember(remaining) { CheckoutLogic.setupAdvice(remaining) }
    val bogeyNote = remember(remaining) {
        if (remaining in 2..170 && suggestion == null) CheckoutLogic.tip(remaining) + ". No three-dart finish from here: take the single that leaves an even double." else ""
    }
    Dialog(onDismissRequest = onDismiss) {
        val interaction = remember { MutableInteractionSource() }
        Card(
            colors = CardDefaults.cardColors(containerColor = NearBlack),
            border = BorderStroke(2.dp, Gold),
            shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.fillMaxWidth().clickable(interactionSource = interaction, indication = null) { onDismiss() }
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachHead(modifier = Modifier.size(72.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("COACH", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold, fontSize = 14.sp)
                        Text("$playerName on $remaining", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OffWhite)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (suggestion != null) {
                    Text("Throw", fontSize = 12.sp, color = Grey)
                    Text(CheckoutLogic.routeLabel(suggestion.best), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold, fontFamily = FontFamily.Monospace)
                    Text(suggestion.bestWhy, fontSize = 14.sp, color = OffWhite, modifier = Modifier.padding(top = 4.dp))
                    if (suggestion.alt != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("Or", fontSize = 12.sp, color = Grey)
                        Text(CheckoutLogic.routeLabel(suggestion.alt), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PaleGold, fontFamily = FontFamily.Monospace)
                        Text(suggestion.altWhy, fontSize = 13.sp, color = Grey, modifier = Modifier.padding(top = 4.dp))
                    }
                } else if (setup != null) {
                    Text("Set up", fontSize = 12.sp, color = Grey)
                    Text("${setup.visit}  ·  ${setup.route}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold, fontFamily = FontFamily.Monospace)
                    Text("leaves ${setup.leaves}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PaleGold)
                    Text(setup.why, fontSize = 14.sp, color = OffWhite, modifier = Modifier.padding(top = 4.dp))
                    if (setup.warning != null) {
                        Text(setup.warning, fontSize = 13.sp, color = Red, modifier = Modifier.padding(top = 6.dp))
                    }
                } else {
                    Text(bogeyNote, fontSize = 14.sp, color = OffWhite)
                }
                Spacer(Modifier.height(12.dp))
                Text("tap anywhere to close", fontSize = 11.sp, color = Grey, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
