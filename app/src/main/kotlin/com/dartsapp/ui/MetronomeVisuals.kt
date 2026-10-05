package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
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
fun ThrowRing(
    startMs: Long,
    periodSec: Float,
    modifier: Modifier = Modifier,
    accuracy: Float = 1f,     // current accuracy: 1 = razor sharp ring, low = blurred ring
    opacity: Float = 1f       // from the Aim transparency setting
) {
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
    // Blur: a poor accuracy smears the ring into several soft, offset copies; a perfect one is a single crisp line.
    val blur = (1f - accuracy.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val layers = 1 + (blur * 7f).toInt()
    val op = opacity.coerceIn(0.1f, 1f)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val boardR = minOf(size.width, size.height) / 2f / RIM_SCALE
        val r = boardR * scale.coerceIn(0.02f, 1f)
        val color = if (nearCentre) BrightGold else Gold
        val stroke = boardR * 0.05f
        val spread = boardR * 0.14f * blur
        drawCircle(color.copy(alpha = 0.18f * op), r, Offset(cx, cy))
        if (layers == 1) {
            drawCircle(color.copy(alpha = 0.75f * op), r, Offset(cx, cy), style = Stroke(width = stroke))
        } else {
            // Spread the stroke over the blur band, fading towards the edges of the band
            for (i in 0 until layers) {
                val k = if (layers == 1) 0f else i / (layers - 1f) * 2f - 1f     // -1 .. 1
                val rr = (r + k * spread).coerceAtLeast(1f)
                val a = (0.75f / layers) * (1.6f - 0.6f * kotlin.math.abs(k)) * op
                drawCircle(color.copy(alpha = a.coerceIn(0f, 1f)), rr, Offset(cx, cy), style = Stroke(width = stroke * (1f + blur)))
            }
        }
        // Centre dot marks the ideal moment
        drawCircle(color.copy(alpha = (if (nearCentre) 0.9f else 0.35f) * op), boardR * 0.025f, Offset(cx, cy))
    }
}

/**
 * A small star pop from the centre of the board, shown when a throw lands dead on the beat.
 * Re-triggers whenever [trigger] changes to a new non-zero value. Draw it over the board box.
 */
@Composable
fun PerfectPop(trigger: Int, modifier: Modifier = Modifier) {
    var progress by remember { mutableStateOf(-1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) { progress = -1f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - start) / 1_000_000_000f
            progress = t
            if (t > 0.7f) { progress = -1f; break }
        }
    }
    if (progress < 0f) return
    val t = progress / 0.7f                       // 0..1
    val ease = 1f - (1f - t) * (1f - t)           // ease-out
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val boardR = minOf(size.width, size.height) / 2f / RIM_SCALE
        val fade = (1f - t).coerceIn(0f, 1f)
        // Flash ring
        drawCircle(OffWhite.copy(alpha = 0.8f * (1f - ease)), boardR * 0.04f + boardR * 0.22f * ease, c, style = Stroke(width = boardR * 0.015f))
        // Eight little stars flying outwards, spinning
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f + 0.3f
            val d = boardR * 0.3f * ease
            val p = Offset(c.x + cos(a) * d, c.y + sin(a) * d)
            val col = if (i % 2 == 0) BrightGold else OffWhite
            drawStar(p, boardR * (0.035f + 0.015f * (1f - ease)), t * 4f + i, col.copy(alpha = fade))
        }
        // Centre star, biggest, shrinking away
        drawStar(c, boardR * 0.08f * (1f - ease * 0.8f), t * 3f, BrightGold.copy(alpha = fade))
    }
}

/**
 * The swipe zone in the bottom-left corner. A diagonal swipe up-and-right arms a throw.
 * A chunky chevron arrow with a gold-to-red gradient, a dark outline, a pulsing glow and a trail
 * of fading chevrons behind it that animate upward to suggest the swipe; brighter while armed.
 */
@Composable
fun SwipeToThrowZone(armed: Boolean, enabled: Boolean, onSwipe: () -> Unit, modifier: Modifier = Modifier) {
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
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
        val alpha = if (!enabled) 0.18f else if (armed) 1f else 0.85f
        val pulse = 0.5f + 0.5f * sin(t * (if (armed) 9f else 3f))

        // Rotate everything 45 degrees so "up" on the arrow points up-and-right
        rotate(degrees = 45f, pivot = Offset(w / 2f, h / 2f)) {
            val cx = w / 2f
            // Trail chevrons: three fading copies sliding upward
            for (i in 0 until 3) {
                val phase = ((t * 0.8f + i * 0.33f) % 1f)
                val y = h * (0.85f - 0.55f * phase)
                val a = (1f - phase) * 0.35f * alpha
                chevron(Offset(cx, y), w * 0.42f, h * 0.16f, Gold.copy(alpha = a), null, h * 0.03f)
            }
            // Main arrow: shaft + head
            val shaftTop = h * 0.36f
            val shaftBottom = h * 0.8f
            val shaftW = w * 0.14f
            val grad = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(BrightGold, Gold, Red, DarkRed),
                startY = h * 0.1f, endY = shaftBottom
            )
            val body = Path().apply {
                moveTo(cx, h * 0.1f)                               // tip
                lineTo(cx + w * 0.34f, h * 0.44f)                  // right wing
                lineTo(cx + w * 0.16f, h * 0.44f)
                lineTo(cx + shaftW / 2f, shaftTop + h * 0.08f)
                lineTo(cx + shaftW / 2f, shaftBottom)
                lineTo(cx - shaftW / 2f, shaftBottom)
                lineTo(cx - shaftW / 2f, shaftTop + h * 0.08f)
                lineTo(cx - w * 0.16f, h * 0.44f)
                lineTo(cx - w * 0.34f, h * 0.44f)
                close()
            }
            // Glow
            drawPath(body, (if (armed) BrightGold else Gold).copy(alpha = (0.12f + 0.18f * pulse) * alpha), style = Stroke(width = h * 0.12f))
            // Outline + gradient fill + bevel highlight
            drawPath(body, Black.copy(alpha = alpha), style = Stroke(width = h * 0.06f))
            drawPath(body, grad, alpha = alpha)
            val highlight = Path().apply {
                moveTo(cx, h * 0.13f); lineTo(cx - w * 0.3f, h * 0.43f); lineTo(cx - w * 0.17f, h * 0.43f); lineTo(cx, h * 0.26f); close()
            }
            drawPath(highlight, OffWhite.copy(alpha = 0.35f * alpha))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.chevron(c: Offset, width: Float, height: Float, color: Color, stroke: Color?, thickness: Float) {
    val p = Path().apply {
        moveTo(c.x - width / 2f, c.y + height / 2f)
        lineTo(c.x, c.y - height / 2f)
        lineTo(c.x + width / 2f, c.y + height / 2f)
    }
    drawPath(p, color, style = Stroke(width = thickness, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    if (stroke != null) drawPath(p, stroke, style = Stroke(width = thickness * 0.4f))
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

/**
 * Bust effect: the screen cracks out from the offending dart, then a chunky 80s graffiti "BUST"
 * slams in, tilted, with a thick outline and drop shadow. Re-triggers on a new non-zero [trigger].
 */
@Composable
fun BustOverlay(trigger: Int, origin: Offset, modifier: Modifier = Modifier) {
    var progress by remember { mutableStateOf(-1f) }
    val cracks = remember(trigger) {
        val rnd = Random(trigger * 104729 + 7)
        List(11) { i ->
            val baseAngle = i * (2f * PI.toFloat() / 11f) + rnd.nextFloat() * 0.4f
            // each crack is a jagged polyline: list of (angle jitter, segment length factor)
            List(7) { Pair((rnd.nextFloat() - 0.5f) * 0.9f, 0.6f + rnd.nextFloat() * 0.8f) } to baseAngle
        }
    }
    LaunchedEffect(trigger) {
        if (trigger == 0) { progress = -1f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - start) / 1_000_000_000f
            progress = t
            if (t > 1.8f) { progress = -1f; break }
        }
    }
    if (progress < 0f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val t = progress
        val fade = if (t > 1.3f) (1f - (t - 1.3f) / 0.5f).coerceIn(0f, 1f) else 1f

        // Flash
        if (t < 0.12f) drawRect(OffWhite.copy(alpha = 0.55f * (1f - t / 0.12f)))

        // Cracks grow out over the first 0.35 s
        val grow = (t / 0.35f).coerceIn(0f, 1f)
        val reach = maxOf(w, h) * 0.9f
        for ((segments, baseAngle) in cracks) {
            var pos = origin
            var angle = baseAngle
            val segLen = reach / segments.size
            val path = Path().apply { moveTo(origin.x, origin.y) }
            var drawn = 0f
            for ((jitter, lenF) in segments) {
                val len = segLen * lenF
                if (drawn + len > reach * grow) {
                    val part = (reach * grow - drawn).coerceAtLeast(0f)
                    pos = Offset(pos.x + cos(angle) * part, pos.y + sin(angle) * part)
                    path.lineTo(pos.x, pos.y)
                    break
                }
                angle += jitter
                pos = Offset(pos.x + cos(angle) * len, pos.y + sin(angle) * len)
                path.lineTo(pos.x, pos.y)
                drawn += len
            }
            drawPath(path, Black.copy(alpha = 0.9f * fade), style = Stroke(width = 7f, cap = StrokeCap.Round))
            drawPath(path, OffWhite.copy(alpha = 0.85f * fade), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }

        // Darken behind the word
        if (t > 0.15f) drawRect(Black.copy(alpha = 0.45f * fade))

        // BUST slams in: scale 1.8 -> 1 with a little overshoot, tilted
        if (t > 0.15f) {
            val k = ((t - 0.15f) / 0.25f).coerceIn(0f, 1f)
            val scale = 1.8f - 0.8f * k + (if (k >= 1f) 0f else 0.08f * sin(k * PI.toFloat()))
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
                textSize = w * 0.30f * scale
                isFakeBoldText = true
                letterSpacing = -0.04f
            }
            val cx = w / 2f
            val cy = h * 0.46f
            drawContext.canvas.nativeCanvas.apply {
                save()
                rotate(-9f, cx, cy)
                val baseline = cy + paint.textSize * 0.36f
                // drop shadow blocks (graffiti style: solid, offset)
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = android.graphics.Color.argb((255 * fade).toInt(), 0x7A, 0x0A, 0x1C)
                drawText("BUST", cx + paint.textSize * 0.09f, baseline + paint.textSize * 0.09f, paint)
                // thick black outline
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = paint.textSize * 0.11f
                paint.strokeJoin = android.graphics.Paint.Join.ROUND
                paint.color = android.graphics.Color.argb((255 * fade).toInt(), 0, 0, 0)
                drawText("BUST", cx, baseline, paint)
                // red fill
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = android.graphics.Color.argb((255 * fade).toInt(), 0xE0, 0x1A, 0x3A)
                drawText("BUST", cx, baseline, paint)
                // gold highlight stroke, thin, offset up-left
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = paint.textSize * 0.025f
                paint.color = android.graphics.Color.argb((255 * fade).toInt(), 0xFF, 0xE2, 0x7A)
                drawText("BUST", cx - paint.textSize * 0.02f, baseline - paint.textSize * 0.02f, paint)
                restore()
            }
            // Spray dots around the word
            val rnd = Random(trigger * 31)
            for (i in 0 until 26) {
                val dx = (rnd.nextFloat() - 0.5f) * w * 0.95f
                val dy = (rnd.nextFloat() - 0.5f) * h * 0.28f
                val r = 2f + rnd.nextFloat() * 6f
                drawCircle((if (i % 3 == 0) Gold else Red).copy(alpha = 0.7f * fade * k), r, Offset(cx + dx, cy + dy))
            }
        }
    }
}

/**
 * Three flat-shaded vector darts, 80s style, pointing up-right. [inHand] of them are drawn solid;
 * the rest (already thrown) are faint outlines, so you always know which dart you are on.
 */
@Composable
fun DartsInHand(inHand: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = 3
        val slot = w / n
        for (i in 0 until n) {
            val solid = i < inHand
            val cx = slot * i + slot / 2f
            drawVectorDart(Offset(cx, h * 0.5f), h * 0.9f, solid)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVectorDart(centre: Offset, length: Float, solid: Boolean) {
    // Dart axis runs from tail (bottom-left) to point (top-right) at 55 degrees
    val a = Math.toRadians(-55.0)
    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
    val perp = Offset(-dir.y, dir.x)
    fun at(d: Float, side: Float) = Offset(centre.x + dir.x * d + perp.x * side, centre.y + dir.y * d + perp.y * side)
    val alpha = if (solid) 1f else 0.28f
    val stroke = Stroke(width = length * 0.03f)

    // Point
    drawLine(OffWhite.copy(alpha = alpha), at(length * 0.2f, 0f), at(length * 0.5f, 0f), strokeWidth = length * 0.035f, cap = StrokeCap.Round)
    // Barrel: tapered polygon
    val barrel = Path().apply {
        moveTo(at(-length * 0.05f, -length * 0.05f).x, at(-length * 0.05f, -length * 0.05f).y)
        lineTo(at(length * 0.2f, -length * 0.035f).x, at(length * 0.2f, -length * 0.035f).y)
        lineTo(at(length * 0.2f, length * 0.035f).x, at(length * 0.2f, length * 0.035f).y)
        lineTo(at(-length * 0.05f, length * 0.05f).x, at(-length * 0.05f, length * 0.05f).y)
        close()
    }
    if (solid) drawPath(barrel, Gold) else drawPath(barrel, Gold.copy(alpha = alpha), style = stroke)
    // Grip rings
    for (k in 0 until 3) {
        val d = length * (0.0f + k * 0.06f)
        drawLine(Black.copy(alpha = alpha), at(d, -length * 0.045f), at(d, length * 0.045f), strokeWidth = length * 0.015f)
    }
    // Shaft
    drawLine(Red.copy(alpha = alpha), at(-length * 0.05f, 0f), at(-length * 0.28f, 0f), strokeWidth = length * 0.03f, cap = StrokeCap.Round)
    // Flights: two flat triangles
    val flight1 = Path().apply {
        moveTo(at(-length * 0.26f, 0f).x, at(-length * 0.26f, 0f).y)
        lineTo(at(-length * 0.5f, -length * 0.16f).x, at(-length * 0.5f, -length * 0.16f).y)
        lineTo(at(-length * 0.48f, 0f).x, at(-length * 0.48f, 0f).y)
        close()
    }
    val flight2 = Path().apply {
        moveTo(at(-length * 0.26f, 0f).x, at(-length * 0.26f, 0f).y)
        lineTo(at(-length * 0.5f, length * 0.16f).x, at(-length * 0.5f, length * 0.16f).y)
        lineTo(at(-length * 0.48f, 0f).x, at(-length * 0.48f, 0f).y)
        close()
    }
    if (solid) {
        drawPath(flight1, Red)
        drawPath(flight2, Color(0xFF8E0E24))
        drawPath(flight1, Black, style = Stroke(width = length * 0.012f))
        drawPath(flight2, Black, style = Stroke(width = length * 0.012f))
    } else {
        drawPath(flight1, Red.copy(alpha = alpha), style = stroke)
        drawPath(flight2, Red.copy(alpha = alpha), style = stroke)
    }
}

/**
 * Fighting-game style power bar for accuracy: chunky segmented blocks, red at the bottom end through
 * orange to gold at the top, with a bevelled frame. Draggable when [enabled]; display-only otherwise.
 */
@Composable
fun PowerBar(value: Float, enabled: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "power"
    )
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures(
                onDragStart = { p -> onChange((p.x / size.width).coerceIn(0f, 1f)) },
                onDrag = { change, _ -> change.consume(); onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            )
        }.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { p -> onChange((p.x / size.width).coerceIn(0f, 1f)) }
        }
    ) {
        val w = size.width
        val h = size.height
        // Bevelled frame: light top-left, dark bottom-right (flat shading)
        drawRect(Color(0xFF3A3A3A))
        drawRect(Color(0xFF6A6A6A), Offset(0f, 0f), Size(w, h * 0.12f))
        drawRect(Color(0xFF6A6A6A), Offset(0f, 0f), Size(w * 0.01f, h))
        drawRect(Color(0xFF151515), Offset(0f, h * 0.88f), Size(w, h * 0.12f))
        drawRect(Color(0xFF151515), Offset(w * 0.99f, 0f), Size(w * 0.01f, h))
        val inner = Offset(w * 0.02f, h * 0.18f)
        val innerW = w * 0.96f
        val innerH = h * 0.64f
        drawRect(Black, inner, Size(innerW, innerH))

        val segments = 24
        val gap = innerW * 0.006f
        val segW = (innerW - gap * (segments - 1)) / segments
        val filled = (shown * segments)
        for (i in 0 until segments) {
            val x = inner.x + i * (segW + gap)
            val frac = i.toFloat() / segments
            val base = when {
                frac < 0.33f -> Color(0xFFE0203A)
                frac < 0.66f -> Color(0xFFF08A1E)
                else -> Gold
            }
            val fill = (filled - i).coerceIn(0f, 1f)
            if (fill <= 0f) {
                drawRect(base.copy(alpha = if (enabled) 0.14f else 0.08f), Offset(x, inner.y), Size(segW, innerH))
            } else {
                drawRect(base.copy(alpha = 0.35f + 0.65f * fill), Offset(x, inner.y), Size(segW, innerH))
                // highlight strip for the pixel-bevel look
                drawRect(OffWhite.copy(alpha = 0.35f * fill), Offset(x, inner.y), Size(segW, innerH * 0.18f))
            }
        }
        // Tick marks every quarter
        for (q in 1..3) {
            val x = inner.x + innerW * q / 4f
            drawLine(Color(0xFF6A6A6A), Offset(x, h * 0.88f), Offset(x, h), strokeWidth = 2f)
        }
    }
}

/**
 * Flat-shaded vector coach — a weathered pub-league veteran: cap, glasses, big moustache,
 * jowls, a polo shirt. Three skin tones for shading, no gradients. Fits a square.
 */
@Composable
fun CoachHead(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val skin = Color(0xFFE2A878)
        val skinDark = Color(0xFFC48A5B)
        val skinLight = Color(0xFFF0C296)
        val hair = Color(0xFF6B6B6B)

        // Polo shirt + collar
        val shirt = Path().apply {
            moveTo(w * 0.08f, h); lineTo(w * 0.2f, h * 0.84f); lineTo(cx - w * 0.1f, h * 0.8f)
            lineTo(cx, h * 0.9f); lineTo(cx + w * 0.1f, h * 0.8f); lineTo(w * 0.8f, h * 0.84f); lineTo(w * 0.92f, h); close()
        }
        drawPath(shirt, DarkRed)
        drawPath(Path().apply { moveTo(cx - w * 0.1f, h * 0.8f); lineTo(cx, h * 0.9f); lineTo(cx - w * 0.16f, h * 0.9f); close() }, Red)
        drawPath(Path().apply { moveTo(cx + w * 0.1f, h * 0.8f); lineTo(cx, h * 0.9f); lineTo(cx + w * 0.16f, h * 0.9f); close() }, Red)
        // Neck
        drawRect(skinDark, Offset(cx - w * 0.1f, h * 0.7f), Size(w * 0.2f, h * 0.13f))

        // Ears
        drawOval(skinDark, Offset(cx - w * 0.31f, h * 0.44f), Size(w * 0.08f, h * 0.13f))
        drawOval(skinDark, Offset(cx + w * 0.23f, h * 0.44f), Size(w * 0.08f, h * 0.13f))

        // Head: oval with a squarer jaw
        drawOval(skin, Offset(cx - w * 0.26f, h * 0.22f), Size(w * 0.52f, h * 0.56f))
        drawRect(skin, Offset(cx - w * 0.22f, h * 0.5f), Size(w * 0.44f, h * 0.2f))
        // Right-side shade (light from the left)
        val shade = Path().apply {
            moveTo(cx + w * 0.06f, h * 0.24f); lineTo(cx + w * 0.2f, h * 0.3f); lineTo(cx + w * 0.26f, h * 0.5f)
            lineTo(cx + w * 0.2f, h * 0.7f); lineTo(cx + w * 0.06f, h * 0.78f); close()
        }
        drawPath(shade, skinDark)
        // Cheek highlight + brow highlight
        drawOval(skinLight, Offset(cx - w * 0.2f, h * 0.5f), Size(w * 0.12f, h * 0.09f))
        drawRect(skinLight, Offset(cx - w * 0.18f, h * 0.3f), Size(w * 0.2f, h * 0.04f))
        // Jowl lines
        drawLine(skinDark, Offset(cx - w * 0.14f, h * 0.6f), Offset(cx - w * 0.12f, h * 0.7f), strokeWidth = h * 0.012f)
        drawLine(skinDark, Offset(cx + w * 0.14f, h * 0.6f), Offset(cx + w * 0.12f, h * 0.7f), strokeWidth = h * 0.012f)

        // Grey hair tufts under the cap
        drawRect(hair, Offset(cx - w * 0.28f, h * 0.34f), Size(w * 0.06f, h * 0.1f))
        drawRect(hair, Offset(cx + w * 0.22f, h * 0.34f), Size(w * 0.06f, h * 0.1f))

        // Cap: crown, band and peak
        drawOval(Red, Offset(cx - w * 0.3f, h * 0.12f), Size(w * 0.6f, h * 0.3f))
        drawRect(Black, Offset(cx - w * 0.3f, h * 0.27f), Size(w * 0.6f, h * 0.1f))                   // the oval's lower half is hidden by the band
        drawRect(Color(0xFF8E0E24), Offset(cx - w * 0.3f, h * 0.27f), Size(w * 0.6f, h * 0.06f))      // band
        drawRect(skin, Offset(cx - w * 0.26f, h * 0.33f), Size(w * 0.52f, h * 0.05f))                 // forehead under the band
        drawPath(shade.let { Path().apply { moveTo(cx + w * 0.06f, h * 0.33f); lineTo(cx + w * 0.26f, h * 0.33f); lineTo(cx + w * 0.26f, h * 0.38f); lineTo(cx + w * 0.06f, h * 0.38f); close() } }, skinDark)
        val peak = Path().apply {
            moveTo(cx - w * 0.3f, h * 0.3f); lineTo(cx + w * 0.3f, h * 0.3f)
            lineTo(cx + w * 0.4f, h * 0.36f); lineTo(cx - w * 0.4f, h * 0.36f); close()
        }
        drawPath(peak, Color(0xFF6E0A1A))
        drawCircle(Gold, w * 0.035f, Offset(cx, h * 0.2f))   // badge

        // Eyebrows (bushy, grey)
        drawLine(hair, Offset(cx - w * 0.2f, h * 0.41f), Offset(cx - w * 0.06f, h * 0.4f), strokeWidth = h * 0.03f, cap = StrokeCap.Round)
        drawLine(hair, Offset(cx + w * 0.06f, h * 0.4f), Offset(cx + w * 0.2f, h * 0.41f), strokeWidth = h * 0.03f, cap = StrokeCap.Round)
        // Eyes
        drawOval(OffWhite, Offset(cx - w * 0.17f, h * 0.45f), Size(w * 0.1f, h * 0.05f))
        drawOval(OffWhite, Offset(cx + w * 0.07f, h * 0.45f), Size(w * 0.1f, h * 0.05f))
        drawCircle(Color(0xFF3A2A1E), w * 0.02f, Offset(cx - w * 0.11f, h * 0.475f))
        drawCircle(Color(0xFF3A2A1E), w * 0.02f, Offset(cx + w * 0.13f, h * 0.475f))
        // Glasses: thin black frames with a highlight
        val gs = Stroke(width = h * 0.018f)
        drawOval(Black, Offset(cx - w * 0.2f, h * 0.43f), Size(w * 0.16f, h * 0.1f), style = gs)
        drawOval(Black, Offset(cx + w * 0.04f, h * 0.43f), Size(w * 0.16f, h * 0.1f), style = gs)
        drawLine(Black, Offset(cx - w * 0.04f, h * 0.47f), Offset(cx + w * 0.04f, h * 0.47f), strokeWidth = h * 0.018f)
        drawLine(Black, Offset(cx - w * 0.2f, h * 0.47f), Offset(cx - w * 0.27f, h * 0.45f), strokeWidth = h * 0.015f)
        drawLine(Black, Offset(cx + w * 0.2f, h * 0.47f), Offset(cx + w * 0.27f, h * 0.45f), strokeWidth = h * 0.015f)
        drawLine(OffWhite.copy(alpha = 0.5f), Offset(cx - w * 0.17f, h * 0.445f), Offset(cx - w * 0.12f, h * 0.44f), strokeWidth = h * 0.012f)
        drawLine(OffWhite.copy(alpha = 0.5f), Offset(cx + w * 0.07f, h * 0.445f), Offset(cx + w * 0.12f, h * 0.44f), strokeWidth = h * 0.012f)

        // Nose: bulbous, shaded
        drawOval(skinDark, Offset(cx - w * 0.03f, h * 0.5f), Size(w * 0.1f, h * 0.1f))
        drawOval(skin, Offset(cx - w * 0.04f, h * 0.5f), Size(w * 0.08f, h * 0.08f))
        drawOval(skinLight, Offset(cx - w * 0.035f, h * 0.51f), Size(w * 0.035f, h * 0.03f))

        // Moustache: thick, drooping
        val tash = Path().apply {
            moveTo(cx - w * 0.19f, h * 0.665f); lineTo(cx - w * 0.08f, h * 0.6f); lineTo(cx, h * 0.615f)
            lineTo(cx + w * 0.08f, h * 0.6f); lineTo(cx + w * 0.19f, h * 0.665f)
            lineTo(cx + w * 0.15f, h * 0.715f); lineTo(cx, h * 0.665f); lineTo(cx - w * 0.15f, h * 0.715f); close()
        }
        drawPath(tash, Color(0xFF4A4A4A))
        drawPath(Path().apply { moveTo(cx - w * 0.12f, h * 0.62f); lineTo(cx, h * 0.63f); lineTo(cx - w * 0.04f, h * 0.65f); close() }, hair)
        // Mouth under the moustache
        drawLine(Color(0xFF7A3E2A), Offset(cx - w * 0.07f, h * 0.73f), Offset(cx + w * 0.07f, h * 0.73f), strokeWidth = h * 0.014f, cap = StrokeCap.Round)
        // Chin shadow
        drawOval(skinDark.copy(alpha = 0.6f), Offset(cx - w * 0.1f, h * 0.74f), Size(w * 0.2f, h * 0.04f))
    }
}

/**
 * Big pop-up word for a great score ("180!", "TON 40", "170 OUT!"): slams in gold with a black outline
 * and a star burst behind it, scaled by [huge].
 */
@Composable
fun BigPop(trigger: Int, text: String, huge: Boolean, origin: Offset, modifier: Modifier = Modifier) {
    var progress by remember { mutableStateOf(-1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) { progress = -1f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - start) / 1_000_000_000f
            progress = t
            if (t > (if (huge) 2.2f else 1.3f)) { progress = -1f; break }
        }
    }
    StarBurst(trigger = trigger, origin = origin, modifier = modifier)
    if (progress < 0f) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val t = progress
        val total = if (huge) 2.2f else 1.3f
        val fade = if (t > total - 0.4f) ((total - t) / 0.4f).coerceIn(0f, 1f) else 1f
        val k = (t / 0.22f).coerceIn(0f, 1f)
        val scale = (1.9f - 0.9f * k) + (if (k >= 1f) 0f else 0.1f * sin(k * PI.toFloat()))
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textSize = w * (if (huge) 0.26f else 0.14f) * scale
            isFakeBoldText = true
        }
        val cx = if (huge) w / 2f else origin.x.coerceIn(w * 0.3f, w * 0.7f)
        val cy = if (huge) h * 0.42f else origin.y
        drawContext.canvas.nativeCanvas.apply {
            save()
            rotate(if (huge) -6f else -3f, cx, cy)
            val baseline = cy + paint.textSize * 0.36f
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.argb((255 * fade).toInt(), 0x7A, 0x0A, 0x1C)
            drawText(text, cx + paint.textSize * 0.07f, baseline + paint.textSize * 0.07f, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = paint.textSize * 0.1f
            paint.strokeJoin = android.graphics.Paint.Join.ROUND
            paint.color = android.graphics.Color.argb((255 * fade).toInt(), 0, 0, 0)
            drawText(text, cx, baseline, paint)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.argb((255 * fade).toInt(), 0xFF, 0xD6, 0x0A)
            drawText(text, cx, baseline, paint)
            restore()
        }
    }
}

/**
 * Flat-shaded flames licking around a rectangle — "on fire" indicator. Draw it behind the thing
 * that is on fire; it extends a little beyond its own bounds, so give it padding.
 */
@Composable
fun FlameFrame(modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = 14
        for (layer in 0 until 3) {
            val color = when (layer) { 0 -> Red; 1 -> Color(0xFFF08A1E); else -> Gold }
            val height = h * (0.55f - layer * 0.14f)
            val path = Path()
            path.moveTo(0f, h)
            for (i in 0..n) {
                val x = w * i / n
                val flick = sin(t * (6f + layer * 2f) + i * 1.7f + layer) * 0.5f + 0.5f
                val tip = h - height * (0.45f + 0.55f * flick)
                path.lineTo(x - w / n * 0.5f, h - height * 0.25f)
                path.lineTo(x, tip)
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color.copy(alpha = 0.9f - layer * 0.15f))
        }
    }
}
