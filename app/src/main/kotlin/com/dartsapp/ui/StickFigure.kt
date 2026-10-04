package com.dartsapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * A pose for the stick figure. Arm angles are degrees from "hanging straight down",
 * rotating towards the board (which is to the right). Elbow angles bend the forearm further.
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
private val GRAB = Pose(0.50f, 45f, 85f, 60f, 60f, 0.4f, 6f)          // hands meet at chest height
private val AIM = Pose(0.50f, 90f, 92f, 60f, 60f, 0.4f, 9f)           // forearm vertical, dart at the eye
private val DRAW = Pose(0.50f, 88f, 104f, 60f, 60f, 0.4f, 9f)         // slight draw-back before release
private val FOLLOW = Pose(0.50f, 96f, 4f, 60f, 60f, 0.45f, 12f)       // arm extended at the board
private val REMOVE = Pose(0.76f, 120f, 0f, 15f, 0f, 0.3f, 8f)

private fun poseFor(step: Step): Pose = when (step.kind) {
    Kind.APPROACH -> WALK
    Kind.GRAB -> GRAB
    Kind.AIM -> AIM
    Kind.THROW -> DRAW
    Kind.REMOVE -> REMOVE
    else -> STAND
}

/**
 * Animated stick figure illustrating the current metronome step: walking to the oche, taking a
 * dart from the other hand, aiming, throwing (with the dart flying to the board), clearing the
 * board, and standing back while the opponent throws.
 */
@Composable
fun StickFigure(step: Step, modifier: Modifier = Modifier) {
    // The dart leaves the hand at the moment a Throw step is COMPLETED, i.e. when the step moves on.
    var prevStep by remember { mutableStateOf(step) }
    val fly = remember { Animatable(0f) }
    var releasing by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        val leavingThrow = prevStep.kind == Kind.THROW && step != prevStep
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
        if (step.kind != Kind.AIM) return@LaunchedEffect
        val startNs = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - startNs) / 1_000_000_000f
            if (t > 0.9f) { sway = 0f; break }
            sway = (9f * exp(-t / 0.18f) * sin(2.0 * Math.PI * 4.0 * t)).toFloat()
        }
    }

    val target = if (releasing) FOLLOW.copy(x = poseFor(step).x) else poseFor(step)
    val spec = tween<Float>(if (releasing) 110 else 300)
    val x by animateFloatAsState(target.x, spec, label = "x")
    val sR by animateFloatAsState(target.shoulderR, spec, label = "sR")
    val eR by animateFloatAsState(target.elbowR, spec, label = "eR")
    val sL by animateFloatAsState(target.shoulderL, spec, label = "sL")
    val eL by animateFloatAsState(target.elbowL, spec, label = "eL")
    val stride by animateFloatAsState(target.stride, spec, label = "stride")
    val lean by animateFloatAsState(target.lean, spec, label = "lean")

    // Darts in the board: every dart whose throw step is already complete.
    val dartsInBoard = when (step.kind) {
        Kind.GRAB, Kind.AIM, Kind.THROW -> step.dart - 1
        Kind.REMOVE -> 3
        else -> 0
    }
    // The one in flight counts in dartsInBoard already (its throw step is over) — hide it until it lands.
    val landed = if (flying) dartsInBoard - 1 else dartsInBoard

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = h * 0.045f, cap = StrokeCap.Round)
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
            drawFigure(Offset(w * 0.50f, floorY), h, AIM, Red, stroke, thin, withDart = true)
        }

        // The player
        val pose = Pose(x, sR, eR + sway, sL, eL, stride, lean)
        val me = Offset(w * x, floorY)
        val dimmed = step.kind == Kind.OPPONENT || step.kind == Kind.NONE
        val holdingDart = !flying && (step.kind == Kind.AIM || step.kind == Kind.THROW || step.kind == Kind.GRAB)
        drawFigure(me, h, pose, if (dimmed) Grey else Gold, stroke, thin, withDart = holdingDart)

        // Dart in flight: a shallow arc from the release point to the board, nose slightly up then down
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
    val shoulder = armEnd(hip, -bodyLen, -p.lean) // negative length = upwards
    val upper = h * 0.14f
    val fore = h * 0.13f
    val elbow = armEnd(shoulder, upper, p.shoulderR)
    return armEnd(elbow, fore, p.shoulderR + p.elbowR)
}

private fun DrawScope.drawFigure(feet: Offset, h: Float, p: Pose, color: Color, stroke: Stroke, thin: Float, withDart: Boolean) {
    val bodyLen = h * 0.30f
    val legLen = h * 0.26f
    val upper = h * 0.14f
    val fore = h * 0.13f
    val headR = h * 0.075f

    val hip = Offset(feet.x, feet.y - legLen)
    val shoulder = armEnd(hip, -bodyLen, -p.lean)
    val head = Offset(shoulder.x + headR * 0.3f * sin(Math.toRadians(p.lean.toDouble())).toFloat(), shoulder.y - headR * 1.25f)

    // Legs
    val spread = h * 0.12f * p.stride
    drawLine(color, hip, Offset(feet.x - spread, feet.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, hip, Offset(feet.x + spread, feet.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Body
    drawLine(color, hip, shoulder, strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Head
    drawCircle(color, headR, head, style = Stroke(width = stroke.width))

    // Dart-holding arm (drawn first, behind)
    val elbowL = armEnd(shoulder, upper, p.shoulderL)
    val handL = armEnd(elbowL, fore, p.shoulderL + p.elbowL)
    drawLine(color.copy(alpha = 0.6f), shoulder, elbowL, strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = 0.6f), elbowL, handL, strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)

    // Throwing arm
    val elbowR = armEnd(shoulder, upper, p.shoulderR)
    val handR = armEnd(elbowR, fore, p.shoulderR + p.elbowR)
    drawLine(color, shoulder, elbowR, strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, elbowR, handR, strokeWidth = stroke.width, cap = StrokeCap.Round)

    if (withDart) {
        // Dart held between thumb and fingers, level with a slightly raised nose, pointing at the board.
        val nose = Offset(handR.x + h * 0.045f, handR.y - h * 0.008f)
        drawDart(nose, 80f, h * 0.075f, thin)
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
