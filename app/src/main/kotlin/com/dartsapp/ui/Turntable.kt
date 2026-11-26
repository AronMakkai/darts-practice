package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
private const val AUDIO_LEAD_SEC = 0.04f
/** How much of the record the scratch sample is pressed onto, starting at the dot. */
private val SAMPLE_ARC = Music.SAMPLE_ARC.toFloat()
/** The second sample ("freeeshhh") starts half a turn round from the first. */
private val SECOND_CUE = Music.SECOND_CUE.toFloat()

/**
 * The menu's turntable: a vinyl record sunk into the bottom edge of the screen so only its top half
 * shows, spinning at 33 1/3 with the menu tune. Its label is a little low-poly dartboard in the same
 * sunset colours as the sun on the horizon, the grooves catch the pink and orange of the sky, a
 * chrome tonearm rests on it, and the rim glows neon pink like the signs.
 *
 * Two samples are pressed onto the record, each on a short arc starting at a dot on the label: a
 * classic "ahhh... yeahhh!" from the white dot and "freeeshhh" from the teal one, half a turn round.
 * The needle is always reading them. With the motor on (START), the sample comes
 * round once a turn. Touch the record and it stops dead under your finger; drag to cut it back and
 * forth (line the dot up with the needle for the start). Let go and it carries your flick, then the
 * motor takes it back up to speed (or, with the motor off, it coasts to a stop). Hold CUT (the
 * fader) to kill the sound. The menu tune keeps playing underneath.
 */
@Composable
fun VinylRecord(motorOn: Boolean, modifier: Modifier = Modifier) {
    val motor by rememberUpdatedState(motorOn)
    var angle by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(if (motorOn) RPM33_DEG else 0f) }     // degrees a second
    var dragging by remember { mutableStateOf(false) }
    var lastDragNs by remember { mutableStateOf(0L) }
    var dragVel by remember { mutableStateOf(0f) }

    /** Tell the needle where the record is: how far the dot has turned past it, as a fraction of the sample. */
    fun reportNeedle(degPerSec: Float) {
        // how far the record has turned past the first cue dot; the sound engine knows where the samples are.
        // Lead by the output delay, so a sample is heard exactly as its line meets the needle.
        val u = ((angle % 360f) + 360f) % 360f + degPerSec * AUDIO_LEAD_SEC
        Music.scratchTo(u.toDouble(), degPerSec.toDouble())
    }

    // the motor
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            if (!dragging) {
                // the motor pulls it up to 33 1/3 quickly; off, the brake brings it to a stop in about a second
                if (motor) speed += (RPM33_DEG - speed) * (dt * 6f).coerceAtMost(1f)
                else { val b = 260f * dt; speed = if (kotlin.math.abs(speed) <= b) 0f else speed - b * kotlin.math.sign(speed) }
                angle = (angle + speed * dt) % 360f
                reportNeedle(speed)
            } else if (now - lastDragNs > 40_000_000L) {
                dragVel = 0f        // finger held still: the record is stopped under it
                reportNeedle(0f)
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            // raw touches, no drag threshold: the record stops the instant you touch it and follows
            // every movement of your finger from the very first pixel
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                dragging = true; dragVel = 0f; lastDragNs = System.nanoTime()
                reportNeedle(0f)
                val c = Offset(size.width / 2f, size.height.toFloat())
                fun ang(p: Offset) = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                var lastAng = ang(down.position)
                try {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val a1 = ang(ch.position)
                        var d = a1 - lastAng
                        lastAng = a1
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        ch.consume()
                        if (d == 0f) continue
                        angle = (angle + d) % 360f
                        val now = System.nanoTime()
                        val dt = ((now - lastDragNs) / 1e9f).coerceAtLeast(0.003f)
                        dragVel = dragVel * 0.5f + (d / dt) * 0.5f
                        lastDragNs = now
                        reportNeedle(dragVel)
                    }
                } finally {
                    // let go: the record carries your flick, then the motor takes over
                    dragging = false
                    speed = if (System.nanoTime() - lastDragNs > 40_000_000L) 0f else dragVel.coerceIn(-1500f, 1500f)
                }
            }
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
            // the two samples: bands of slightly shinier groove where they are pressed, each running back
            // from its cue (the part that passes the needle as the record turns forward), and a dot on
            // the label with a line out across the grooves marking where each starts.
            // White: "ahhh... yeahhh!"   Teal: "freeeshhh"
            for ((cue, mark) in listOf(0f to Color.White, SECOND_CUE to NeonTeal)) {
                val start = NEEDLE_DEG - cue
                drawArc((if (cue == 0f) Color(0xFF4A2E66) else Color(0xFF1E4A5A)).copy(alpha = 0.55f), start - SAMPLE_ARC, SAMPLE_ARC, false,
                    Offset(c.x - r * 0.72f, c.y - r * 0.72f), Size(r * 1.44f, r * 1.44f), style = Stroke(width = r * 0.42f))
                val da = Math.toRadians(start.toDouble())
                val dot = Offset(c.x + lr * 0.78f * cos(da).toFloat(), c.y + lr * 0.78f * sin(da).toFloat())
                drawCircle(Night, lr * 0.13f, dot)
                drawCircle(mark, lr * 0.1f, dot)
                drawLine(mark.copy(alpha = 0.55f), Offset(c.x + lr * 0.98f * cos(da).toFloat(), c.y + lr * 0.98f * sin(da).toFloat()),
                    Offset(c.x + r * 0.94f * cos(da).toFloat(), c.y + r * 0.94f * sin(da).toFloat()), strokeWidth = r * 0.006f)
            }
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


/** A round neon deck button: glow, dark plate, tube ring, and a label. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.deckButton(neon: Color, lit: Boolean, label: String, labelColor: Int) {
    val w = size.width; val h = size.height
    val c = Offset(w / 2f, h / 2f)
    val r = minOf(w, h) / 2f
    drawCircle(Brush.radialGradient(0.55f to neon.copy(alpha = if (lit) 0.75f else 0.3f), 1f to Color.Transparent, center = c, radius = r), r, c)
    drawCircle(Color(0xFF1E082C).copy(alpha = 0.85f), r * 0.68f, c)
    drawCircle(neon, r * 0.68f, c, style = Stroke(width = r * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.68f, c, style = Stroke(width = r * 0.025f))
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textSize = r * (if (label.length > 4) 0.26f else 0.34f); color = labelColor
    }
    drawContext.canvas.nativeCanvas.drawText(label, c.x, c.y - r * 0.02f, paint)
}

/**
 * The fader: a neon CUT button beside the turntable. The sound is open; press and hold to kill it,
 * let go to open it again — tap it while you work the record for chirps and stabs. It reacts the
 * moment your finger lands, not when it lifts.
 */
@Composable
fun CutButton(modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { Music.setFader(true) } }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                held = true; Music.setFader(false)
                try { waitForUpOrCancellation() } finally { held = false; Music.setFader(true) }
            }
        }
    ) {
        val r = minOf(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        deckButton(if (held) Red else NeonPink, held, "CUT",
            if (held) android.graphics.Color.WHITE else android.graphics.Color.rgb(0xFF, 0xE3, 0xA3))
        // the fader knob: across to the closed side while held
        drawLine(Color(0xFF5A3A70), Offset(c.x - r * 0.38f, c.y + r * 0.24f), Offset(c.x + r * 0.38f, c.y + r * 0.24f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
        val kx = if (held) c.x - r * 0.38f else c.x + r * 0.38f
        drawRoundRect(if (held) Red else PaleGold, Offset(kx - r * 0.08f, c.y + r * 0.12f), Size(r * 0.16f, r * 0.24f), androidx.compose.ui.geometry.CornerRadius(r * 0.04f))
    }
}

/** The deck's START/STOP button: turns the turntable's motor on and off. */
@Composable
fun StartStopButton(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val toggle by rememberUpdatedState(onToggle)
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown().consume(); toggle() }
        }
    ) {
        val r = minOf(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        deckButton(if (on) NeonTeal else NeonPink, on, if (on) "STOP" else "START",
            if (on) android.graphics.Color.WHITE else android.graphics.Color.rgb(0xFF, 0xE3, 0xA3))
        // the strobe light: lit while the platter turns
        drawCircle(if (on) Color(0xFFFF3B3B) else Color(0xFF4A2030), r * 0.07f, Offset(c.x, c.y + r * 0.32f))
    }
}


/** Before the record player is won: just a faint neon outline of where it will sit. */
@Composable
fun EmptyDeckOutline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.height * 0.98f
        val c = Offset(size.width / 2f, size.height + r * 0.02f)
        drawCircle(NeonPink.copy(alpha = 0.22f), r, c, style = Stroke(width = r * 0.012f))
    }
}
