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
private val STAND = Pose(0.18f, 6f, 0f, -6f, 0f, 0.25f, 0f)
private val WALK = Pose(0.50f, 20f, 15f, 55f, 70f, 0.9f, 4f)
private val AIM = Pose(0.50f, 90f, 92f, 60f, 60f, 0.4f, 9f)           // forearm vertical, dart at the eye
private val FOLLOW = Pose(0.50f, 96f, 4f, 60f, 60f, 0.45f, 12f)       // arm extended at the board
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

/** Position of the throwing hand for the given pose, used as the dart's launch point. */
private fun handPosition(feet: Offset, h: Float, p: Pose): Offset {
    val bodyLen = h * 0.30f
    val legLen = h * 0.26f
    val hip = Offset(feet.x, feet.y - legLen)
    val shoulder = armEnd(hip, -bodyLen, -p.lean)
    val upper = h * 0.14f
    val fore = h * 0.13f
    val elbow = armEnd(shoulder, upper, p.shoulderR)
    return armEnd(elbow, fore, p.shoulderR + p.elbowR)
}

/** A tapered limb segment: a quad from [a] (width [wa]) to [b] (width [wb]) with round joints. */
private fun DrawScope.limb(a: Offset, b: Offset, wa: Float, wb: Float, color: Color, shade: Color? = null) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val nx = -dy / len
    val ny = dx / len
    val path = Path().apply {
        moveTo(a.x + nx * wa / 2f, a.y + ny * wa / 2f)
        lineTo(b.x + nx * wb / 2f, b.y + ny * wb / 2f)
        lineTo(b.x - nx * wb / 2f, b.y - ny * wb / 2f)
        lineTo(a.x - nx * wa / 2f, a.y - ny * wa / 2f)
        close()
    }
    drawPath(path, color)
    if (shade != null) {
        // darker strip along the far edge for the two-tone look
        val sp = Path().apply {
            moveTo(a.x + nx * wa / 2f, a.y + ny * wa / 2f)
            lineTo(b.x + nx * wb / 2f, b.y + ny * wb / 2f)
            lineTo(b.x + nx * wb * 0.15f, b.y + ny * wb * 0.15f)
            lineTo(a.x + nx * wa * 0.15f, a.y + ny * wa * 0.15f)
            close()
        }
        drawPath(sp, shade)
    }
    drawCircle(color, wa / 2f, a)
    drawCircle(color, wb / 2f, b)
}

private fun DrawScope.drawFigure(feet: Offset, h: Float, p: Pose, dimmed: Boolean, walkT: Float, withDart: Boolean, rival: Boolean) {
    val bodyLen = h * 0.30f
    val legLen = h * 0.26f
    val upper = h * 0.14f
    val fore = h * 0.13f
    val headR = h * 0.075f
    val alpha = if (dimmed) 0.45f else 1f
    val skin = Skin.copy(alpha = alpha)
    val skinShade = SkinShade.copy(alpha = alpha)
    val shirt = (if (rival) Color(0xFF1E5A9E) else Shirt).copy(alpha = alpha)
    val shirtShade = (if (rival) Color(0xFF123A6A) else ShirtShade).copy(alpha = alpha)
    val trousers = Trousers.copy(alpha = alpha)
    val trousersShade = TrousersShade.copy(alpha = alpha)

    val hip = Offset(feet.x, feet.y - legLen)
    val shoulder = armEnd(hip, -bodyLen, -p.lean)
    val head = Offset(shoulder.x + headR * 0.4f * sin(Math.toRadians(p.lean.toDouble())).toFloat(), shoulder.y - headR * 1.2f)

    // Legs: back leg then front leg, each with a knee. Walking swings them.
    val spread = h * 0.12f * p.stride
    val swing = if (walkT > 0f) sin(walkT * 7f) * h * 0.06f else 0f
    val backFoot = Offset(feet.x - spread + swing, feet.y)
    val frontFoot = Offset(feet.x + spread - swing, feet.y)
    fun knee(foot: Offset, bend: Float): Offset = Offset((hip.x + foot.x) / 2f + bend, (hip.y + foot.y) / 2f + h * 0.01f)
    val backKnee = knee(backFoot, -h * 0.02f + (if (walkT > 0f) kotlin.math.abs(sin(walkT * 7f)) * h * 0.04f else 0f))
    val frontKnee = knee(frontFoot, h * 0.02f + (if (walkT > 0f) kotlin.math.abs(cos(walkT * 7f)) * h * 0.04f else 0f))
    val legW = h * 0.075f
    limb(hip, backKnee, legW, legW * 0.85f, trousersShade)
    limb(backKnee, backFoot, legW * 0.85f, legW * 0.7f, trousersShade)
    drawOval(Shoe.copy(alpha = alpha), Offset(backFoot.x - h * 0.05f, backFoot.y - h * 0.02f), androidx.compose.ui.geometry.Size(h * 0.1f, h * 0.035f))
    limb(hip, frontKnee, legW, legW * 0.85f, trousers, trousersShade)
    limb(frontKnee, frontFoot, legW * 0.85f, legW * 0.7f, trousers, trousersShade)
    drawOval(Shoe.copy(alpha = alpha), Offset(frontFoot.x - h * 0.04f, frontFoot.y - h * 0.02f), androidx.compose.ui.geometry.Size(h * 0.11f, h * 0.035f))

    // Dart-holding arm (behind the body)
    val elbowL = armEnd(shoulder, upper, p.shoulderL)
    val handL = armEnd(elbowL, fore, p.shoulderL + p.elbowL)
    limb(shoulder, elbowL, h * 0.06f, h * 0.05f, shirtShade)
    limb(elbowL, handL, h * 0.05f, h * 0.04f, skinShade)
    drawCircle(skinShade, h * 0.028f, handL)

    // Torso: a tapered quad with shaded back
    val torsoW = h * 0.11f
    limb(hip, shoulder, torsoW * 0.8f, torsoW, shirt, shirtShade)
    // Collar
    drawCircle(shirtShade, torsoW * 0.35f, Offset(shoulder.x, shoulder.y + h * 0.015f))

    // Neck + head (polygonal)
    limb(Offset(shoulder.x, shoulder.y), Offset(head.x, head.y + headR * 0.6f), h * 0.04f, h * 0.04f, skinShade)
    val hp = Path().apply {
        moveTo(head.x - headR * 0.8f, head.y - headR * 0.5f)
        lineTo(head.x + headR * 0.3f, head.y - headR * 0.95f)
        lineTo(head.x + headR * 0.95f, head.y - headR * 0.2f)
        lineTo(head.x + headR * 0.85f, head.y + headR * 0.6f)
        lineTo(head.x + headR * 0.2f, head.y + headR)
        lineTo(head.x - headR * 0.5f, head.y + headR * 0.85f)
        lineTo(head.x - headR * 0.9f, head.y + headR * 0.2f)
        close()
    }
    drawPath(hp, skin)
    // Face shade (back of the head) and hair
    val hs = Path().apply {
        moveTo(head.x - headR * 0.8f, head.y - headR * 0.5f)
        lineTo(head.x - headR * 0.2f, head.y - headR * 0.75f)
        lineTo(head.x - headR * 0.3f, head.y + headR * 0.9f)
        lineTo(head.x - headR * 0.5f, head.y + headR * 0.85f)
        lineTo(head.x - headR * 0.9f, head.y + headR * 0.2f)
        close()
    }
    drawPath(hs, skinShade)
    val hair = Path().apply {
        moveTo(head.x - headR * 0.9f, head.y - headR * 0.3f)
        lineTo(head.x - headR * 0.6f, head.y - headR * 0.9f)
        lineTo(head.x + headR * 0.4f, head.y - headR * 1.05f)
        lineTo(head.x + headR * 0.95f, head.y - headR * 0.45f)
        lineTo(head.x + headR * 0.6f, head.y - headR * 0.55f)
        lineTo(head.x - headR * 0.2f, head.y - headR * 0.6f)
        lineTo(head.x - headR * 0.8f, head.y - headR * 0.1f)
        close()
    }
    drawPath(hair, Hair.copy(alpha = alpha))
    // Eye (facing the board)
    drawCircle(Black.copy(alpha = alpha), headR * 0.09f, Offset(head.x + headR * 0.55f, head.y - headR * 0.1f))

    // Throwing arm (in front)
    val elbowR = armEnd(shoulder, upper, p.shoulderR)
    val handR = armEnd(elbowR, fore, p.shoulderR + p.elbowR)
    limb(shoulder, elbowR, h * 0.065f, h * 0.055f, shirt, shirtShade)
    limb(elbowR, handR, h * 0.055f, h * 0.04f, skin, skinShade)
    drawCircle(skin, h * 0.03f, handR)

    if (withDart) {
        val nose = Offset(handR.x + h * 0.045f, handR.y - h * 0.008f)
        drawDart(nose, 80f, h * 0.075f, h * 0.03f)
    }
}

/** A small dart: shaft with a flight, pointing along [deg] (90 = towards the board). */
private fun DrawScope.drawDart(tip: Offset, deg: Float, len: Float, thin: Float) {
    val r = Math.toRadians(deg.toDouble())
    val dir = Offset(sin(r).toFloat(), cos(r).toFloat())
    val tail = Offset(tip.x - dir.x * len, tip.y - dir.y * len)
    drawLine(OffWhite, tail, tip, strokeWidth = thin * 0.8f, cap = StrokeCap.Round)
    val perp = Offset(-dir.y, dir.x)
    val flightLen = len * 0.3f
    drawLine(Red, tail, Offset(tail.x + perp.x * flightLen + dir.x * flightLen, tail.y + perp.y * flightLen + dir.y * flightLen), strokeWidth = thin, cap = StrokeCap.Round)
    drawLine(Red, tail, Offset(tail.x - perp.x * flightLen + dir.x * flightLen, tail.y - perp.y * flightLen + dir.y * flightLen), strokeWidth = thin, cap = StrokeCap.Round)
}
