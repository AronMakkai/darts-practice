package com.dartsapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * A pose for the player. Arm angles are degrees from "hanging straight down", rotating towards the
 * board (which is to the right). Elbow angles bend the forearm further the same way.
 */
private data class Pose(
    val x: Float,          // figure position, fraction of width
    val shoulderR: Float,  // throwing arm
    val elbowR: Float,
    val shoulderL: Float,  // dart-holding arm
    val elbowL: Float,
    val stride: Float,     // leg spread 0..1
    val lean: Float        // body lean towards board, degrees
)

// Darts form, side-on to the board. Upper arm points at the board, forearm vertical with the
// dart at eye level; the release extends the forearm towards the board with a relaxed follow-through.
// A real darts stance: side-on, front foot planted at the oche, back leg stretched out behind,
// weight forward with the upper body leaning over the front foot; the off arm hangs relaxed.
private val STAND = Pose(0.18f, 14f, 40f, -8f, 6f, 0.45f, 6f)          // ready: side-on, dart held low
private val WALK = Pose(0.50f, 20f, 15f, -10f, 10f, 0.9f, 4f)
private val AIM = Pose(0.50f, 65f, 115f, -14f, 8f, 1.0f, 20f)          // elbow dropped below the shoulder, forearm upright, dart level with the middle of the head
private val FOLLOW = Pose(0.50f, 98f, 4f, -10f, 8f, 1.0f, 26f)         // arm extended at the board, leaning into it
private val REMOVE = Pose(0.76f, 120f, 0f, 15f, 0f, 0.3f, 8f)

private fun poseFor(step: Step): Pose = when (step.kind) {
    Kind.APPROACH -> WALK
    Kind.DART -> AIM
    Kind.REMOVE -> REMOVE
    else -> STAND
}

// Palette: flat, no gradients — Another World style
private val Skin = Color(0xFFE2A878)
private val SkinShade = Color(0xFFBF8858)
private val Shirt = Color(0xFFB0122C)
private val ShirtShade = Color(0xFF7A0A1C)
private val Trousers = Color(0xFF2E2E34)
private val TrousersShade = Color(0xFF1C1C20)
private val Shoe = Color(0xFF111111)
private val Hair = Color(0xFF3A2A1E)

/**
 * The darts player: a flat-shaded polygon figure in the spirit of late-80s cinematic platformers.
 * Chunky tapered limbs, two-tone shading, smooth spring-eased motion between poses. Walks to the
 * oche, aims (with a settling sway), releases (the dart flies to the board), clears the board, and
 * stands back while the opponent throws.
 */
@Composable
fun StickFigure(step: Step, modifier: Modifier = Modifier) {
    // The dart leaves the hand at the moment a Dart step is COMPLETED, i.e. when the step moves on.
    var prevStep by remember { mutableStateOf(step) }
    val fly = remember { Animatable(0f) }
    var releasing by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        val leavingThrow = prevStep.kind == Kind.DART && step != prevStep
        prevStep = step
        if (leavingThrow) {
            releasing = true
            fly.snapTo(0f)
            fly.animateTo(1f, tween(320))
            releasing = false
        } else if (step.kind == Kind.NONE || step.kind == Kind.OPPONENT) {
            fly.snapTo(0f)
        }
    }
    val flying = fly.value > 0f && fly.value < 1f

    // Aiming: the elbow sways a little as the arm comes up, then settles within about half a second.
    var sway by remember { mutableStateOf(0f) }
    LaunchedEffect(step) {
        sway = 0f
        if (step.kind != Kind.DART) return@LaunchedEffect
        val startNs = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - startNs) / 1_000_000_000f
            if (t > 0.9f) { sway = 0f; break }
            sway = (9f * exp(-t / 0.18f) * sin(2.0 * Math.PI * 4.0 * t)).toFloat()
        }
    }

    // Walking: leg swing and body bob while approaching
    var walk by remember { mutableStateOf(0f) }
    LaunchedEffect(step) {
        walk = 0f
        if (step.kind != Kind.APPROACH) return@LaunchedEffect
        val startNs = withFrameNanos { it }
        while (true) {
            walk = (withFrameNanos { it } - startNs) / 1_000_000_000f
        }
    }

    val target = if (releasing) FOLLOW.copy(x = poseFor(step).x) else poseFor(step)
    val soft = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    val quick = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    val x by animateFloatAsState(target.x, spring(dampingRatio = 1f, stiffness = 60f), label = "x")
    val sR by animateFloatAsState(target.shoulderR, if (releasing) quick else soft, label = "sR")
    val eR by animateFloatAsState(target.elbowR, if (releasing) quick else soft, label = "eR")
    val sL by animateFloatAsState(target.shoulderL, soft, label = "sL")
    val eL by animateFloatAsState(target.elbowL, soft, label = "eL")
    val stride by animateFloatAsState(target.stride, soft, label = "stride")
    val lean by animateFloatAsState(target.lean, soft, label = "lean")

    // Darts in the board: every dart whose throw step is already complete.
    val dartsInBoard = when (step.kind) {
        Kind.DART -> step.dart - 1
        Kind.REMOVE -> 3
        else -> 0
    }
    val landed = if (flying) dartsInBoard - 1 else dartsInBoard

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val thin = h * 0.03f

        // Floor and oche line
        val floorY = h * 0.92f
        drawLine(Charcoal, Offset(0f, floorY), Offset(w, floorY), strokeWidth = thin)
        drawLine(Grey, Offset(w * 0.56f, floorY - h * 0.06f), Offset(w * 0.56f, floorY), strokeWidth = thin)

        // Board on the right
        val boardC = Offset(w * 0.90f, h * 0.36f)
        val boardR = h * 0.17f
        drawCircle(Charcoal, boardR, boardC)
        drawCircle(Red, boardR * 0.55f, boardC)
        drawCircle(Black, boardR * 0.25f, boardC)
        drawCircle(Gold, boardR, boardC, style = Stroke(width = thin))
        for (i in 0 until landed.coerceIn(0, 3)) {
            val dy = (i - 1) * boardR * 0.3f
            drawDart(Offset(boardC.x - boardR * 0.1f, boardC.y + dy), 92f, h * 0.075f, thin)
        }

        // Opponent (only while the opponent throws)
        if (step.kind == Kind.OPPONENT) {
            drawFigure(Offset(w * 0.50f, floorY), h, AIM, dimmed = false, walkT = 0f, withDart = true, rival = true)
        }

        // The player
        val walking = step.kind == Kind.APPROACH
        val pose = Pose(x, sR, eR + sway, sL, eL, stride, lean)
        val bob = if (walking) kotlin.math.abs(sin(walk * 7f)) * h * 0.02f else 0f
        val me = Offset(w * x, floorY - bob)
        val dimmed = step.kind == Kind.OPPONENT || step.kind == Kind.NONE
        val holdingDart = !flying && step.kind == Kind.DART
        drawFigure(me, h, pose, dimmed, if (walking) walk else 0f, holdingDart, rival = false)

        // Dart in flight: a shallow arc from the release point to the board
        if (flying) {
            val hand = handPosition(me, h, FOLLOW.copy(x = pose.x))
            val slot = (dartsInBoard - 1).coerceIn(0, 2)
            val targetPt = Offset(boardC.x - boardR * 0.1f, boardC.y + (slot - 1) * boardR * 0.3f)
            val t = fly.value
            val px = hand.x + (targetPt.x - hand.x) * t
            val arc = -h * 0.06f * 4f * t * (1f - t)
            val py = hand.y + (targetPt.y - hand.y) * t + arc
            drawDart(Offset(px, py), 82f + 14f * t, h * 0.075f, thin)
        }
    }
}

private fun armEnd(from: Offset, len: Float, deg: Float): Offset {
    val r = Math.toRadians(deg.toDouble())
    return Offset(from.x + len * sin(r).toFloat(), from.y + len * cos(r).toFloat())
}

/** Hip position for a pose: the front foot is planted at [feet]; the hips sit back as the stride opens. */
private fun hipFor(feet: Offset, h: Float, p: Pose): Offset =
    Offset(feet.x - h * 0.05f * p.stride, feet.y - h * 0.26f * (1f - 0.04f * p.stride))

/** Position of the throwing hand for the given pose, used as the dart's launch point. */
private fun handPosition(feet: Offset, h: Float, p: Pose): Offset {
    val hip = hipFor(feet, h, p)
    val shoulder = armEnd(hip, -h * 0.30f, -p.lean)
    val elbow = armEnd(shoulder, h * 0.14f, p.shoulderR)
    return armEnd(elbow, h * 0.13f, p.shoulderR + p.elbowR)
}

/** A closed smooth shape through the midpoints of [pts] (quadratic B-spline), filled with [c]. */
private fun DrawScope.blob(c: Color, pts: List<Offset>) {
    val n = pts.size
    val path = Path().apply {
        moveTo((pts[0].x + pts[1].x) / 2f, (pts[0].y + pts[1].y) / 2f)
        for (i in 1..n) {
            val b = pts[i % n]; val nx = pts[(i + 1) % n]
            quadraticBezierTo(b.x, b.y, (b.x + nx.x) / 2f, (b.y + nx.y) / 2f)
        }
        close()
    }
    drawPath(path, c)
}

/** A tapered, rounded limb from [a] (width [wa]) to [b] (width [wb]); [rim] adds a thin sunset rim light on the leading edge. */
private fun DrawScope.limb(a: Offset, b: Offset, wa: Float, wb: Float, color: Color, shade: Color? = null, rim: Color? = null) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val nx = -dy / len
    val ny = dx / len
    val path = Path().apply {
        moveTo(a.x + nx * wa / 2f, a.y + ny * wa / 2f)
        quadraticBezierTo((a.x + b.x) / 2f + nx * (wa + wb) * 0.3f, (a.y + b.y) / 2f + ny * (wa + wb) * 0.3f, b.x + nx * wb / 2f, b.y + ny * wb / 2f)
        lineTo(b.x - nx * wb / 2f, b.y - ny * wb / 2f)
        quadraticBezierTo((a.x + b.x) / 2f - nx * (wa + wb) * 0.3f, (a.y + b.y) / 2f - ny * (wa + wb) * 0.3f, a.x - nx * wa / 2f, a.y - ny * wa / 2f)
        close()
    }
    drawPath(path, color)
    if (shade != null) {
        val sp = Path().apply {
            moveTo(a.x - nx * wa / 2f, a.y - ny * wa / 2f)
            quadraticBezierTo((a.x + b.x) / 2f - nx * (wa + wb) * 0.3f, (a.y + b.y) / 2f - ny * (wa + wb) * 0.3f, b.x - nx * wb / 2f, b.y - ny * wb / 2f)
            lineTo(b.x - nx * wb * 0.1f, b.y - ny * wb * 0.1f)
            lineTo(a.x - nx * wa * 0.1f, a.y - ny * wa * 0.1f)
            close()
        }
        drawPath(sp, shade)
    }
    drawCircle(color, wa / 2f, a)
    drawCircle(color, wb / 2f, b)
    if (rim != null) drawLine(rim, Offset(a.x + nx * wa * 0.42f, a.y + ny * wa * 0.42f), Offset(b.x + nx * wb * 0.42f, b.y + ny * wb * 0.42f), strokeWidth = wb * 0.16f, cap = StrokeCap.Round)
}

private fun DrawScope.drawFigure(feet: Offset, h: Float, p: Pose, dimmed: Boolean, walkT: Float, withDart: Boolean, rival: Boolean) {
    val bodyLen = h * 0.30f
    val upper = h * 0.14f
    val fore = h * 0.13f
    val headR = h * 0.072f
    // Standing back (not throwing): solid but darker, faded towards the night purple — never see-through
    fun c(x: Color): Color = if (!dimmed) x else Color(
        x.red * 0.5f + 0.10f * 0.5f, x.green * 0.5f + 0.04f * 0.5f, x.blue * 0.5f + 0.16f * 0.5f, x.alpha
    )
    val skin = c(Skin); val skinShade = c(SkinShade)
    val shirt = c(if (rival) Color(0xFF1E5A9E) else Shirt)
    val shirtShade = c(if (rival) Color(0xFF123A6A) else ShirtShade)
    val trousers = c(Trousers); val trousersShade = c(TrousersShade)
    val rim = c(Color(0xFFFF6FA8).copy(alpha = 0.7f))       // sunset rim light from the board side

    val hip = hipFor(feet, h, p)
    val shoulder = armEnd(hip, -bodyLen, -p.lean)
    val leanR = Math.toRadians(p.lean.toDouble())
    // the head tips forward with the lean, looking down the dart
    val head = Offset(shoulder.x + headR * (0.35f + 1.1f * sin(leanR).toFloat()), shoulder.y - headR * 1.15f)

    // Ground shadow
    drawOval(c(Color.Black.copy(alpha = 0.35f)), Offset(feet.x - h * 0.24f * (0.5f + p.stride * 0.5f), feet.y - h * 0.012f),
        androidx.compose.ui.geometry.Size(h * 0.34f * (0.5f + p.stride * 0.5f), h * 0.03f))

    // Legs: the front foot is planted at the oche; the back leg stretches out behind, heel lifted
    val swing = if (walkT > 0f) sin(walkT * 7f) * h * 0.06f else 0f
    val frontFoot = Offset(feet.x - swing, feet.y)
    val backFoot = Offset(feet.x - h * (0.07f + 0.13f * p.stride) + swing, feet.y)
    val frontKnee = Offset((hip.x + frontFoot.x) / 2f + h * 0.02f + (if (walkT > 0f) kotlin.math.abs(cos(walkT * 7f)) * h * 0.04f else 0f), (hip.y + frontFoot.y) / 2f)
    val backKnee = Offset((hip.x + backFoot.x) / 2f - h * 0.004f + (if (walkT > 0f) kotlin.math.abs(sin(walkT * 7f)) * h * 0.04f else 0f), (hip.y + backFoot.y) / 2f)
    val legW = h * 0.078f
    limb(hip, backKnee, legW, legW * 0.82f, trousersShade)
    limb(backKnee, Offset(backFoot.x, backFoot.y - h * 0.02f), legW * 0.82f, legW * 0.62f, trousersShade)
    // back shoe: up on the toes
    blob(c(Shoe), listOf(Offset(backFoot.x - h * 0.03f, backFoot.y - h * 0.045f), Offset(backFoot.x + h * 0.02f, backFoot.y - h * 0.03f),
        Offset(backFoot.x + h * 0.06f, backFoot.y - h * 0.004f), Offset(backFoot.x + h * 0.01f, backFoot.y + h * 0.004f), Offset(backFoot.x - h * 0.04f, backFoot.y - h * 0.025f)))
    limb(hip, frontKnee, legW * 1.05f, legW * 0.86f, trousers, trousersShade, rim)
    limb(frontKnee, Offset(frontFoot.x, frontFoot.y - h * 0.025f), legW * 0.86f, legW * 0.66f, trousers, trousersShade, rim)
    // front shoe: flat, toe pointing at the board, with a sole
    blob(c(Shoe), listOf(Offset(frontFoot.x - h * 0.035f, frontFoot.y - h * 0.035f), Offset(frontFoot.x + h * 0.04f, frontFoot.y - h * 0.03f),
        Offset(frontFoot.x + h * 0.085f, frontFoot.y - h * 0.008f), Offset(frontFoot.x + h * 0.06f, frontFoot.y + h * 0.004f), Offset(frontFoot.x - h * 0.04f, frontFoot.y + h * 0.004f)))
    drawLine(c(Color(0xFF3A3A44)), Offset(frontFoot.x - h * 0.04f, frontFoot.y + h * 0.002f), Offset(frontFoot.x + h * 0.07f, frontFoot.y + h * 0.002f), strokeWidth = h * 0.008f)

    // Off arm, behind the body: hanging relaxed
    val elbowL = armEnd(shoulder, upper, p.shoulderL)
    val handL = armEnd(elbowL, fore, p.shoulderL + p.elbowL)
    limb(shoulder, elbowL, h * 0.06f, h * 0.05f, shirtShade)
    limb(elbowL, handL, h * 0.048f, h * 0.038f, skinShade)
    drawCircle(skinShade, h * 0.027f, handL)

    // Torso: shirt with a bit of a belly, shaded down the back, rim lit down the front
    val ax = shoulder.x - hip.x; val ay = shoulder.y - hip.y
    val al = kotlin.math.sqrt(ax * ax + ay * ay)
    val ux = ax / al; val uy = ay / al          // up the spine
    val fx = -uy; val fy = ux                    // towards the board
    fun at(along: Float, out: Float) = Offset(hip.x + ux * bodyLen * along + fx * h * out, hip.y + uy * bodyLen * along + fy * h * out)
    val torso = listOf(at(-0.02f, -0.045f), at(0.0f, 0.05f), at(0.35f, 0.075f), at(0.7f, 0.065f), at(1.02f, 0.05f), at(1.04f, -0.04f), at(0.6f, -0.06f), at(0.25f, -0.055f))
    blob(shirt, torso)
    blob(shirtShade, listOf(at(1.0f, -0.04f), at(0.6f, -0.06f), at(0.25f, -0.055f), at(-0.02f, -0.045f), at(0.1f, -0.02f), at(0.6f, -0.03f)))
    drawLine(rim, at(0.08f, 0.055f), at(0.7f, 0.066f), strokeWidth = h * 0.008f, cap = StrokeCap.Round)
    // belt and a couple of shirt buttons down the front
    drawLine(c(Color(0xFF111114)), at(0.0f, -0.045f), at(0.0f, 0.05f), strokeWidth = h * 0.016f)
    drawCircle(c(Color(0xFFD4AF37)), h * 0.009f, at(0.0f, 0.03f))
    for (k in 0 until 3) drawCircle(shirtShade, h * 0.006f, at(0.45f + k * 0.17f, 0.045f))
    // collar
    blob(shirtShade, listOf(at(0.98f, 0.03f), at(1.08f, 0.045f), at(1.08f, -0.03f), at(0.98f, -0.02f)))

    // Neck and head: ear, nose, brow, short hair; looking along the dart at the board
    limb(at(1.02f, 0.0f), Offset(head.x - headR * 0.1f, head.y + headR * 0.6f), h * 0.045f, h * 0.04f, skinShade)
    blob(skin, listOf(Offset(head.x - headR * 0.85f, head.y - headR * 0.45f), Offset(head.x + headR * 0.25f, head.y - headR * 1.0f),
        Offset(head.x + headR * 0.95f, head.y - headR * 0.25f), Offset(head.x + headR * 1.05f, head.y + headR * 0.35f),
        Offset(head.x + headR * 0.55f, head.y + headR * 1.0f), Offset(head.x - headR * 0.4f, head.y + headR * 0.9f), Offset(head.x - headR * 0.95f, head.y + headR * 0.2f)))
    blob(skinShade, listOf(Offset(head.x - headR * 0.85f, head.y - headR * 0.45f), Offset(head.x - headR * 0.2f, head.y - headR * 0.7f),
        Offset(head.x - headR * 0.3f, head.y + headR * 0.9f), Offset(head.x - headR * 0.95f, head.y + headR * 0.2f)))
    drawOval(skinShade, Offset(head.x - headR * 0.25f, head.y - headR * 0.15f), androidx.compose.ui.geometry.Size(headR * 0.32f, headR * 0.5f))   // ear
    blob(skin, listOf(Offset(head.x + headR * 0.95f, head.y - headR * 0.1f), Offset(head.x + headR * 1.25f, head.y + headR * 0.22f), Offset(head.x + headR * 0.95f, head.y + headR * 0.35f)))  // nose
    blob(c(Hair), listOf(Offset(head.x - headR * 0.95f, head.y - headR * 0.25f), Offset(head.x - headR * 0.7f, head.y - headR * 0.95f),
        Offset(head.x + headR * 0.35f, head.y - headR * 1.15f), Offset(head.x + headR * 0.95f, head.y - headR * 0.55f),
        Offset(head.x + headR * 0.45f, head.y - headR * 0.6f), Offset(head.x - headR * 0.25f, head.y - headR * 0.55f), Offset(head.x - headR * 0.6f, head.y + headR * 0.05f)))
    drawLine(c(Hair), Offset(head.x + headR * 0.45f, head.y - headR * 0.32f), Offset(head.x + headR * 0.85f, head.y - headR * 0.28f), strokeWidth = headR * 0.12f, cap = StrokeCap.Round)  // brow
    drawCircle(c(Black), headR * 0.09f, Offset(head.x + headR * 0.66f, head.y - headR * 0.08f))                                              // eye
    drawLine(c(SkinShade), Offset(head.x + headR * 0.6f, head.y + headR * 0.6f), Offset(head.x + headR * 0.85f, head.y + headR * 0.55f), strokeWidth = headR * 0.08f, cap = StrokeCap.Round) // mouth
    drawLine(rim, Offset(head.x + headR * 0.6f, head.y - headR * 0.85f), Offset(head.x + headR * 1.02f, head.y + headR * 0.1f), strokeWidth = h * 0.006f, cap = StrokeCap.Round)

    // Throwing arm, in front: short sleeve, forearm, a pinch grip on the dart
    val elbowR = armEnd(shoulder, upper, p.shoulderR)
    val handR = armEnd(elbowR, fore, p.shoulderR + p.elbowR)
    limb(shoulder, Offset((shoulder.x + elbowR.x) / 2f, (shoulder.y + elbowR.y) / 2f), h * 0.072f, h * 0.064f, shirt, shirtShade, rim)
    limb(Offset((shoulder.x + elbowR.x) / 2f, (shoulder.y + elbowR.y) / 2f), elbowR, h * 0.056f, h * 0.05f, skin, skinShade)
    limb(elbowR, handR, h * 0.052f, h * 0.038f, skin, skinShade, rim)
    drawCircle(skin, h * 0.03f, handR)
    drawCircle(skinShade, h * 0.012f, Offset(handR.x + h * 0.018f, handR.y - h * 0.012f))      // thumb tip

    if (withDart) {
        val nose = Offset(handR.x + h * 0.05f, handR.y - h * 0.01f)
        drawDart(nose, 82f, h * 0.085f, h * 0.03f)
    }
}

/** A small dart: steel point, gripped barrel, shaft and red flights, pointing along [deg] (90 = towards the board). */
private fun DrawScope.drawDart(tip: Offset, deg: Float, len: Float, thin: Float) {
    val r = Math.toRadians(deg.toDouble())
    val dir = Offset(sin(r).toFloat(), cos(r).toFloat())
    fun along(f: Float) = Offset(tip.x - dir.x * len * f, tip.y - dir.y * len * f)
    drawLine(Color(0xFFD8D8E0), along(0f), along(0.2f), strokeWidth = thin * 0.35f, cap = StrokeCap.Round)          // point
    drawLine(Color(0xFF9A9AA8), along(0.2f), along(0.55f), strokeWidth = thin * 0.9f, cap = StrokeCap.Round)        // barrel
    for (k in 0 until 4) {
        val g = along(0.26f + k * 0.07f); val perp = Offset(-dir.y, dir.x)
        drawLine(Color(0xFF55555F), Offset(g.x + perp.x * thin * 0.45f, g.y + perp.y * thin * 0.45f), Offset(g.x - perp.x * thin * 0.45f, g.y - perp.y * thin * 0.45f), strokeWidth = thin * 0.18f)
    }
    drawLine(Color(0xFF1A1A22), along(0.55f), along(0.82f), strokeWidth = thin * 0.45f)                             // shaft
    val tail = along(1f)
    val perp = Offset(-dir.y, dir.x)
    val flightLen = len * 0.32f
    drawLine(Red, tail, Offset(tail.x + perp.x * flightLen + dir.x * flightLen, tail.y + perp.y * flightLen + dir.y * flightLen), strokeWidth = thin, cap = StrokeCap.Round)
    drawLine(Red, tail, Offset(tail.x - perp.x * flightLen + dir.x * flightLen, tail.y - perp.y * flightLen + dir.y * flightLen), strokeWidth = thin, cap = StrokeCap.Round)
}
