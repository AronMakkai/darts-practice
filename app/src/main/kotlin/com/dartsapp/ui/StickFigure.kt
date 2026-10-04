package com.dartsapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
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

private val STAND = Pose(0.18f, 8f, 0f, -8f, 0f, 0.25f, 0f)
private val WALK = Pose(0.50f, 25f, 20f, 60f, 70f, 0.9f, 6f)
private val GRAB = Pose(0.50f, 55f, 85f, 70f, 65f, 0.35f, 4f)
private val AIM = Pose(0.50f, 95f, 115f, 70f, 65f, 0.35f, 6f)
private val THROW = Pose(0.50f, 105f, 5f, 70f, 65f, 0.45f, 12f)
private val REMOVE = Pose(0.76f, 125f, 0f, 20f, 0f, 0.3f, 8f)

private fun poseFor(step: Step): Pose = when (step.kind) {
    Kind.APPROACH -> WALK
    Kind.GRAB -> GRAB
    Kind.AIM -> AIM
    Kind.THROW -> THROW
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
    val target = poseFor(step)
    val spec = tween<Float>(280)
    val x by animateFloatAsState(target.x, spec, label = "x")
    val sR by animateFloatAsState(target.shoulderR, spec, label = "sR")
    val eR by animateFloatAsState(target.elbowR, spec, label = "eR")
    val sL by animateFloatAsState(target.shoulderL, spec, label = "sL")
    val eL by animateFloatAsState(target.elbowL, spec, label = "eL")
    val stride by animateFloatAsState(target.stride, spec, label = "stride")
    val lean by animateFloatAsState(target.lean, spec, label = "lean")

    // Dart flight on a throw step
    val fly = remember { Animatable(0f) }
    LaunchedEffect(step) {
        if (step.kind == Kind.THROW) {
            fly.snapTo(0f)
            fly.animateTo(1f, tween(380))
        } else {
            fly.snapTo(0f)
        }
    }

    // How many darts are in the board at this step
    val dartsInBoard = when (step.kind) {
        Kind.THROW -> step.dart          // the one flying lands at the end of the flight
        Kind.GRAB, Kind.AIM -> step.dart - 1
        Kind.REMOVE -> 3
        else -> 0
    }

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
        val landed = if (step.kind == Kind.THROW) dartsInBoard - 1 + (if (fly.value >= 1f) 1 else 0) else dartsInBoard
        for (i in 0 until landed.coerceIn(0, 3)) {
            val dy = (i - 1) * boardR * 0.3f
            drawDart(Offset(boardC.x - boardR * 0.1f, boardC.y + dy), 180f, h * 0.09f, thin)
        }

        // Opponent (only while the opponent throws)
        if (step.kind == Kind.OPPONENT) {
            drawFigure(Offset(w * 0.50f, floorY), h, THROW, Red, stroke, thin, withDart = true)
        }

        // The player
        val pose = Pose(x, sR, eR, sL, eL, stride, lean)
        val me = Offset(w * x, floorY)
        val dimmed = step.kind == Kind.OPPONENT || step.kind == Kind.NONE
        drawFigure(me, h, pose, if (dimmed) Grey else Gold, stroke, thin, withDart = step.kind == Kind.AIM || (step.kind == Kind.THROW && fly.value <= 0.02f) || step.kind == Kind.GRAB)

        // Dart in flight
        if (step.kind == Kind.THROW && fly.value > 0.02f && fly.value < 1f) {
            val hand = handPosition(me, h, pose)
            val targetPt = Offset(boardC.x - boardR * 0.1f, boardC.y + (step.dart - 2) * boardR * 0.3f)
            val t = fly.value
            val px = hand.x + (targetPt.x - hand.x) * t
            val arc = -h * 0.12f * 4f * t * (1f - t)
            val py = hand.y + (targetPt.y - hand.y) * t + arc
            drawDart(Offset(px, py), 170f + 20f * t, h * 0.09f, thin)
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
        drawDart(handR, 90f + (p.shoulderR + p.elbowR - 90f) * 0.3f, h * 0.09f, thin)
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
