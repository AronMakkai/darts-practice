package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.dartsapp.logic.Music
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 33 1/3 rpm in degrees a second. */
private const val RPM33_DEG = 200f
/** The stylus tip, relative to the record's centre in record radii (the tonearm is drawn to it). */
private const val TIP_X = 0.36f
private const val TIP_Y = -0.67f
/** Where the needle sits on the record, as a screen angle from the centre (degrees, 0 = 3 o'clock). */
private val NEEDLE_DEG = Math.toDegrees(atan2(TIP_Y.toDouble(), TIP_X.toDouble())).toFloat()
/** Sound comes out of the speaker a little after we ask for it: lead the needle by this much. */
private const val AUDIO_LEAD_SEC = 0.07f
/** How much of the record the scratch sample is pressed onto, starting at the dot. */
private const val SAMPLE_ARC = 160f

/**
 * The menu's turntable: a vinyl record sunk into the bottom edge of the screen so only its top half
 * shows, spinning at 33 1/3 with the menu tune. Its label is a little low-poly dartboard in the same
 * sunset colours as the sun on the horizon, the grooves catch the pink and orange of the sky, a
 * chrome tonearm rests on it, and the rim glows neon pink like the signs.
 *
 * A classic "ahhh... yeahhh!" is pressed onto a short arc of the record that starts at the white dot
 * on the label, and the needle is always reading it. Hold the SCRATCH button (the fader) to hear it:
 * with the record just spinning, the sample comes round once a turn; put a finger on the record and
 * drag to cut it back and forth (line the dot up with the needle for the start). Let go and it
 * carries your flick, then the motor brings it back up to speed and it plays round and round again.
 * The menu tune keeps playing underneath.
 */
@Composable
fun VinylRecord(modifier: Modifier = Modifier) {
    var angle by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(RPM33_DEG) }     // degrees a second
    var dragging by remember { mutableStateOf(false) }
    var lastDragNs by remember { mutableStateOf(0L) }
    var dragVel by remember { mutableStateOf(0f) }

    /** Tell the needle where the record is: how far the dot has turned past it, as a fraction of the sample. */
    fun reportNeedle(degPerSec: Float) {
        var u = ((angle % 360f) + 360f) % 360f
        if (u > 300f) u -= 360f          // just before the dot
        // lead by the output delay, so the "ahh" is heard exactly as the white line meets the needle
        u += degPerSec * AUDIO_LEAD_SEC
        Music.scratchTo((u / SAMPLE_ARC).toDouble(), (degPerSec / SAMPLE_ARC).toDouble())
    }

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
                reportNeedle(speed)
            } else if (now - lastDragNs > 80_000_000L) {
                dragVel = 0f        // finger held still: the record is stopped under it
                reportNeedle(0f)
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragging = true; dragVel = 0f; lastDragNs = System.nanoTime() },
                // let go: the record carries your flick, then the motor takes it back up to speed
                onDragEnd = { dragging = false; speed = dragVel.coerceIn(-1500f, 1500f) },
                onDragCancel = { dragging = false; speed = dragVel },
                onDrag = { change, _ ->
                    change.consume()
                    val c = Offset(size.width / 2f, size.height.toFloat())
                    val a0 = Math.toDegrees(atan2((change.previousPosition.y - c.y).toDouble(), (change.previousPosition.x - c.x).toDouble())).toFloat()
                    val a1 = Math.toDegrees(atan2((change.position.y - c.y).toDouble(), (change.position.x - c.x).toDouble())).toFloat()
                    var d = a1 - a0
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    angle = (angle + d) % 360f
                    val now = System.nanoTime()
                    val dt = ((now - lastDragNs) / 1e9f).coerceAtLeast(0.004f)
                    dragVel = dragVel * 0.6f + (d / dt) * 0.4f
                    lastDragNs = now
                    reportNeedle(dragVel)
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
            // the scratch sample: a band of slightly shinier groove where it is pressed, running back
            // from the dot (the part that passes the needle as the record turns forward)
            drawArc(Color(0xFF4A2E66).copy(alpha = 0.55f), NEEDLE_DEG - SAMPLE_ARC, SAMPLE_ARC, false,
                Offset(c.x - r * 0.72f, c.y - r * 0.72f), Size(r * 1.44f, r * 1.44f), style = Stroke(width = r * 0.42f))
            // the white dot on the label marking where the sample starts (it lines up with the needle at the cue)
            val da = Math.toRadians(NEEDLE_DEG.toDouble())
            val dot = Offset(c.x + lr * 0.78f * cos(da).toFloat(), c.y + lr * 0.78f * sin(da).toFloat())
            drawCircle(Night, lr * 0.13f, dot)
            drawCircle(Color.White, lr * 0.1f, dot)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x + lr * 0.98f * cos(da).toFloat(), c.y + lr * 0.98f * sin(da).toFloat()),
                Offset(c.x + r * 0.94f * cos(da).toFloat(), c.y + r * 0.94f * sin(da).toFloat()), strokeWidth = r * 0.006f)
        }
        drawCircle(Color(0xFFFFF1A8), r * 0.03f, c)     // the spindle

        // chrome tonearm from a pivot off to the right, its headshell resting in the grooves
        val pivot = Offset(c.x + r * 1.02f, c.y - r * 0.62f)
        val tip = Offset(c.x + r * TIP_X, c.y + r * TIP_Y)
        val head = Offset(tip.x + r * 0.06f, tip.y - r * 0.05f)
        drawCircle(Color(0xFF5A5A6E), r * 0.09f, pivot)
        drawCircle(Color(0xFFD8DAE6), r * 0.06f, pivot)
        drawLine(Color(0xFF6E7084), pivot, head, strokeWidth = r * 0.04f, cap = StrokeCap.Round)
        drawLine(Color(0xFFEDEFF5), pivot, head, strokeWidth = r * 0.016f, cap = StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(head.x + r * 0.03f, head.y - r * 0.04f); lineTo(head.x - r * 0.08f, head.y - r * 0.02f)
            lineTo(head.x - r * 0.07f, head.y + r * 0.06f); lineTo(head.x + r * 0.03f, head.y + r * 0.04f); close()
        }, Color(0xFFB9BDC9))
        drawCircle(NeonPink, r * 0.012f, tip)
    }
}


/**
 * The fader: a neon SCRATCH button beside the turntable. Hold it to open the fader and hear the
 * needle on the record; let go to cut it. Tap it while you work the record for chirps and stabs.
 */
@Composable
fun ScratchButton(modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { Music.setFader(false) } }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                held = true; Music.setFader(true)
                try { tryAwaitRelease() } finally { held = false; Music.setFader(false) }
            })
        }
    ) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val r = minOf(w, h) / 2f
        val neon = if (held) NeonTeal else NeonPink
        // glow, plate, tube
        drawCircle(Brush.radialGradient(0.55f to neon.copy(alpha = if (held) 0.75f else 0.35f), 1f to Color.Transparent, center = c, radius = r), r, c)
        drawCircle(Color(0xFF1E082C).copy(alpha = 0.85f), r * 0.68f, c)
        drawCircle(neon, r * 0.68f, c, style = Stroke(width = r * 0.08f))
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.68f, c, style = Stroke(width = r * 0.025f))
        // a little fader slot with its knob: over to the open side while held
        drawLine(Color(0xFF5A3A70), Offset(c.x - r * 0.38f, c.y + r * 0.22f), Offset(c.x + r * 0.38f, c.y + r * 0.22f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
        val kx = if (held) c.x + r * 0.38f else c.x - r * 0.38f
        drawRoundRect(if (held) NeonTeal else PaleGold, Offset(kx - r * 0.08f, c.y + r * 0.1f), Size(r * 0.16f, r * 0.24f), androidx.compose.ui.geometry.CornerRadius(r * 0.04f))
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textSize = r * 0.25f; color = if (held) android.graphics.Color.WHITE else android.graphics.Color.rgb(0xFF, 0xE3, 0xA3)
        }
        drawContext.canvas.nativeCanvas.drawText("SCRATCH", c.x, c.y - r * 0.02f, paint)
    }
}
