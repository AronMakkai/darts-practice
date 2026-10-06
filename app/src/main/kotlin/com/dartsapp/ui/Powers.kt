package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One lizard's wander: a Lissajous loop around the board. */
private class LizardPath(val rx: Float, val ry: Float, val fx: Float, val fy: Float, val phase: Float, val scale: Float, val tint: Color)

private val LIZARDS = listOf(
    LizardPath(0.36f, 0.30f, 0.50f, 0.74f, 0.0f, 1.00f, Color(0xFF4C8F3A)),
    LizardPath(0.30f, 0.38f, 0.66f, 0.42f, 1.7f, 0.85f, Color(0xFF6B8E23)),
    LizardPath(0.40f, 0.22f, 0.38f, 0.90f, 3.1f, 1.10f, Color(0xFF3F7D4E)),
    LizardPath(0.22f, 0.40f, 0.82f, 0.56f, 4.4f, 0.75f, Color(0xFF8A9A3B)),
    LizardPath(0.34f, 0.34f, 0.58f, 0.62f, 5.6f, 0.95f, Color(0xFF557A2F)),
    LizardPath(0.16f, 0.18f, 0.94f, 0.70f, 2.4f, 0.70f, Color(0xFF4C8F3A)),
    LizardPath(0.38f, 0.14f, 0.62f, 1.02f, 0.9f, 0.90f, Color(0xFF6B8E23)),
    LizardPath(0.12f, 0.36f, 1.04f, 0.48f, 3.8f, 0.80f, Color(0xFF3F7D4E)),
    LizardPath(0.28f, 0.26f, 0.72f, 0.86f, 5.0f, 1.05f, Color(0xFF8A9A3B)),
    LizardPath(0.42f, 0.40f, 0.34f, 0.52f, 1.2f, 0.85f, Color(0xFF557A2F)),
    LizardPath(0.20f, 0.10f, 0.88f, 1.10f, 4.0f, 0.65f, Color(0xFF4C8F3A))
)

/** The Lizzard's special power: lizards crawl all over the board. Purely visual; taps pass through. */
@Composable
fun LizardSwarm(modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        for (l in LIZARDS) {
            fun pos(time: Float) = Offset(
                w / 2f + w * l.rx * cos(time * l.fx + l.phase),
                h / 2f + h * l.ry * sin(time * l.fy + l.phase * 1.3f)
            )
            val p = pos(t)
            val ahead = pos(t + 0.05f)
            val heading = Math.toDegrees(atan2((ahead.y - p.y).toDouble(), (ahead.x - p.x).toDouble())).toFloat()
            withTransform({
                translate(p.x, p.y)
                rotate(heading, Offset.Zero)
            }) { drawLizard(w * 0.26f * l.scale, t * 9f + l.phase, l.tint) }
        }
    }
}

/** A lizard facing +x, centred on the origin, [len] long; [gait] drives the legs and tail. */
private fun DrawScope.drawLizard(len: Float, gait: Float, body: Color) {
    val dark = Color(0xFF24401C)
    val belly = Color(0xFFB9C77A)
    val swing = sin(gait)
    // Legs: diagonal pairs move together
    fun leg(x: Float, side: Float, s: Float) {
        val knee = Offset(x + len * 0.05f * s, side * len * 0.12f)
        val foot = Offset(x + len * 0.12f * s, side * len * 0.2f)
        drawLine(dark, Offset(x, side * len * 0.04f), knee, strokeWidth = len * 0.035f, cap = StrokeCap.Round)
        drawLine(dark, knee, foot, strokeWidth = len * 0.03f, cap = StrokeCap.Round)
        for (toe in -1..1) drawLine(dark, foot, Offset(foot.x + len * 0.04f, foot.y + toe * len * 0.025f + side * len * 0.015f), strokeWidth = len * 0.012f, cap = StrokeCap.Round)
    }
    leg(len * 0.12f, -1f, swing); leg(len * 0.12f, 1f, -swing)
    leg(-len * 0.16f, -1f, -swing); leg(-len * 0.16f, 1f, swing)
    // Tail: tapering, wagging
    val tail = Path().apply {
        moveTo(-len * 0.2f, -len * 0.045f)
        quadraticBezierTo(-len * 0.4f, len * 0.1f * swing, -len * 0.6f, -len * 0.08f * swing)
        quadraticBezierTo(-len * 0.4f, len * 0.1f * swing + len * 0.03f, -len * 0.2f, len * 0.045f)
        close()
    }
    drawPath(tail, body)
    // Body and head
    drawOval(body, Offset(-len * 0.25f, -len * 0.08f), Size(len * 0.47f, len * 0.16f))
    drawOval(belly.copy(alpha = 0.55f), Offset(-len * 0.2f, -len * 0.025f), Size(len * 0.36f, len * 0.05f))
    drawOval(body, Offset(len * 0.17f, -len * 0.065f), Size(len * 0.22f, len * 0.13f))
    // Back markings
    for (i in 0..3) drawCircle(dark, len * 0.018f, Offset(-len * 0.17f + i * len * 0.09f, 0f))
    // Eyes and a flicking tongue
    drawCircle(Color(0xFFFFD54A), len * 0.02f, Offset(len * 0.3f, -len * 0.045f))
    drawCircle(Color(0xFFFFD54A), len * 0.02f, Offset(len * 0.3f, len * 0.045f))
    drawCircle(Color.Black, len * 0.009f, Offset(len * 0.305f, -len * 0.045f))
    drawCircle(Color.Black, len * 0.009f, Offset(len * 0.305f, len * 0.045f))
    if (sin(gait * 0.35f) > 0.6f) drawLine(Color(0xFFD0304A), Offset(len * 0.39f, 0f), Offset(len * 0.47f, 0f), strokeWidth = len * 0.012f, cap = StrokeCap.Round)
    drawPath(tail, dark.copy(alpha = 0.25f), style = Stroke(width = len * 0.008f))
}

/**
 * The Bling's special power: his jewellery throws the stage lights back in your eyes.
 * [strength] 0..1 — how washed-out the board gets. Purely visual; taps pass through.
 */
@Composable
fun BlingGlare(strength: Float, modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val s = strength.coerceIn(0f, 1f)
        val gold = Color(0xFFFFE9A0)
        // Overall wash, pulsing a little
        drawRect(Color.White.copy(alpha = (s * (0.5f + 0.08f * sin(t * 3.1f))).coerceIn(0f, 1f)))
        // The hot spot: a big flare that drifts across the board
        val c = Offset(w / 2f + w * 0.3f * cos(t * 0.9f), h / 2f + h * 0.26f * sin(t * 1.3f))
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(Color.White.copy(alpha = s), gold.copy(alpha = s * 0.7f), Color.Transparent),
                center = c, radius = w * 0.55f
            ),
            radius = w * 0.55f, center = c
        )
        // Streaks through the hot spot
        drawLine(Color.White.copy(alpha = s * 0.8f), Offset(c.x - w * 0.6f, c.y), Offset(c.x + w * 0.6f, c.y), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = s * 0.6f), Offset(c.x, c.y - h * 0.4f), Offset(c.x, c.y + h * 0.4f), strokeWidth = w * 0.008f, cap = StrokeCap.Round)
        // Twinkling four-point sparkles
        for (i in 0 until 9) {
            val px = w * (0.1f + 0.8f * ((i * 0.37f + 0.13f) % 1f))
            val py = h * (0.1f + 0.8f * ((i * 0.61f + 0.29f) % 1f))
            val tw = sin(t * (3f + i * 0.7f) + i * 1.9f)
            if (tw > 0f) {
                val r = w * 0.09f * tw
                val a = (s * tw).coerceIn(0f, 1f)
                drawLine(Color.White.copy(alpha = a), Offset(px - r, py), Offset(px + r, py), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = a), Offset(px, py - r), Offset(px, py + r), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
                drawCircle(gold.copy(alpha = a), r * 0.22f, Offset(px, py))
            }
        }
    }
}

private const val DRUNK_AGSL = """
uniform shader content;
uniform float time;
uniform float amp;
uniform float2 size;
half4 main(float2 p) {
    float2 q = p;
    q.x += amp * sin(p.y / size.y * 11.0 + time * 2.3);
    q.y += amp * cos(p.x / size.x * 9.0 + time * 1.7);
    return content.eval(q);
}
"""

/** The wavy part of the drunk effect. Needs Android 13+, so it lives in its own class. */
@androidx.annotation.RequiresApi(33)
private object DrunkWaves {
    private val shader = android.graphics.RuntimeShader(DRUNK_AGSL)
    fun effect(time: Float, amp: Float, w: Float, h: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("amp", amp)
        shader.setFloatUniform("size", w, h)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/**
 * The Jockey's special power: you've had a pint too many. The board sways and stretches ([sway], 0..1)
 * and, on Android 13+, ripples in waves ([wave], 0..1). [speed] scales how fast it all moves (1 = normal).
 * Pass `active = false` for no effect.
 */
@Composable
fun Modifier.drunk(active: Boolean, sway: Float, wave: Float, speed: Float): Modifier {
    var t by remember { mutableStateOf(0f) }
    val liveSpeed = rememberUpdatedState(speed)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t += (now - last) / 1_000_000_000f * liveSpeed.value   // integrate, so changing speed never jumps
            last = now
        }
    }
    val s = sway.coerceIn(0f, 1f)
    val wv = wave.coerceIn(0f, 1f)
    return if (!active) this else this.graphicsLayer {
        rotationZ = 8f * s * sin(t * 1.1f)
        translationX = size.width * 0.06f * s * sin(t * 0.8f)
        translationY = size.height * 0.035f * s * cos(t * 0.6f)
        scaleX = 1f + 0.08f * s * sin(t * 1.7f)
        scaleY = 1f + 0.08f * s * cos(t * 1.3f)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            renderEffect = DrunkWaves.effect(t, size.width * 0.06f * wv, size.width, size.height)
        }
    }
}

private val HECKLES = listOf(
    "MISS!", "BOTTLE JOB!", "NOT TODAY, SON", "BOOOO!", "YOU'LL CHOKE!", "WIDE!", "MY NAN THROWS BETTER",
    "SIT DOWN!", "WHO ARE YA?", "NO CHANCE!", "WOBBLY!", "HE'S GONE!", "GET ON WITH IT!", "SHAKY HANDS!"
)

/**
 * The Cockney's special power, part one: he turns the crowd on you. Heckles pop up over the board
 * and the crowd jeers. Purely visual (plus sound); taps pass through.
 */
@Composable
fun HeckleBubbles(modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
    // The crowd gets on your back every few seconds
    LaunchedEffect(Unit) {
        while (true) {
            com.dartsapp.logic.Sounds.groan()
            kotlinx.coroutines.delay(3200)
        }
    }
    val fill = remember { android.graphics.Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE } }
    val edge = remember { android.graphics.Paint().apply { isAntiAlias = true; color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE } }
    val ink = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true; color = android.graphics.Color.BLACK; textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val every = 0.8f      // seconds between heckles
        val life = 1.9f       // how long each one stays up
        val newest = (t / every).toInt()
        val canvas = drawContext.canvas.nativeCanvas
        ink.textSize = w * 0.062f
        edge.strokeWidth = w * 0.008f
        for (i in (newest - 3).coerceAtLeast(0)..newest) {
            val age = t - i * every
            if (age < 0f || age > life) continue
            val text = HECKLES[(i * 7 + 3) % HECKLES.size]
            val px = w * (0.22f + 0.56f * ((i * 0.618f + 0.21f) % 1f))
            val py = h * (0.14f + 0.72f * ((i * 0.377f + 0.43f) % 1f))
            val pop = if (age < 0.12f) age / 0.12f * 1.2f else if (age < 0.2f) 1.2f - (age - 0.12f) / 0.08f * 0.2f else 1f
            val alpha = (if (age > life - 0.4f) (life - age) / 0.4f else 1f).coerceIn(0f, 1f)
            val a = (alpha * 255).toInt()
            fill.alpha = a; edge.alpha = a; ink.alpha = a
            val tw = ink.measureText(text)
            val bw = tw + w * 0.07f
            val bh = w * 0.11f
            canvas.save()
            canvas.translate(px.coerceIn(bw / 2f, w - bw / 2f), py)
            canvas.rotate(((i * 37) % 21 - 10).toFloat())
            canvas.scale(pop, pop)
            val rect = android.graphics.RectF(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f)
            canvas.drawRoundRect(rect, bh * 0.3f, bh * 0.3f, fill)
            canvas.drawRoundRect(rect, bh * 0.3f, bh * 0.3f, edge)
            canvas.drawText(text, 0f, ink.textSize * 0.35f, ink)
            canvas.restore()
        }
    }
}

/**
 * The Cockney's special power, part two: nerves. Whatever this is applied to (the aim ring) trembles.
 * [amount] 0..1 sets how far it moves, [speed] how fast (1 = normal). Pass `active = false` for no effect.
 */
@Composable
fun Modifier.nerves(active: Boolean, amount: Float, speed: Float = 1f): Modifier {
    var t by remember { mutableStateOf(0f) }
    val liveSpeed = rememberUpdatedState(speed)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t += (now - last) / 1_000_000_000f * liveSpeed.value
            last = now
        }
    }
    val a = amount.coerceIn(0f, 1f)
    return if (!active) this else this.graphicsLayer {
        translationX = size.width * 0.12f * a * (sin(t * 31f) * 0.6f + sin(t * 47f + 1.3f) * 0.4f)
        translationY = size.height * 0.12f * a * (cos(t * 37f) * 0.6f + sin(t * 53f + 0.7f) * 0.4f)
    }
}
