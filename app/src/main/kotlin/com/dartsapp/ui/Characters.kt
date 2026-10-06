package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The bot opponents for 501 · 1 Player. Each has a flat-shaded vector portrait in the coach's style
 * and a base skill (hit accuracy, 0..1) that the difficulty setting then scales.
 *
 * Names: edit [displayName] to whatever you want shown on the scoreboard.
 */
enum class Opponent(
    val displayName: String,
    val skill: Float,
    val blurb: String,
    val scoring: Float = 1f,      // accuracy multiplier on scoring / setup darts
    val finishing: Float = 1f,    // accuracy multiplier on darts from the checkout book
    val scatter: Float = 1f,      // spread of a miss (lower = tighter, more consistent)
    val jitter: Float = 0.08f,    // visit-to-visit form swing (lower = steadier)
    val flair: Boolean = false,   // goes for bull and double-double finishes when the book allows
    val paceMs: Long = 850,       // time between the bot's darts
    val powerName: String? = null, // special power, played when he is being beaten (null = none yet)
    val powerLine: String = ""     // what he says on the splash screen
) {
    BEARD("THE VIKING", 0.74f, "Shaggy mane, full beard, a horn of ale never far away — moves at his own glacial pace", paceMs = 1400,
        powerName = "LIGHTS OUT",
        powerLine = "This is not looking good. Let me move my large frame over here and see how that affects your view of the board..."),
    GRIN("THE COCKNEY", 0.80f, "Mop top and a cheeky smile — scores for fun",
        powerName = "THE WIND-UP",
        powerLine = "Listen to that, son. Hear 'em? They ain't here for you. Let's see how steady that hand is with a few thousand of my mates in your ear..."),
    BLING("THE BLING", 0.76f, "Walk-on music, smoke machine, more gold than a pawnbroker — always after the bull and the double-double", flair = true,
        powerName = "BLINDED BY THE BLING",
        powerLine = "Not a shining situation.. Maybe I just hold my jewellery up like this and..."),
    MULLET("THE JOCKEY", 0.70f, "Feathered hair, leather jacket, eight pints deep — and somehow the steadiest arm in the pub", scatter = 0.6f, jitter = 0.02f,
        powerName = "ONE FOR THE ROAD",
        powerLine = "Have a pint, mate. Beer is living proof that God loves us and wants us to be happy."),
    GOATEE("THE LIZZARD", 0.84f, "Sun-bleached spikes, long goatee, straight outta the outback — deadly on combination finishes", finishing = 1.12f,
        powerName = "CREEPY CRAWLIES",
        powerLine = "Back home down under, mate, I used to practise with lizards crawlin' all over me board. Distractin' as. Oh, hang on \u2014 there's a couple in me goatee, lemme just..."),
    TACHE("THE TAYLOR", 0.88f, "Dark hair, neat moustache, Stoke-on-Trent through and through — the heaviest scorer in the room", scoring = 1.08f,
        powerName = "BIG BOY PANTS",
        powerLine = "I am 16 time Metro Darts champion.. Let me put the big boy pants on and see if you can take the pressure..."),
    COACH("THE COACH", 0.82f, "Your coach — swept-back hair, big grin, that Hawaiian shirt, knows every route in the book",
        powerName = "LOOK OVER THERE",
        powerLine = "This is not going my way. But being both whimsical and charming has its perks. Look over there...")
}

/** Portrait of [who], flat-shaded like the coach. Fits a square. */
@Composable
fun OpponentHead(who: Opponent, modifier: Modifier = Modifier, angry: Boolean = false) {
    if (who == Opponent.COACH) {
        Box(modifier = modifier) {
            CoachHead(modifier = Modifier.matchParentSize())
            if (angry) Canvas(modifier = Modifier.matchParentSize()) { angryOverlay() }
        }
    } else {
        Canvas(modifier = modifier) {
            when (who) {
                Opponent.BEARD -> drawBeard()
                Opponent.GRIN -> drawGrin()
                Opponent.BLING -> drawBling()
                Opponent.MULLET -> drawMullet()
                Opponent.GOATEE -> drawGoatee()
                Opponent.TACHE -> drawTache()
                Opponent.COACH -> {}
            }
            if (angry) angryOverlay()
        }
    }
}

/** Drawn over a portrait for the special-power splash: flushed face, heavy scowling brows, furrow. */
private fun DrawScope.angryOverlay() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val brow = Color(0xFF1A0F0C)
    drawOval(Color(0xFFD01818).copy(alpha = 0.28f), Offset(cx - w * 0.27f, h * 0.2f), Size(w * 0.54f, h * 0.5f))
    drawLine(brow, Offset(cx - w * 0.23f, h * 0.35f), Offset(cx - w * 0.04f, h * 0.45f), strokeWidth = h * 0.05f, cap = StrokeCap.Round)
    drawLine(brow, Offset(cx + w * 0.23f, h * 0.35f), Offset(cx + w * 0.04f, h * 0.45f), strokeWidth = h * 0.05f, cap = StrokeCap.Round)
    drawLine(brow, Offset(cx - w * 0.015f, h * 0.36f), Offset(cx - w * 0.015f, h * 0.42f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
    drawLine(brow, Offset(cx + w * 0.015f, h * 0.36f), Offset(cx + w * 0.015f, h * 0.42f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
}

// ---- shared pieces -------------------------------------------------------------------------

private class Skin(val base: Color, val dark: Color, val light: Color)

private val FAIR = Skin(Color(0xFFEFC4A0), Color(0xFFD3A077), Color(0xFFF8DCC2))
private val TAN = Skin(Color(0xFFE2A878), Color(0xFFC48A5B), Color(0xFFF0C296))
private val RUDDY = Skin(Color(0xFFE8AA8C), Color(0xFFC9826A), Color(0xFFF4C9B4))

/** Shirt with a collar: [body] colour, [collar] colour, optional [tee] under an open collar. */
private fun DrawScope.shirt(body: Color, collar: Color, tee: Color? = null) {
    val w = size.width; val h = size.height; val cx = w / 2f
    drawPath(Path().apply {
        moveTo(w * 0.06f, h); lineTo(w * 0.18f, h * 0.84f); lineTo(cx - w * 0.12f, h * 0.79f)
        lineTo(cx, h * 0.9f); lineTo(cx + w * 0.12f, h * 0.79f); lineTo(w * 0.82f, h * 0.84f); lineTo(w * 0.94f, h); close()
    }, body)
    if (tee != null) drawPath(Path().apply { moveTo(cx - w * 0.12f, h * 0.79f); lineTo(cx + w * 0.12f, h * 0.79f); lineTo(cx, h * 0.92f); close() }, tee)
    drawPath(Path().apply { moveTo(cx - w * 0.12f, h * 0.79f); lineTo(cx, h * 0.9f); lineTo(cx - w * 0.19f, h * 0.9f); close() }, collar)
    drawPath(Path().apply { moveTo(cx + w * 0.12f, h * 0.79f); lineTo(cx, h * 0.9f); lineTo(cx + w * 0.19f, h * 0.9f); close() }, collar)
}

/** Neck, ears, head oval, jaw block and the right-side shade. [jaw] widens the lower face. */
private fun DrawScope.face(s: Skin, jaw: Float = 0.44f, round: Float = 0f) {
    val w = size.width; val h = size.height; val cx = w / 2f
    drawRect(s.dark, Offset(cx - w * 0.1f, h * 0.7f), Size(w * 0.2f, h * 0.13f))
    drawOval(s.dark, Offset(cx - w * 0.31f, h * 0.44f), Size(w * 0.08f, h * 0.13f))
    drawOval(s.dark, Offset(cx + w * 0.23f, h * 0.44f), Size(w * 0.08f, h * 0.13f))
    drawOval(s.base, Offset(cx - w * (0.26f + round), h * 0.2f), Size(w * (0.52f + 2 * round), h * 0.58f))
    drawRect(s.base, Offset(cx - w * jaw / 2f, h * 0.5f), Size(w * jaw, h * 0.2f))
    drawPath(Path().apply {
        moveTo(cx + w * 0.06f, h * 0.22f); lineTo(cx + w * 0.2f, h * 0.3f); lineTo(cx + w * (0.26f + round), h * 0.5f)
        lineTo(cx + w * 0.2f, h * 0.7f); lineTo(cx + w * 0.06f, h * 0.78f); close()
    }, s.dark)
    drawOval(s.light, Offset(cx - w * 0.2f, h * 0.5f), Size(w * 0.12f, h * 0.09f))
}

private fun DrawScope.eyes(s: Skin, brow: Color, browThick: Float = 0.025f, narrow: Boolean = false, pupil: Color = Color(0xFF3A2A1E)) {
    val w = size.width; val h = size.height; val cx = w / 2f
    val eh = if (narrow) 0.035f else 0.05f
    drawLine(brow, Offset(cx - w * 0.2f, h * 0.41f), Offset(cx - w * 0.06f, h * 0.4f), strokeWidth = h * browThick, cap = StrokeCap.Round)
    drawLine(brow, Offset(cx + w * 0.06f, h * 0.4f), Offset(cx + w * 0.2f, h * 0.41f), strokeWidth = h * browThick, cap = StrokeCap.Round)
    drawOval(OffWhite, Offset(cx - w * 0.17f, h * 0.45f), Size(w * 0.1f, h * eh))
    drawOval(OffWhite, Offset(cx + w * 0.07f, h * 0.45f), Size(w * 0.1f, h * eh))
    drawCircle(pupil, w * 0.02f, Offset(cx - w * 0.12f, h * (0.45f + eh / 2f)))
    drawCircle(pupil, w * 0.02f, Offset(cx + w * 0.12f, h * (0.45f + eh / 2f)))
    if (narrow) {
        drawRect(s.base, Offset(cx - w * 0.17f, h * 0.44f), Size(w * 0.1f, h * 0.015f))
        drawRect(s.base, Offset(cx + w * 0.07f, h * 0.44f), Size(w * 0.1f, h * 0.015f))
    }
}

private fun DrawScope.nose(s: Skin, big: Float = 1f) {
    val w = size.width; val h = size.height; val cx = w / 2f
    drawOval(s.dark, Offset(cx - w * 0.03f * big, h * 0.5f), Size(w * 0.09f * big, h * 0.1f * big))
    drawOval(s.base, Offset(cx - w * 0.04f * big, h * 0.5f), Size(w * 0.075f * big, h * 0.08f * big))
    drawOval(s.light, Offset(cx - w * 0.035f * big, h * 0.51f), Size(w * 0.03f, h * 0.025f))
}

/** Wide toothy grin. */
private fun DrawScope.grin() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val mouth = Path().apply {
        moveTo(cx - w * 0.15f, h * 0.65f); quadraticBezierTo(cx, h * 0.78f, cx + w * 0.15f, h * 0.65f)
        quadraticBezierTo(cx, h * 0.69f, cx - w * 0.15f, h * 0.65f); close()
    }
    drawPath(mouth, Color(0xFF6E2A20))
    drawPath(Path().apply {
        moveTo(cx - w * 0.13f, h * 0.655f); quadraticBezierTo(cx, h * 0.72f, cx + w * 0.13f, h * 0.655f)
        quadraticBezierTo(cx, h * 0.675f, cx - w * 0.13f, h * 0.655f); close()
    }, OffWhite)
    drawLine(Color(0xFF6E2A20), Offset(cx - w * 0.15f, h * 0.65f), Offset(cx + w * 0.15f, h * 0.65f), strokeWidth = h * 0.01f)
}

private fun DrawScope.flatMouth(color: Color = Color(0xFF7A3E2A), y: Float = 0.7f, width: Float = 0.08f) {
    val w = size.width; val h = size.height; val cx = w / 2f
    drawLine(color, Offset(cx - w * width, h * y), Offset(cx + w * width, h * y), strokeWidth = h * 0.014f, cap = StrokeCap.Round)
}

// ---- the six ------------------------------------------------------------------------------------

/** Long shaggy dark hair, big grey-streaked beard, black shirt, intense stare. */
private fun DrawScope.drawBeard() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val s = RUDDY
    val hair = Color(0xFF3B2A22); val hairLight = Color(0xFF5A4336); val grey = Color(0xFF8E8A86)
    shirt(Color(0xFF151515), Color(0xFF262626))
    // Hair mass behind the head: long, down to the shoulders
    drawPath(Path().apply {
        moveTo(cx - w * 0.34f, h * 0.3f); lineTo(cx - w * 0.4f, h * 0.84f); lineTo(cx - w * 0.3f, h * 0.8f); lineTo(cx - w * 0.26f, h * 0.5f)
        lineTo(cx + w * 0.26f, h * 0.5f); lineTo(cx + w * 0.3f, h * 0.8f); lineTo(cx + w * 0.4f, h * 0.84f); lineTo(cx + w * 0.34f, h * 0.3f); close()
    }, hair)
    face(s)
    // Beard: wide, from the ears down past the chin
    val beard = Path().apply {
        moveTo(cx - w * 0.27f, h * 0.52f); lineTo(cx - w * 0.3f, h * 0.72f); lineTo(cx - w * 0.18f, h * 0.9f); lineTo(cx, h * 0.95f)
        lineTo(cx + w * 0.18f, h * 0.9f); lineTo(cx + w * 0.3f, h * 0.72f); lineTo(cx + w * 0.27f, h * 0.52f)
        lineTo(cx + w * 0.14f, h * 0.62f); lineTo(cx, h * 0.6f); lineTo(cx - w * 0.14f, h * 0.62f); close()
    }
    drawPath(beard, hair)
    // Grey streaks in the beard
    drawLine(grey, Offset(cx - w * 0.12f, h * 0.72f), Offset(cx - w * 0.06f, h * 0.9f), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
    drawLine(grey, Offset(cx + w * 0.1f, h * 0.7f), Offset(cx + w * 0.14f, h * 0.86f), strokeWidth = h * 0.016f, cap = StrokeCap.Round)
    drawLine(hairLight, Offset(cx, h * 0.68f), Offset(cx + w * 0.02f, h * 0.9f), strokeWidth = h * 0.018f, cap = StrokeCap.Round)
    // Mouth open mid-shout
    drawOval(Color(0xFF4A1A14), Offset(cx - w * 0.07f, h * 0.635f), Size(w * 0.14f, h * 0.06f))
    drawRect(OffWhite, Offset(cx - w * 0.05f, h * 0.64f), Size(w * 0.1f, h * 0.018f))
    nose(s, 1.15f)
    eyes(s, hair, browThick = 0.03f, narrow = true)
    // Fringe: heavy, parted, falling over the brow
    drawPath(Path().apply {
        moveTo(cx - w * 0.34f, h * 0.3f); lineTo(cx - w * 0.3f, h * 0.14f); lineTo(cx, h * 0.1f); lineTo(cx + w * 0.3f, h * 0.14f); lineTo(cx + w * 0.34f, h * 0.3f)
        lineTo(cx + w * 0.26f, h * 0.44f); lineTo(cx + w * 0.16f, h * 0.3f); lineTo(cx + w * 0.04f, h * 0.38f); lineTo(cx - w * 0.08f, h * 0.3f)
        lineTo(cx - w * 0.18f, h * 0.4f); lineTo(cx - w * 0.26f, h * 0.46f); close()
    }, hair)
    drawLine(hairLight, Offset(cx - w * 0.2f, h * 0.16f), Offset(cx - w * 0.1f, h * 0.3f), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
    drawLine(grey, Offset(cx + w * 0.1f, h * 0.14f), Offset(cx + w * 0.2f, h * 0.26f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
}

/** Dark red mop top, open grin, red shirt — the young one. */
private fun DrawScope.drawGrin() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val s = FAIR
    val hair = Color(0xFF7A2E1E); val hairLight = Color(0xFFA0452C)
    shirt(Color(0xFFB8141E), Color(0xFF8E0E24))
    face(s, round = 0.02f)
    grin()
    nose(s, 0.95f)
    eyes(s, hair, browThick = 0.02f)
    // Cheek dimples
    drawLine(s.dark, Offset(cx - w * 0.18f, h * 0.63f), Offset(cx - w * 0.17f, h * 0.7f), strokeWidth = h * 0.012f)
    drawLine(s.dark, Offset(cx + w * 0.18f, h * 0.63f), Offset(cx + w * 0.17f, h * 0.7f), strokeWidth = h * 0.012f)
    // Bowl-cut mop: round cap of hair sitting low on the brow, covering the ears' tops
    drawPath(Path().apply {
        moveTo(cx - w * 0.34f, h * 0.48f); lineTo(cx - w * 0.33f, h * 0.22f); quadraticBezierTo(cx, h * 0.02f, cx + w * 0.33f, h * 0.22f)
        lineTo(cx + w * 0.34f, h * 0.48f); lineTo(cx + w * 0.29f, h * 0.46f); lineTo(cx + w * 0.26f, h * 0.34f)
        lineTo(cx + w * 0.1f, h * 0.36f); lineTo(cx - w * 0.06f, h * 0.33f); lineTo(cx - w * 0.2f, h * 0.37f); lineTo(cx - w * 0.26f, h * 0.34f)
        lineTo(cx - w * 0.29f, h * 0.46f); close()
    }, hair)
    drawLine(hairLight, Offset(cx - w * 0.2f, h * 0.14f), Offset(cx + w * 0.05f, h * 0.1f), strokeWidth = h * 0.03f, cap = StrokeCap.Round)
}

/** Auburn swept hair, big grin, white shirt, gold chain and a fist full of rings. */
private fun DrawScope.drawBling() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val s = RUDDY
    val hair = Color(0xFF9A4A26); val hairLight = Color(0xFFC46A3A)
    shirt(Color(0xFFF2F2F2), Color(0xFFD8D8D8))
    // Chunky chain over the open collar
    drawPath(Path().apply { moveTo(cx - w * 0.14f, h * 0.8f); quadraticBezierTo(cx, h * 1.02f, cx + w * 0.14f, h * 0.8f) }, Gold, style = Stroke(width = h * 0.028f))
    drawPath(Path().apply { moveTo(cx - w * 0.14f, h * 0.8f); quadraticBezierTo(cx, h * 1.02f, cx + w * 0.14f, h * 0.8f) }, Color(0xFF8A6A14), style = Stroke(width = h * 0.008f))
    face(s)
    grin()
    // Laugh lines
    drawLine(s.dark, Offset(cx - w * 0.2f, h * 0.6f), Offset(cx - w * 0.17f, h * 0.72f), strokeWidth = h * 0.012f)
    drawLine(s.dark, Offset(cx + w * 0.2f, h * 0.6f), Offset(cx + w * 0.17f, h * 0.72f), strokeWidth = h * 0.012f)
    nose(s, 1.1f)
    eyes(s, hair, browThick = 0.022f, narrow = true, pupil = Color(0xFF3A6A9A))
    // Swept-back hair with a high forehead
    drawPath(Path().apply {
        moveTo(cx - w * 0.3f, h * 0.36f); lineTo(cx - w * 0.3f, h * 0.2f); quadraticBezierTo(cx - w * 0.1f, h * 0.06f, cx + w * 0.2f, h * 0.1f)
        lineTo(cx + w * 0.32f, h * 0.22f); lineTo(cx + w * 0.3f, h * 0.4f); lineTo(cx + w * 0.26f, h * 0.3f); lineTo(cx + w * 0.1f, h * 0.26f)
        lineTo(cx - w * 0.14f, h * 0.3f); lineTo(cx - w * 0.26f, h * 0.3f); close()
    }, hair)
    drawLine(hairLight, Offset(cx - w * 0.14f, h * 0.16f), Offset(cx + w * 0.14f, h * 0.12f), strokeWidth = h * 0.03f, cap = StrokeCap.Round)
    // Fist with gold rings propped at the bottom-right, like resting the chin on a hand
    drawOval(s.base, Offset(cx + w * 0.1f, h * 0.76f), Size(w * 0.3f, h * 0.2f))
    drawOval(s.dark, Offset(cx + w * 0.26f, h * 0.8f), Size(w * 0.14f, h * 0.16f))
    for (i in 0 until 3) {
        val x = cx + w * (0.15f + i * 0.08f)
        drawRect(Gold, Offset(x, h * 0.8f), Size(w * 0.05f, h * 0.035f))
        drawRect(BrightGold, Offset(x + w * 0.01f, h * 0.805f), Size(w * 0.02f, h * 0.012f))
    }
    drawCircle(Gold, w * 0.03f, Offset(cx + w * 0.21f, h * 0.775f))     // the big sovereign ring
}

/** Dark feathered mullet, round face, leather jacket over a yellow shirt. */
private fun DrawScope.drawMullet() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val s = FAIR
    val hair = Color(0xFF2E2420); val hairLight = Color(0xFF4A3A34)
    // Leather jacket: black with a wide collar, yellow shirt beneath
    drawPath(Path().apply {
        moveTo(w * 0.02f, h); lineTo(w * 0.14f, h * 0.82f); lineTo(cx - w * 0.18f, h * 0.78f); lineTo(cx, h * 0.98f)
        lineTo(cx + w * 0.18f, h * 0.78f); lineTo(w * 0.86f, h * 0.82f); lineTo(w * 0.98f, h); close()
    }, Color(0xFF111111))
    drawPath(Path().apply { moveTo(cx - w * 0.16f, h * 0.79f); lineTo(cx + w * 0.16f, h * 0.79f); lineTo(cx, h * 0.96f); close() }, Color(0xFFE8C23A))
    drawPath(Path().apply { moveTo(cx - w * 0.16f, h * 0.79f); lineTo(cx, h * 0.96f); lineTo(cx - w * 0.3f, h * 0.98f); close() }, Color(0xFF2A2A2A))
    drawPath(Path().apply { moveTo(cx + w * 0.16f, h * 0.79f); lineTo(cx, h * 0.96f); lineTo(cx + w * 0.3f, h * 0.98f); close() }, Color(0xFF2A2A2A))
    // Hair at the back of the neck (the mullet part)
    drawPath(Path().apply {
        moveTo(cx - w * 0.3f, h * 0.4f); lineTo(cx - w * 0.34f, h * 0.8f); lineTo(cx - w * 0.2f, h * 0.76f); lineTo(cx + w * 0.2f, h * 0.76f)
        lineTo(cx + w * 0.34f, h * 0.8f); lineTo(cx + w * 0.3f, h * 0.4f); close()
    }, hair)
    face(s, jaw = 0.5f, round = 0.03f)
    // Double chin
    drawOval(s.dark.copy(alpha = 0.7f), Offset(cx - w * 0.14f, h * 0.73f), Size(w * 0.28f, h * 0.05f))
    flatMouth(y = 0.68f, width = 0.07f)
    nose(s, 1.1f)
    eyes(s, hair, browThick = 0.025f, narrow = true)
    // Feathered top: side-parted, winged out over the ears
    drawPath(Path().apply {
        moveTo(cx - w * 0.36f, h * 0.5f); lineTo(cx - w * 0.34f, h * 0.2f); quadraticBezierTo(cx - w * 0.1f, h * 0.04f, cx + w * 0.22f, h * 0.1f)
        lineTo(cx + w * 0.36f, h * 0.24f); lineTo(cx + w * 0.38f, h * 0.5f); lineTo(cx + w * 0.3f, h * 0.44f); lineTo(cx + w * 0.26f, h * 0.3f)
        lineTo(cx + w * 0.06f, h * 0.34f); lineTo(cx - w * 0.1f, h * 0.28f); lineTo(cx - w * 0.26f, h * 0.34f); lineTo(cx - w * 0.3f, h * 0.46f); close()
    }, hair)
    drawLine(hairLight, Offset(cx - w * 0.22f, h * 0.18f), Offset(cx - w * 0.02f, h * 0.12f), strokeWidth = h * 0.028f, cap = StrokeCap.Round)
    drawLine(hairLight, Offset(cx + w * 0.3f, h * 0.3f), Offset(cx + w * 0.34f, h * 0.44f), strokeWidth = h * 0.016f, cap = StrokeCap.Round)
}

/** Spiky bleached hair, long pointed goatee, black shirt with a yellow-green flash. */
private fun DrawScope.drawGoatee() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val s = FAIR
    val blond = Color(0xFFE8D27A); val blondDark = Color(0xFFB89A3A); val goat = Color(0xFFC9A64A)
    shirt(Color(0xFF101010), Color(0xFF1E1E1E))
    // Sponsor flash on the shirt
    drawRect(Color(0xFFBFD63A), Offset(cx + w * 0.16f, h * 0.9f), Size(w * 0.2f, h * 0.04f))
    drawRect(Color(0xFFE8E23A), Offset(cx - w * 0.36f, h * 0.9f), Size(w * 0.16f, h * 0.04f))
    face(s, jaw = 0.4f)
    nose(s, 0.95f)
    flatMouth(y = 0.655f, width = 0.06f)
    eyes(s, blondDark, browThick = 0.02f, narrow = true, pupil = Color(0xFF4A7AA8))
    // Goatee: long tapered point well below the chin, with a moustache
    drawPath(Path().apply {
        moveTo(cx - w * 0.1f, h * 0.68f); lineTo(cx + w * 0.1f, h * 0.68f); lineTo(cx + w * 0.07f, h * 0.84f); lineTo(cx, h * 1.0f); lineTo(cx - w * 0.07f, h * 0.84f); close()
    }, goat)
    drawLine(blond, Offset(cx - w * 0.02f, h * 0.72f), Offset(cx, h * 0.94f), strokeWidth = h * 0.016f, cap = StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(cx - w * 0.12f, h * 0.63f); lineTo(cx, h * 0.6f); lineTo(cx + w * 0.12f, h * 0.63f); lineTo(cx + w * 0.08f, h * 0.655f); lineTo(cx, h * 0.635f); lineTo(cx - w * 0.08f, h * 0.655f); close()
    }, goat)
    // Spikes: a row of upward points, darker roots
    drawRect(blondDark, Offset(cx - w * 0.3f, h * 0.22f), Size(w * 0.6f, h * 0.14f))
    val spikes = Path().apply {
        moveTo(cx - w * 0.32f, h * 0.36f)
        val n = 8
        val step = w * 0.64f / n
        for (i in 0 until n) {
            val x0 = cx - w * 0.32f + i * step
            val tip = if (i % 2 == 0) 0.07f else 0.12f
            lineTo(x0, h * 0.24f); lineTo(x0 + step / 2f, h * tip); lineTo(x0 + step, h * 0.24f)
        }
        lineTo(cx + w * 0.32f, h * 0.36f); close()
    }
    drawPath(spikes, blond)
    drawRect(blondDark, Offset(cx - w * 0.3f, h * 0.3f), Size(w * 0.6f, h * 0.06f))
    drawRect(s.base, Offset(cx - w * 0.24f, h * 0.33f), Size(w * 0.48f, h * 0.05f))
    drawRect(s.dark, Offset(cx + w * 0.06f, h * 0.33f), Size(w * 0.18f, h * 0.05f))
}

/** Dark side-parted hair, neat moustache, white collar on a blue shirt, dead-eyed stare. */
private fun DrawScope.drawTache() {
    val w = size.width; val h = size.height; val cx = w / 2f
    val s = TAN
    val hair = Color(0xFF241A16); val hairLight = Color(0xFF3E2E28)
    shirt(Color(0xFF1E3A8A), Color(0xFFF2F2F2), tee = Color(0xFFF2F2F2))
    face(s, jaw = 0.46f, round = 0.015f)
    nose(s, 1.05f)
    // Moustache: neat, full, sitting right on the lip
    drawPath(Path().apply {
        moveTo(cx - w * 0.14f, h * 0.65f); lineTo(cx - w * 0.06f, h * 0.61f); lineTo(cx, h * 0.625f); lineTo(cx + w * 0.06f, h * 0.61f); lineTo(cx + w * 0.14f, h * 0.65f)
        lineTo(cx + w * 0.1f, h * 0.675f); lineTo(cx, h * 0.655f); lineTo(cx - w * 0.1f, h * 0.675f); close()
    }, hair)
    flatMouth(y = 0.705f, width = 0.06f)
    // Heavy lids: eyes narrow, brows low and straight
    eyes(s, hair, browThick = 0.028f, narrow = true)
    drawRect(s.dark.copy(alpha = 0.5f), Offset(cx - w * 0.17f, h * 0.5f), Size(w * 0.1f, h * 0.015f))
    drawRect(s.dark.copy(alpha = 0.5f), Offset(cx + w * 0.07f, h * 0.5f), Size(w * 0.1f, h * 0.015f))
    // Hair: side parting on the left, full and slightly curly at the sides
    drawPath(Path().apply {
        moveTo(cx - w * 0.33f, h * 0.5f); lineTo(cx - w * 0.33f, h * 0.2f); quadraticBezierTo(cx - w * 0.05f, h * 0.04f, cx + w * 0.3f, h * 0.14f)
        lineTo(cx + w * 0.34f, h * 0.3f); lineTo(cx + w * 0.34f, h * 0.5f); lineTo(cx + w * 0.28f, h * 0.44f); lineTo(cx + w * 0.26f, h * 0.3f)
        lineTo(cx - w * 0.12f, h * 0.3f); lineTo(cx - w * 0.2f, h * 0.36f); lineTo(cx - w * 0.26f, h * 0.32f); lineTo(cx - w * 0.28f, h * 0.46f); close()
    }, hair)
    drawLine(hairLight, Offset(cx - w * 0.16f, h * 0.3f), Offset(cx + w * 0.18f, h * 0.12f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
    drawLine(hairLight, Offset(cx - w * 0.3f, h * 0.24f), Offset(cx - w * 0.3f, h * 0.44f), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
}
