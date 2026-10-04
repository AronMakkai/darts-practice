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
            androidx.compose.foundation.gestures.detectTapGestures { p -> onChange((p.x / size.width).coerceIn(0f, 1f)) }
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
