package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.dartsapp.logic.Music
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 33 1/3 rpm in degrees a second. */
private const val RPM33_DEG = 200f

/**
 * The menu's turntable: a vinyl record sunk into the bottom edge of the screen so only its top half
 * shows, spinning at 33 1/3 with the menu tune. Its label is a little low-poly dartboard in the same
 * sunset colours as the sun on the horizon, the grooves catch the pink and orange of the sky, a
 * chrome tonearm rests on it, and the rim glows neon pink like the signs.
 *
 * Put a finger on it and drag round to scratch: the record follows your hand (backwards too) and a
 * "ffff-aaah" vocal stab is cut over the top of the tune, which keeps playing underneath. Let go and
 * the motor brings the record back up to speed.
 */
@Composable
fun VinylRecord(modifier: Modifier = Modifier) {
    var angle by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(RPM33_DEG) }     // degrees a second
    var dragging by remember { mutableStateOf(false) }
    var lastDragNs by remember { mutableStateOf(0L) }
    var dragVel by remember { mutableStateOf(0f) }

    // the motor
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            if (!dragging) {
                speed += (RPM33_DEG - speed) * (dt * 6f).coerceAtMost(1f)
                angle = (angle + speed * dt) % 360f
            } else if (now - lastDragNs > 80_000_000L) {
                dragVel = 0f        // finger held still: the record is stopped under it
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragging = true; dragVel = 0f; lastDragNs = System.nanoTime(); Music.scratchStart() },
                onDragEnd = { dragging = false; speed = dragVel.coerceIn(-1500f, 1500f); Music.scratchEnd() },
                onDragCancel = { dragging = false; speed = dragVel; Music.scratchEnd() },
                onDrag = { change, _ ->
                    change.consume()
                    val c = Offset(size.width / 2f, size.height.toFloat())
                    val a0 = Math.toDegrees(atan2((change.previousPosition.y - c.y).toDouble(), (change.previousPosition.x - c.x).toDouble())).toFloat()
                    val a1 = Math.toDegrees(atan2((change.position.y - c.y).toDouble(), (change.position.x - c.x).toDouble())).toFloat()
                    var d = a1 - a0
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    angle = (angle + d) % 360f
                    Music.scratchBy(d / RPM33_DEG.toDouble())
                    val now = System.nanoTime()
                    val dt = ((now - lastDragNs) / 1e9f).coerceAtLeast(0.004f)
                    dragVel = dragVel * 0.6f + (d / dt) * 0.4f
                    lastDragNs = now
                }
            )
        }
    ) {
        val w = size.width; val h = size.height
        val r = h * 0.98f
        val c = Offset(w / 2f, h + r * 0.02f)

        // neon rim glow, like the signs
        drawCircle(Brush.radialGradient(
            0.9f to Color.Transparent, 0.97f to NeonPink.copy(alpha = 0.45f), 1f to Color.Transparent,
            center = c, radius = r * 1.06f), radius = r * 1.06f, center = c)
        // the vinyl
        drawCircle(Color(0xFF0E0714), r, c)
        drawCircle(NeonPink, r, c, style = Stroke(width = r * 0.012f))
        // grooves: fine rings, with lighter gaps between the tracks
        var gr = r * 0.95f
        var k = 0
        while (gr > r * 0.4f) {
            val gap = k % 9 == 8
            drawCircle(if (gap) Color(0xFF3A2450) else Color(0xFF1C1026), gr, c, style = Stroke(width = if (gap) r * 0.012f else r * 0.004f))
            gr -= r * 0.018f; k++
        }
        // the sky's light catching the grooves: two fixed sheens (they don't turn with the record)
        for ((mid, col) in listOf(-130f to Color(0xFFFF4F8B), -50f to Color(0xFFFFB13B))) {
            drawArc(
                Brush.radialGradient(0.4f to Color.Transparent, 0.7f to col.copy(alpha = 0.33f), 0.95f to Color.Transparent, center = c, radius = r),
                startAngle = mid - 14f, sweepAngle = 28f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r)
            )
        }

        rotate(angle, pivot = c) {
            // a faint pressing mark on the vinyl so the spin reads even at the edge
            drawArc(Color(0xFF2A1838), -20f, 40f, false, Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = Stroke(width = r * 0.01f))
            // label: a little low-poly dartboard in the sun's colours
            val lr = r * 0.36f
            for (i in 0 until 20) {
                val a0 = Math.toRadians((i * 18 - 99).toDouble()); val a1 = Math.toRadians(((i + 1) * 18 - 99).toDouble())
                drawPath(Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + lr * cos(a0).toFloat(), c.y + lr * sin(a0).toFloat())
                    lineTo(c.x + lr * cos(a1).toFloat(), c.y + lr * sin(a1).toFloat()); close()
                }, if (i % 2 == 0) Color(0xFFFFB13B) else Color(0xFFFF4F8B))
            }
            drawCircle(DarkRed, lr * 0.62f, c, style = Stroke(width = lr * 0.08f))
            drawCircle(DarkRed, lr * 0.98f, c, style = Stroke(width = lr * 0.06f))
            // a "sticker" on the label so you can see it turn
            drawCircle(PaleGold, lr * 0.1f, Offset(c.x, c.y - lr * 0.8f))
            drawLine(Night, Offset(c.x - lr * 0.3f, c.y - lr * 0.45f), Offset(c.x + lr * 0.3f, c.y - lr * 0.45f), strokeWidth = lr * 0.05f, cap = StrokeCap.Round)
        }
        drawCircle(Color(0xFFFFF1A8), r * 0.03f, c)     // the spindle

        // chrome tonearm from a pivot off to the right, its headshell resting in the grooves
        val pivot = Offset(c.x + r * 1.02f, c.y - r * 0.62f)
        val head = Offset(c.x + r * 0.42f, c.y - r * 0.72f)
        drawCircle(Color(0xFF5A5A6E), r * 0.09f, pivot)
        drawCircle(Color(0xFFD8DAE6), r * 0.06f, pivot)
        drawLine(Color(0xFF6E7084), pivot, head, strokeWidth = r * 0.04f, cap = StrokeCap.Round)
        drawLine(Color(0xFFEDEFF5), pivot, head, strokeWidth = r * 0.016f, cap = StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(head.x + r * 0.03f, head.y - r * 0.04f); lineTo(head.x - r * 0.08f, head.y - r * 0.02f)
            lineTo(head.x - r * 0.07f, head.y + r * 0.06f); lineTo(head.x + r * 0.03f, head.y + r * 0.04f); close()
        }, Color(0xFFB9BDC9))
        drawCircle(NeonPink, r * 0.012f, Offset(head.x - r * 0.06f, head.y + r * 0.05f))
    }
}
