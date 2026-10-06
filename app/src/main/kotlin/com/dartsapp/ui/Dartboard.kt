package com.dartsapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import com.dartsapp.data.Ring
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val BoardBlack = Color(0xFF141414)
internal val BoardCream = Color(0xFFEDE3C4)
private val BoardRed = Color(0xFFC8102E)
private val BoardGreen = Color(0xFF0A6B36)
private val BoardRim = Color(0xFF000000)
private val Wire = Color(0xFFCFCFCF)
private val GoldInt = 0xFFD4AF37.toInt()

/**
 * A full dartboard drawn with Canvas.
 *
 * @param geometry ring sizes — STANDARD looks like a real board, WIDE has fat rings for tapping
 * @param showValues draws the double and treble values on their rings (Value Checker mode)
 * @param marks points (normalised board coords, 1.0 = board radius) to draw as landed darts
 * @param focus finger position (normalised) — values near it are magnified
 * @param onTap called with the tap position in normalised board coords
 * @param onPointer called continuously with the finger position while touching, and null on release
 */
@Composable
fun Dartboard(
    modifier: Modifier = Modifier,
    geometry: BoardGeometry = BoardGeometry.STANDARD,
    showValues: Boolean = false,
    marks: List<Offset> = emptyList(),
    focus: Offset? = null,
    onTap: ((Offset) -> Unit)? = null,
    onPointer: ((Offset?) -> Unit)? = null
) {
    // pointerInput(Unit) keeps the lambda from the first composition, so always call the latest one
    val latestTap = androidx.compose.runtime.rememberUpdatedState(onTap)
    val latestPointer = androidx.compose.runtime.rememberUpdatedState(onPointer)
    var m = modifier.fillMaxWidth().aspectRatio(1f)
    if (onTap != null) {
        m = m.pointerInput(Unit) {
            detectTapGestures { pos ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(size.width, size.height) / 2f / RIM_SCALE
                latestTap.value?.invoke(Offset((pos.x - cx) / radius, (pos.y - cy) / radius))
            }
        }
    }
    if (onPointer != null) {
        m = m.pointerInput(Unit) {
            awaitEachGesture {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(size.width, size.height) / 2f / RIM_SCALE
                fun norm(p: Offset) = Offset((p.x - cx) / radius, (p.y - cy) / radius)
                val down = awaitFirstDown()
                latestPointer.value?.invoke(norm(down.position))
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                    if (!change.pressed) break
                    latestPointer.value?.invoke(norm(change.position))
                    change.consume()
                }
                latestPointer.value?.invoke(null)
            }
        }
    }
    // Landing animation for the newest mark: a small ring scales up into the dot and flashes red -> yellow
    val landState = remember { mutableStateOf(1f) }
    val seenState = remember { mutableStateOf(marks.size) }
    LaunchedEffect(marks.size) {
        if (marks.size > seenState.value) {
            val start = withFrameNanos { it }
            landState.value = 0f
            while (true) {
                val t = (withFrameNanos { it } - start) / 1_000_000_000f / 0.55f
                landState.value = t.coerceAtMost(1f)
                if (t >= 1f) break
            }
        }
        seenState.value = marks.size
    }
    Canvas(modifier = m) {
        drawBoard(geometry, showValues, marks, focus, landState.value)
    }
}

/** Board radius × this = full canvas radius (leaves room for the numbers ring). */
internal const val RIM_SCALE = 1.2f

private fun DrawScope.drawBoard(g: BoardGeometry, showValues: Boolean, marks: List<Offset>, focus: Offset?, landT: Float = 1f) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = min(size.width, size.height) / 2f / RIM_SCALE
    val center = Offset(cx, cy)

    // Outer rim / number ring with a thin gold edge
    drawCircle(BoardRim, radius = r * RIM_SCALE, center = center)
    drawCircle(Color(0xFFD4AF37), radius = r * RIM_SCALE - r * 0.01f, center = center, style = Stroke(width = r * 0.015f))

    fun wedge(index: Int, outer: Float, color: Color) {
        val start = -99f + index * 18f
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = 18f,
            useCenter = true,
            topLeft = Offset(cx - r * outer, cy - r * outer),
            size = Size(2 * r * outer, 2 * r * outer)
        )
    }

    for (i in 0 until 20) {
        val even = i % 2 == 0
        val single = if (even) BoardBlack else BoardCream
        val multi = if (even) BoardRed else BoardGreen
        wedge(i, g.doubleOut, multi)
        wedge(i, g.doubleIn, single)
        wedge(i, g.trebleOut, multi)
        wedge(i, g.trebleIn, single)
    }
    drawCircle(BoardGreen, radius = r * g.outerBullR, center = center)
    drawCircle(BoardRed, radius = r * g.bullR, center = center)

    // Wires
    val wireWidth = r * 0.006f
    for (ring in listOf(g.doubleOut, g.doubleIn, g.trebleOut, g.trebleIn, g.outerBullR, g.bullR)) {
        drawCircle(Wire, radius = r * ring, center = center, style = Stroke(width = wireWidth))
    }
    for (i in 0 until 20) {
        val a = Math.toRadians((-99f + i * 18f).toDouble())
        val inner = Offset(cx + r * g.outerBullR * cos(a).toFloat(), cy + r * g.outerBullR * sin(a).toFloat())
        val outer = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        drawLine(Wire, inner, outer, strokeWidth = wireWidth)
    }

    // Numbers around the board (gold)
    val numberPaint = Paint().apply {
        color = GoldInt
        textSize = r * 0.125f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    for (i in 0 until 20) {
        val a = Math.toRadians(Board.segmentAngle(i).toDouble())
        val x = cx + r * 1.10f * cos(a).toFloat()
        val y = cy + r * 1.10f * sin(a).toFloat() + numberPaint.textSize * 0.35f
        drawContext.canvas.nativeCanvas.drawText(Board.segments[i].toString(), x, y, numberPaint)
    }

    if (showValues) {
        // Highlight the double/treble segment under the finger
        if (focus != null) {
            val hit = Board.hitTest(focus.x, focus.y, g)
            val ring = when (hit.ring) {
                Ring.DOUBLE -> g.doubleIn to g.doubleOut
                Ring.TREBLE -> g.trebleIn to g.trebleOut
                else -> null
            }
            if (ring != null) {
                val idx = Board.segments.indexOf(hit.number)
                val startDeg = -99f + idx * 18f
                val path = Path()
                val outer = ring.second * r
                val inner = ring.first * r
                path.arcTo(
                    Rect(cx - outer, cy - outer, cx + outer, cy + outer),
                    startDeg, 18f, true
                )
                path.arcTo(
                    Rect(cx - inner, cy - inner, cx + inner, cy + inner),
                    startDeg + 18f, -18f, false
                )
                path.close()
                drawPath(path, Color(0xFFD4AF37), style = Stroke(width = r * 0.025f))
            }
        }

        // Value labels. Those close to the finger are scaled up smoothly (magnifier effect).
        val baseSize = r * 0.075f
        val valuePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(r * 0.015f, 0f, 0f, android.graphics.Color.BLACK)
        }
        data class Label(val text: String, val nx: Float, val ny: Float, val scale: Float)
        val labels = ArrayList<Label>(42)
        val doubleMid = (g.doubleIn + g.doubleOut) / 2f
        val trebleMid = (g.trebleIn + g.trebleOut) / 2f
        fun scaleAt(nx: Float, ny: Float): Float {
            if (focus == null) return 1f
            val dx = nx - focus.x
            val dy = ny - focus.y
            val d = sqrt(dx * dx + dy * dy)
            val t = (1f - d / 0.38f).coerceIn(0f, 1f)
            val smooth = t * t * (3f - 2f * t)
            return 1f + 2.2f * smooth
        }
        for (i in 0 until 20) {
            val a = Math.toRadians(Board.segmentAngle(i).toDouble())
            val n = Board.segments[i]
            val c = cos(a).toFloat()
            val sn = sin(a).toFloat()
            labels.add(Label((n * 2).toString(), doubleMid * c, doubleMid * sn, scaleAt(doubleMid * c, doubleMid * sn)))
            labels.add(Label((n * 3).toString(), trebleMid * c, trebleMid * sn, scaleAt(trebleMid * c, trebleMid * sn)))
        }
        labels.add(Label("50", 0f, 0f, scaleAt(0f, 0f) * 0.75f))
        labels.add(Label("25", 0f, -(g.bullR + g.outerBullR) / 2f - 0.02f, scaleAt(0f, -g.outerBullR) * 0.75f))
        labels.sortBy { it.scale } // biggest drawn last, on top
        for (l in labels) {
            valuePaint.textSize = baseSize * l.scale
            if (l.scale > 1.05f) valuePaint.color = GoldInt else valuePaint.color = android.graphics.Color.WHITE
            drawContext.canvas.nativeCanvas.drawText(
                l.text,
                cx + l.nx * r,
                cy + l.ny * r + valuePaint.textSize * 0.35f,
                valuePaint
            )
        }
    }

    // Landed darts (gold flights). The newest one lands: a thin ring collapses into the dot while the
    // dot scales up with a little overshoot and flashes red -> yellow -> gold.
    for ((i, p) in marks.withIndex()) {
        val pt = Offset(cx + p.x * r, cy + p.y * r)
        val newest = i == marks.lastIndex && landT < 1f
        if (!newest) {
            drawCircle(Color.Black, radius = r * 0.032f, center = pt)
            drawCircle(Color(0xFFD4AF37), radius = r * 0.022f, center = pt)
        } else {
            val t = landT
            // scale: 0.35 -> 1.25 (overshoot at t≈0.55) -> 1.0
            val scale = if (t < 0.55f) 0.35f + 0.9f * (t / 0.55f) else 1.25f - 0.25f * ((t - 0.55f) / 0.45f)
            // colour: red for the first third, yellow in the middle, settling to gold
            val col = when {
                t < 0.3f -> Color(0xFFE0203A)
                t < 0.6f -> Color(0xFFFFE23A)
                else -> {
                    val k = (t - 0.6f) / 0.4f
                    val y = Color(0xFFFFE23A); val gold = Color(0xFFD4AF37)
                    Color(y.red + (gold.red - y.red) * k, y.green + (gold.green - y.green) * k, y.blue + (gold.blue - y.blue) * k)
                }
            }
            // collapsing ring from 3x the dot down to the dot
            val ringR = r * 0.032f * (3.2f - 2.2f * t.coerceAtMost(1f))
            drawCircle(col.copy(alpha = (1f - t) * 0.9f), radius = ringR, center = pt, style = Stroke(width = r * 0.012f))
            drawCircle(Color.Black, radius = r * 0.032f * scale, center = pt)
            drawCircle(col, radius = r * 0.022f * scale, center = pt)
        }
    }
}

/** Normalised board coordinates for the centre of a bed (used to land a dart exactly where intended). */
internal fun boardPoint(hit: com.dartsapp.data.Hit, g: BoardGeometry): Offset {
    if (hit.ring == com.dartsapp.data.Ring.BULL) return Offset(0f, 0f)
    if (hit.ring == com.dartsapp.data.Ring.OUTER_BULL) return Offset(0f, -(g.bullR + g.outerBullR) / 2f)
    val idx = com.dartsapp.data.Board.segments.indexOf(hit.number)
    val a = Math.toRadians(com.dartsapp.data.Board.segmentAngle(idx).toDouble())
    val r = when (hit.ring) {
        com.dartsapp.data.Ring.TREBLE -> (g.trebleIn + g.trebleOut) / 2f
        com.dartsapp.data.Ring.DOUBLE -> (g.doubleIn + g.doubleOut) / 2f
        else -> (g.trebleOut + g.doubleIn) / 2f
    }
    return Offset(r * kotlin.math.cos(a).toFloat(), r * kotlin.math.sin(a).toFloat())
}
