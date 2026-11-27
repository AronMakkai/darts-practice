package com.dartsapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where players can reach us: Play Store listing, and (when set) a feedback email address. */
object AppLinks {
    const val PACKAGE = "com.aronmakkai.metrodarts"
    /** Feedback address shown in Settings; leave blank to hide the "Send feedback" button. */
    const val FEEDBACK_EMAIL = ""

    /** Open the Play Store page for Metro Darts (the Play app if installed, else the web page). */
    fun openStorePage(ctx: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(market) } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { }
        }
    }

    /** Start an email to the developer with the app version filled in. */
    fun sendFeedback(ctx: Context) {
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Metro Darts feedback (v$version)")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(i) } catch (_: Exception) { }
    }
}

private data class TourPage(val coach: String, val title: String, val body: String)

private val TOUR = listOf(
    TourPage("Alright, champ! I'm the Coach.",
        "WELCOME TO METRO DARTS",
        "Darts is rhythm. Every game here is played to a beat, like a real throw: stance, aim, release."),
    TourPage("Here's the secret, listen up.",
        "HOW TO THROW",
        "Swipe up from the throw arrow to pick up a dart. The aiming ring runs out from the bull and back — tap your target as it returns to the centre. On the beat, the dart flies true; early or late, it drifts."),
    TourPage("Rhythm wins matches. Find yours.",
        "YOUR PACE",
        "Every game runs at a pace: how fast the ring sweeps and the beat between your darts. Set your own from your real throw: go to Darts IRL → MY THROWING PACE, press Record my own pace, and play a few turns at your natural speed, tapping as you finish each step. Save it and every game plays at your rhythm."),
    TourPage("This is where I earn my money.",
        "CHECKOUT GAME",
        "Random finishes from 2 to 170. Check out in one visit to build your streak. Hit perfect darts in a row to get IN THE ZONE — fatter trebles and doubles for four darts. I'll rate every checkout."),
    TourPage("Watch this lot. They play dirty.",
        "501, CHARACTERS & TOURNAMENT",
        "Play 501 against six characters, each with their own game — and a dirty super power when they're losing. Tap any portrait for his bio. Win the Tournament and someone special joins the next draw."),
    TourPage("And when you're ready for the real thing…",
        "DARTS IRL",
        "Got a real board? Score real games here: 2-player 501, 501 against a character, a checkout helper, and the Metronome to learn and record your own throwing rhythm."),
)

/**
 * First-launch walkthrough: the Coach stands beside a speech-bubble pop-up and talks you through a
 * few short pages. He bobs about while he talks and gives a little nod on every new page. Skip on every page.
 */
@Composable
fun WelcomeTour(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val p = TOUR[page]
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

    // The Coach: an idle bob and sway, plus a nod each time the page turns
    val idle = androidx.compose.animation.core.rememberInfiniteTransition(label = "coachIdle")
    val bob = idle.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse),
        label = "bob")
    val nod = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(page) {
        nod.snapTo(1f)
        nod.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 260f))
    }
    // ...and he pops in the first time
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 300f)) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            // The pop-up: a speech bubble from the Coach
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(BubblePurple, shape)
                    .border(BorderStroke(2.dp, NeonPink), shape)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (androidx.compose.animation.slideInHorizontally { it * dir / 3 } + androidx.compose.animation.fadeIn())
                            .togetherWith(androidx.compose.animation.slideOutHorizontally { -it * dir / 3 } + androidx.compose.animation.fadeOut())
                    },
                    label = "tourPage"
                ) { i ->
                    val q = TOUR[i]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("“${q.coach}”", color = NeonPink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(q.title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                            color = Gold, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(q.body, color = BubbleText, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(14.dp))
                // page dots (tap one to jump there)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in TOUR.indices) Box(
                        Modifier.size(if (i == page) 10.dp else 8.dp)
                            .background(if (i == page) NeonPink else Charcoal, CutCornerShape(2.dp))
                            .clickable { page = i }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) TextButton(onClick = { page-- }) { Text("Back", color = Grey) }
                    else TextButton(onClick = onDone) { Text("Skip", color = Grey) }
                    Spacer(Modifier.weight(1f))
                    if (page in 1 until TOUR.lastIndex) TextButton(onClick = onDone) { Text("Skip", color = Grey) }
                    Button(onClick = { if (page < TOUR.lastIndex) page++ else onDone() }) {
                        Text(if (page < TOUR.lastIndex) "Next" else "Let's play")
                    }
                }
            }
            // The bubble's tail, pointing down at the Coach
            androidx.compose.foundation.Canvas(modifier = Modifier.padding(start = 54.dp).size(width = 30.dp, height = 18.dp).offset(y = (-2).dp)) {
                val tail = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width * 0.15f, size.height); close()
                }
                drawPath(tail, BubblePurple)
                val edge = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                drawLine(NeonPink, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height), edge.width)
                drawLine(NeonPink, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height), edge.width)
            }
            // The Coach himself, with his nameplate
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                CoachHead(modifier = Modifier.size(104.dp).graphicsLayer {
                    val e = enter.value
                    scaleX = e; scaleY = e
                    translationY = (bob.value - 0.5f) * 6.dp.toPx() - nod.value * 8.dp.toPx()
                    rotationZ = (bob.value - 0.5f) * 4f + nod.value * 6f
                })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("THE COACH", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Gold, fontSize = 14.sp)
                    Text("${page + 1} of ${TOUR.size}", fontFamily = FontFamily.Monospace, color = Grey, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Gentle, one-off rating prompt after a milestone. "Never" and "Rate" both stop it for good. */
@Composable
fun RatePrompt(onRate: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Enjoying Metro Darts?") },
        text = { Text("If you have a moment, a rating on Google Play helps other darts players find the game. Thanks for playing!") },
        confirmButton = { Button(onClick = onRate) { Text("Rate it") } },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text("No thanks", color = Grey) }
                TextButton(onClick = onLater) { Text("Later", color = Grey) }
            }
        }
    )
}


/**
 * How far in from the left edge the swipe-to-throw arrow sits, from the setting (0 = the corner,
 * 1 = its centre lined up with the middle of the screen). Read once per screen.
 */
@Composable
fun throwButtonInset(): androidx.compose.ui.unit.Dp {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pos = remember { com.dartsapp.logic.Settings.throwButtonPos(ctx) }
    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val maxInset = (widthDp / 2f - 60f - 8f).coerceAtLeast(0f)     // 120 dp zone, 8 dp margin
    return (maxInset * pos).dp
}

/**
 * ...and the same setting lifts it up the screen towards the board as it comes in: from the bottom
 * corner at one end of the slider to about an eighth of the screen higher at the other.
 */
@Composable
fun throwButtonLift(): androidx.compose.ui.unit.Dp {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pos = remember { com.dartsapp.logic.Settings.throwButtonPos(ctx) }
    val heightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val maxLift = (heightDp * 0.13f).coerceIn(0f, 120f)
    return (maxLift * pos).dp
}


/** The dartboard never grows beyond this, so it is the same size on a tablet or unfolded phone as on a big phone. */
val BOARD_MAX = 470.dp

/**
 * Sideways, the board shares the screen with the stats: it never takes more than half the width and
 * stays a little smaller than upright, so on a big near-square screen (an unfolded Fold) it doesn't swamp them.
 */
fun landscapeBoardCap(screenWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    screenWidth * 0.6f

/**
 * On small screens (a narrow phone, or a foldable's cover screen) everything around the board —
 * scores, stats, bars, buttons, text — is drawn a bit smaller, so the board keeps its full width and
 * the rest still fits. Normal and large screens are untouched.
 */
@Composable
fun CompactOnSmallScreens(content: @Composable () -> Unit) {
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val w = conf.screenWidthDp; val h = conf.screenHeightDp
    val factor = when {
        w < 360 || h < 640 -> 0.80f
        w < 400 || h < 740 -> 0.88f
        else -> 1f
    }
    val d = androidx.compose.ui.platform.LocalDensity.current
    if (factor == 1f) {
        content()
    } else {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * factor, d.fontScale),
            content = content
        )
    }
}


/**
 * IN THE ZONE: a dark vignette closes in around the edge of the screen while it is on (taps pass
 * straight through), and the crowd and the caller go muffled, as if heard under water.
 */
@Composable
fun InTheZone(on: Boolean, modifier: Modifier = Modifier) {
    LaunchedEffect(on) {
        com.dartsapp.logic.Music.setMuffled(on)
        com.dartsapp.logic.Announcer.setMuffled(on)
    }
    DisposableEffect(Unit) {
        onDispose { com.dartsapp.logic.Music.setMuffled(false); com.dartsapp.logic.Announcer.setMuffled(false) }
    }
    val k by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(if (on) 700 else 500), label = "zone"
    )
    // a slow breathing pulse while in the zone
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "zonePulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse")
    if (k > 0f) androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        // An oval vignette fitted to the screen: a circle drawn in a square of the short side, then
        // stretched to the screen's shape, so the dark closes in on all four edges, not just the corners
        val m = minOf(size.width, size.height)
        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val inner = 0.5f - 0.05f * pulse.value
        val dark = Color(0xFF05000C)
        scale(scaleX = size.width / m, scaleY = size.height / m, pivot = c) {
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to Color.Transparent,
                    inner to Color.Transparent,
                    0.78f to dark.copy(alpha = 0.45f * k),
                    0.95f to dark.copy(alpha = 0.8f * k),
                    1f to dark.copy(alpha = 0.88f * k),
                    center = c, radius = m / 2f
                ),
                topLeft = androidx.compose.ui.geometry.Offset(c.x - m, c.y - m),
                size = androidx.compose.ui.geometry.Size(2f * m, 2f * m)
            )
        }
    }
}

/**
 * A rain of stars falling from the top of the screen for a few seconds after [trigger] changes:
 * gold, pink and teal, tumbling as they fall, at different sizes and speeds. Taps pass through.
 */
@Composable
fun StarRain(trigger: Int, modifier: Modifier = Modifier, count: Int = 140, seconds: Float = 5f) {
    if (trigger == 0) return
    var t by remember(trigger) { mutableStateOf(0f) }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (t < seconds + 2f) { val now = withFrameNanos { it }; t = (now - start) / 1e9f }
    }
    if (t > seconds + 2f) return
    val stars = remember(trigger) {
        val rnd = kotlin.random.Random(trigger * 31 + 7)
        List(count) {
            floatArrayOf(
                rnd.nextFloat(),                   // x across the screen
                rnd.nextFloat() * seconds,         // when it starts falling
                0.35f + rnd.nextFloat() * 0.5f,    // fall speed, screens a second
                0.6f + rnd.nextFloat() * 0.9f,     // size
                rnd.nextFloat() * 360f,            // spin start
                (rnd.nextFloat() - 0.5f) * 400f,   // spin speed
                rnd.nextInt(3).toFloat(),          // colour
                (rnd.nextFloat() - 0.5f) * 0.08f   // drift
            )
        }
    }
    val colours = listOf(Color(0xFFFFD34D), Color(0xFFFF4F8B), Color(0xFF3FE0D0))
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val unit = minOf(w, h) * 0.022f
        for (s in stars) {
            val age = t - s[1]
            if (age < 0f) continue
            val y = -unit * 2 + age * s[2] * h
            if (y > h + unit * 2) continue
            val x = (s[0] + s[7] * age) * w + kotlin.math.sin(age * 3f + s[4]) * unit
            val r = unit * s[3]
            val path = androidx.compose.ui.graphics.Path()
            for (k in 0 until 10) {
                val a = Math.toRadians((s[4] + s[5] * age + k * 36f).toDouble())
                val rr = if (k % 2 == 0) r else r * 0.45f
                val px = x + (rr * kotlin.math.cos(a)).toFloat(); val py = y + (rr * kotlin.math.sin(a)).toFloat()
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            val fade = if (t > seconds) (1f - (t - seconds) / 2f).coerceIn(0f, 1f) else 1f
            drawPath(path, colours[s[6].toInt()].copy(alpha = fade))
        }
    }
}
