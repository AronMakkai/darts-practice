package com.dartsapp.ui

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
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * An inverted metronome pendulum. It passes upright on every beat: [periodSec] is the time between
 * beats (one throw), so a full left-right-left swing takes two beats. [anchorMs] is the wall-clock
 * time of beat zero; when it is 0 the pendulum rests upright.
 */
@Composable
fun MetronomePendulum(anchorMs: Long, periodSec: Float, modifier: Modifier = Modifier) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(anchorMs) {
        if (anchorMs == 0L) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            nowMs = System.currentTimeMillis()
        }
    }

    val running = anchorMs > 0L && periodSec > 0f
    val t = if (running) (nowMs - anchorMs) / 1000f else 0f
    val maxDeg = 32f
    val angleDeg = if (running) maxDeg * sin(PI.toFloat() * t / periodSec) else 0f
    // How close we are to a beat, 1 = on the beat, 0 = halfway between beats
    val phase = if (running) ((t / periodSec) % 1f + 1f) % 1f else 0f
    val closeness = 1f - 2f * minOf(phase, 1f - phase)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pivot = Offset(w / 2f, h * 0.88f)
        val rodLen = h * 0.74f

        // Base
        val base = Path().apply {
            moveTo(w * 0.30f, h); lineTo(w * 0.70f, h); lineTo(w * 0.62f, h * 0.80f); lineTo(w * 0.38f, h * 0.80f); close()
        }
        drawPath(base, Charcoal)
        drawPath(base, Gold, style = Stroke(width = h * 0.015f))

        // Beat marker (upright) and swing limits
        val tickTop = pivot.y - rodLen - h * 0.06f
        drawLine(if (closeness > 0.85f) BrightGold else DarkRed, Offset(pivot.x, tickTop), Offset(pivot.x, tickTop + h * 0.08f), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
        for (sign in listOf(-1f, 1f)) {
            val r = Math.toRadians((maxDeg * sign).toDouble())
            val tip = Offset(pivot.x + (rodLen + h * 0.03f) * sin(r).toFloat(), pivot.y - (rodLen + h * 0.03f) * cos(r).toFloat())
            drawCircle(Grey, h * 0.012f, tip)
        }

        // Rod and weight
        val r = Math.toRadians(angleDeg.toDouble())
        val tip = Offset(pivot.x + rodLen * sin(r).toFloat(), pivot.y - rodLen * cos(r).toFloat())
        val weight = Offset(pivot.x + rodLen * 0.72f * sin(r).toFloat(), pivot.y - rodLen * 0.72f * cos(r).toFloat())
        drawLine(OffWhite, pivot, tip, strokeWidth = h * 0.022f, cap = StrokeCap.Round)
        drawCircle(if (closeness > 0.85f) BrightGold else Gold, h * 0.07f, weight)
        drawCircle(Black, h * 0.03f, weight)
        drawCircle(Red, h * 0.035f, pivot)
    }
}

private class Star(val x: Float, val speed: Float, val size: Float, val spin: Float, val delay: Float, val color: Color)

/**
 * Gold stars raining down the screen for a few seconds. Shown when a checkout is hit without
 * missing a single beat. Re-triggers whenever [trigger] changes to a new non-zero value.
 */
@Composable
fun StarRain(trigger: Int, modifier: Modifier = Modifier) {
    var progress by remember { mutableStateOf(-1f) }   // seconds since start, -1 = idle
    val stars = remember(trigger) {
        val rnd = Random(trigger)
        List(60) {
            Star(
                x = rnd.nextFloat(),
                speed = 0.35f + rnd.nextFloat() * 0.5f,
                size = 0.5f + rnd.nextFloat(),
                spin = (rnd.nextFloat() - 0.5f) * 6f,
                delay = rnd.nextFloat() * 1.2f,
                color = listOf(Gold, BrightGold, PaleGold, OffWhite)[rnd.nextInt(4)]
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger == 0) { progress = -1f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - start) / 1_000_000_000f
            progress = t
            if (t > 4.5f) { progress = -1f; break }
        }
    }
    if (progress < 0f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (s in stars) {
            val t = progress - s.delay
            if (t < 0f) continue
            val y = t * s.speed * h
            if (y > h + 40f) continue
            val x = s.x * w + sin(t * 2f + s.x * 10f) * w * 0.02f
            val fade = (1f - (y / h)).coerceIn(0f, 1f)
            drawStar(Offset(x, y), h * 0.018f * s.size, t * s.spin, s.color.copy(alpha = fade))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(c: Offset, r: Float, rotRad: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = rotRad + i * PI.toFloat() / 5f - PI.toFloat() / 2f
        val p = Offset(c.x + rad * cos(a), c.y + rad * sin(a))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}
