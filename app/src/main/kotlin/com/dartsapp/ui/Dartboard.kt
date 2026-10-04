package com.dartsapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val BoardBlack = Color(0xFF141414)
private val BoardCream = Color(0xFFEDE3C4)
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
 * @param onTap called with the tap position in normalised board coords
 */
@Composable
fun Dartboard(
    modifier: Modifier = Modifier,
    geometry: BoardGeometry = BoardGeometry.STANDARD,
    showValues: Boolean = false,
    marks: List<Offset> = emptyList(),
    onTap: ((Offset) -> Unit)? = null
) {
    var m = modifier.fillMaxWidth().aspectRatio(1f)
    if (onTap != null) {
        m = m.pointerInput(Unit) {
            detectTapGestures { pos ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(size.width, size.height) / 2f / RIM_SCALE
                onTap(Offset((pos.x - cx) / radius, (pos.y - cy) / radius))
            }
        }
    }
    Canvas(modifier = m) {
        drawBoard(geometry, showValues, marks)
    }
}

/** Board radius × this = full canvas radius (leaves room for the numbers ring). */
private const val RIM_SCALE = 1.2f

private fun DrawScope.drawBoard(g: BoardGeometry, showValues: Boolean, marks: List<Offset>) {
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
        val valuePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = r * 0.075f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(r * 0.015f, 0f, 0f, android.graphics.Color.BLACK)
        }
        val doubleMid = (g.doubleIn + g.doubleOut) / 2f
        val trebleMid = (g.trebleIn + g.trebleOut) / 2f
        for (i in 0 until 20) {
            val a = Math.toRadians(Board.segmentAngle(i).toDouble())
            val n = Board.segments[i]
            val dy = valuePaint.textSize * 0.35f
            drawContext.canvas.nativeCanvas.drawText(
                (n * 2).toString(),
                cx + r * doubleMid * cos(a).toFloat(),
                cy + r * doubleMid * sin(a).toFloat() + dy,
                valuePaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                (n * 3).toString(),
                cx + r * trebleMid * cos(a).toFloat(),
                cy + r * trebleMid * sin(a).toFloat() + dy,
                valuePaint
            )
        }
        val bullPaint = Paint(valuePaint).apply { textSize = r * 0.055f }
        drawContext.canvas.nativeCanvas.drawText("50", cx, cy + bullPaint.textSize * 0.35f, bullPaint)
        drawContext.canvas.nativeCanvas.drawText("25", cx, cy - r * 0.065f, bullPaint)
    }

    // Landed darts (gold flights)
    for (p in marks) {
        val pt = Offset(cx + p.x * r, cy + p.y * r)
        drawCircle(Color.Black, radius = r * 0.032f, center = pt)
        drawCircle(Color(0xFFD4AF37), radius = r * 0.022f, center = pt)
    }
}
