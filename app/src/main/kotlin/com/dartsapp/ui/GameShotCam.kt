package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Length of the game-shot replay, in seconds. */
const val GAME_SHOT_SECONDS = 2.6f
private const val ZOOM = 2.6f

/**
 * The game-shot replay clock: -1 when idle, otherwise seconds since [trigger] last changed (to a
 * non-zero value). Plays the impact thud at the right moment.
 */
@Composable
fun rememberGameShotClock(trigger: Int): Float {
    var t by remember { mutableStateOf(-1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) { t = -1f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val s = (withFrameNanos { it } - start) / 1_000_000_000f
            t = s
            if (s > GAME_SHOT_SECONDS) { t = -1f; break }
        }
    }
    return t
}

private const val IMPACT = 1.25f      // when the dart lands (seconds)

/** How far in the camera is at time [t]: eases in, holds, eases back out. */
private fun zoomAt(t: Float): Float {
    if (t < 0f) return 1f
    val k = when {
        t < 0.4f -> smooth(t / 0.4f)
        t < GAME_SHOT_SECONDS - 0.45f -> 1f
        else -> 1f - smooth(((t - (GAME_SHOT_SECONDS - 0.45f)) / 0.45f).coerceIn(0f, 1f))
    }
    return 1f + (ZOOM - 1f) * k
}

private fun smooth(x: Float) = x * x * (3f - 2f * x)

/**
 * Camera on the board for the replay: clips to the board area and zooms in, centring on the dart's
 * landing point [nx], [ny] (board coordinates, 1.0 = board radius).
 */
fun Modifier.gameShotCamera(t: Float, nx: Float, ny: Float): Modifier =
    if (t < 0f) this else this.clipToBounds().graphicsLayer {
        val z = zoomAt(t)
        val k = (z - 1f) / (ZOOM - 1f)
        val dx = nx / (2f * RIM_SCALE) * size.width
        val dy = ny / (2f * RIM_SCALE) * size.height
        scaleX = z; scaleY = z
        translationX = -dx * z * k
        translationY = -dy * z * k
    }

/**
 * The slow-motion dart: it glides in from the lower right with ghost trails and speed lines,
 * decelerating, punches into the board at the landing point with a flash, then shivers to a stop.
 * Draw it inside the zoomed board box, over the board.
 */
@Composable
fun GameShotDart(t: Float, nx: Float, ny: Float, modifier: Modifier = Modifier) {
    if (t >= 0f) {
        Canvas(modifier = modifier) {
            val w = size.width
            val tip = Offset(w / 2f + nx / (2f * RIM_SCALE) * w, size.height / 2f + ny / (2f * RIM_SCALE) * size.height)
            val len = w * 0.13f
            val angle = -32f                       // the dart points up and to the left, into the board
            val travel = w * 0.55f
            val dir = Offset(cos(Math.toRadians(angle.toDouble())).toFloat(), sin(Math.toRadians(angle.toDouble())).toFloat())
            val start = 0.25f
            if (t < IMPACT) {
                if (t < start) return@Canvas
                // decelerating approach (slow motion feel): fast at first, crawling in at the end
                val u = ((t - start) / (IMPACT - start)).coerceIn(0f, 1f)
                val e = 1f - (1f - u) * (1f - u) * (1f - u)
                val back = travel * (1f - e)
                // speed lines
                for (i in 0 until 6) {
                    val off = (i - 2.5f) * len * 0.12f
                    val p0 = Offset(tip.x - dir.x * (back + len * 1.2f) - dir.y * off, tip.y - dir.y * (back + len * 1.2f) + dir.x * off)
                    val p1 = Offset(p0.x - dir.x * len * (0.8f + i % 3 * 0.3f), p0.y - dir.y * len * (0.8f + i % 3 * 0.3f))
                    drawLine(Color.White.copy(alpha = 0.25f * (1f - u)), p0, p1, strokeWidth = 2f, cap = StrokeCap.Round)
                }
                // ghost trail, then the dart itself
                for (g in 3 downTo 1) {
                    val gb = back + travel * 0.06f * g * (1f - u)
                    drawDart(Offset(tip.x - dir.x * gb, tip.y - dir.y * gb), angle, len, alpha = 0.12f * (4 - g) * (1f - u * 0.5f))
                }
                drawDart(Offset(tip.x - dir.x * back, tip.y - dir.y * back), angle, len, alpha = 1f)
            } else {
                val s = t - IMPACT
                // impact flash
                if (s < 0.3f) {
                    val k = s / 0.3f
                    drawCircle(BrightGold.copy(alpha = 1f - k), len * (0.15f + 0.6f * k), tip, style = Stroke(width = len * 0.05f * (1f - k) + 1f))
                }
                // the shiver as it buries in
                val wobble = 7f * sin(s * 42f) * exp(-s * 6f)
                drawDart(tip, angle + wobble, len, alpha = 1f)
            }
        }
    }
}

/** A dart drawn with its tip at [tip], pointing along [angleDeg]: steel point, knurled barrel, shaft, red neon flights. */
private fun DrawScope.drawDart(tip: Offset, angleDeg: Float, len: Float, alpha: Float) {
    rotate(angleDeg + 180f, pivot = tip) {
        // after rotating, the dart runs from the tip along +x
        val x0 = tip.x; val y = tip.y
        val steel = Color(0xFFD8D8E0).copy(alpha = alpha)
        drawLine(steel, Offset(x0, y), Offset(x0 + len * 0.18f, y), strokeWidth = len * 0.025f, cap = StrokeCap.Round)                 // point
        drawLine(Color(0xFF9A9AA8).copy(alpha = alpha), Offset(x0 + len * 0.18f, y), Offset(x0 + len * 0.52f, y), strokeWidth = len * 0.075f, cap = StrokeCap.Round) // barrel
        for (r in 0 until 6) {
            val gx = x0 + len * (0.24f + r * 0.045f)
            drawLine(Color(0xFF5A5A66).copy(alpha = alpha), Offset(gx, y - len * 0.036f), Offset(gx, y + len * 0.036f), strokeWidth = len * 0.012f)      // grip rings
        }
        drawLine(Color(0xFF1A1A22).copy(alpha = alpha), Offset(x0 + len * 0.52f, y), Offset(x0 + len * 0.78f, y), strokeWidth = len * 0.04f)       // shaft
        val flight = Path().apply {
            moveTo(x0 + len * 0.72f, y); lineTo(x0 + len * 0.86f, y - len * 0.16f); lineTo(x0 + len * 1.0f, y - len * 0.16f); lineTo(x0 + len * 0.98f, y)
            lineTo(x0 + len * 1.0f, y + len * 0.16f); lineTo(x0 + len * 0.86f, y + len * 0.16f); close()
        }
        drawPath(flight, Color(0xFFFF2A45).copy(alpha = alpha))
        drawPath(flight, Color(0xFFFFE066).copy(alpha = alpha), style = Stroke(width = len * 0.015f))
    }
}
