package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Throw-pace ring drawn over the board. Started by a swipe, it grows from the centre to the edge of
 * the board and shrinks back to the centre over [periodSec] (the preset's dart time). The ideal
 * moment to tap the target is when it is smallest again. After that it pulses small at the centre
 * until the throw is made. [startMs] = 0 means no throw is armed and nothing is drawn.
 */
@Composable
fun ThrowRing(startMs: Long, periodSec: Float, modifier: Modifier = Modifier) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startMs) {
        if (startMs == 0L) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            nowMs = System.currentTimeMillis()
        }
    }
    if (startMs == 0L || periodSec <= 0f) return

    val t = (nowMs - startMs) / 1000f
    val inCycle = t <= periodSec
    // 0 -> 1 -> 0 over one period, then a gentle pulse around the centre
    val scale = if (inCycle) sin(PI.toFloat() * t / periodSec) else 0.06f + 0.03f * sin((t - periodSec) * 8f)
    val nearCentre = !inCycle || (t > periodSec * 0.85f)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val boardR = minOf(size.width, size.height) / 2f / RIM_SCALE
        val r = boardR * scale.coerceIn(0.02f, 1f)
        val color = if (nearCentre) BrightGold else Gold
        drawCircle(color.copy(alpha = 0.18f), r, Offset(cx, cy))
        drawCircle(color.copy(alpha = 0.75f), r, Offset(cx, cy), style = Stroke(width = boardR * 0.05f))
        // Centre dot marks the ideal moment
        drawCircle(color.copy(alpha = if (nearCentre) 0.9f else 0.35f), boardR * 0.025f, Offset(cx, cy))
    }
}

/**
 * The swipe zone in the bottom-left corner. A diagonal swipe up-and-right arms a throw.
 * Draws a translucent arrow; brighter while a throw is armed.
 */
@Composable
fun SwipeToThrowZone(armed: Boolean, enabled: Boolean, onSwipe: () -> Unit, modifier: Modifier = Modifier) {
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures(
                onDragStart = { dragX = 0f; dragY = 0f },
                onDrag = { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y },
                onDragEnd = { if (dragX > 60f && dragY < -60f) onSwipe() },
                onDragCancel = { }
            )
        }
    ) {
        val w = size.width
        val h = size.height
        val color = (if (armed) BrightGold else Gold).copy(alpha = if (enabled) (if (armed) 0.9f else 0.45f) else 0.15f)
        val stroke = h * 0.06f
        val from = Offset(w * 0.2f, h * 0.8f)
        val to = Offset(w * 0.8f, h * 0.2f)
        drawLine(color, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, to, Offset(to.x - w * 0.3f, to.y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, to, Offset(to.x, to.y + h * 0.3f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private class Spark(val angle: Float, val speed: Float, val size: Float, val spin: Float, val color: Color, val life: Float)

/**
 * A burst of gold stars exploding out of [origin] (screen pixels) — shown when a checkout is hit
 * in perfect rhythm. Re-triggers whenever [trigger] changes to a new non-zero value.
 */
@Composable
fun StarBurst(trigger: Int, origin: Offset, modifier: Modifier = Modifier) {
    var progress by remember { mutableStateOf(-1f) }   // seconds since start, -1 = idle
    val sparks = remember(trigger) {
        val rnd = Random(trigger * 7919)
        List(70) {
            Spark(
                angle = rnd.nextFloat() * 2f * PI.toFloat(),
                speed = 0.25f + rnd.nextFloat() * 0.9f,
                size = 0.5f + rnd.nextFloat() * 1.1f,
                spin = (rnd.nextFloat() - 0.5f) * 10f,
                color = listOf(Gold, BrightGold, PaleGold, OffWhite, Red)[rnd.nextInt(5)],
                life = 1.2f + rnd.nextFloat() * 1.0f
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger == 0) { progress = -1f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - start) / 1_000_000_000f
            progress = t
            if (t > 2.4f) { progress = -1f; break }
        }
    }
    if (progress < 0f) return

    Canvas(modifier = modifier) {
        val h = size.height
        val t = progress
        // Flash ring at the impact point
        if (t < 0.25f) {
            val k = t / 0.25f
            drawCircle(BrightGold.copy(alpha = 1f - k), h * 0.02f + h * 0.12f * k, origin, style = Stroke(width = h * 0.01f * (1f - k)))
        }
        for (sp in sparks) {
            if (t > sp.life) continue
            val dist = sp.speed * h * 0.55f * (1f - exp(-2.2f * t)) / 2.2f * 2.2f  // ease-out burst
            val x = origin.x + cos(sp.angle) * dist
            val y = origin.y + sin(sp.angle) * dist + 0.35f * h * t * t          // gravity
            val fade = (1f - t / sp.life).coerceIn(0f, 1f)
            drawStar(Offset(x, y), h * 0.016f * sp.size, t * sp.spin, sp.color.copy(alpha = fade))
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

/**
 * Gold bars stacked at the side of the screen, one per perfect checkout in a row. Ten small bars
 * compact into one medium bar, ten medium into one large (100), ten large into one huge (1000).
 */
@Composable
fun GoldBarStack(count: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val ones = count % 10
        val tens = (count / 10) % 10
        val hundreds = (count / 100) % 10
        val thousands = count / 1000

        var y = h - 4f
        fun bars(n: Int, barH: Float, widthFrac: Float, face: Color, top: Color, side: Color) {
            val bw = w * widthFrac
            val x0 = (w - bw) / 2f
            val skew = barH * 0.45f
            for (i in 0 until n) {
                val yTop = y - barH
                if (yTop < 0f) return
                // front face
                drawRect(face, Offset(x0, yTop + skew), Size(bw - skew, barH - skew))
                // top face (parallelogram)
                val topPath = Path().apply {
                    moveTo(x0, yTop + skew); lineTo(x0 + skew, yTop); lineTo(x0 + bw, yTop); lineTo(x0 + bw - skew, yTop + skew); close()
                }
                drawPath(topPath, top)
                // right side face
                val sidePath = Path().apply {
                    moveTo(x0 + bw - skew, yTop + skew); lineTo(x0 + bw, yTop); lineTo(x0 + bw, y - skew); lineTo(x0 + bw - skew, y); close()
                }
                drawPath(sidePath, side)
                drawRect(Black.copy(alpha = 0.6f), Offset(x0, yTop + skew), Size(bw - skew, barH - skew), style = Stroke(width = 1.2f))
                y -= barH + 2f
            }
        }
        bars(thousands, h * 0.075f, 1.0f, Color(0xFFE8C14A), Color(0xFFFFF0A0), Color(0xFF9A7A1E))
        bars(hundreds, h * 0.055f, 0.9f, Color(0xFFDDB53E), Color(0xFFFFE98A), Color(0xFF8E6F18))
        bars(tens, h * 0.04f, 0.78f, Gold, PaleGold, Color(0xFF8A6A14))
        bars(ones, h * 0.026f, 0.62f, Color(0xFFC9A431), Color(0xFFEFD77E), Color(0xFF7A5E10))
    }
}
