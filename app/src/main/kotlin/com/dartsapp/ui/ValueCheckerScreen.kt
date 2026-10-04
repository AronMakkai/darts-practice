package com.dartsapp.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.data.Board
import com.dartsapp.data.BoardGeometry
import com.dartsapp.data.Hit
import com.dartsapp.data.Ring
import kotlin.math.min

private val ValueGeo = BoardGeometry.WIDE

/** A value that floats up from the finger and exits at the top of the screen. */
private class Floater(val text: String, val startX: Float, val startY: Float, val bornMs: Long)

/**
 * Value checker: the board with every double and treble value written on it. Touch or slide a
 * finger: values near it magnify, and the value under the finger floats up out of the way so you
 * can see what you are touching.
 */
@Composable
fun ValueCheckerScreen(navController: NavHostController) {
    var selected by remember { mutableStateOf<Hit?>(null) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    var boardPos by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val floaters = remember { mutableStateListOf<Floater>() }
    var nowMs by remember { mutableStateOf(0L) }

    // Frame clock for the floating numbers
    LaunchedEffect(floaters.size) {
        while (floaters.isNotEmpty()) {
            withFrameNanos { }
            nowMs = System.currentTimeMillis()
            floaters.removeAll { nowMs - it.bornMs > FLOAT_MS }
        }
    }

    fun spawn(hit: Hit, p: Offset) {
        if (hit.ring == Ring.MISS) return
        val label = hit.score.toString()
        val last = floaters.lastOrNull()
        if (last != null && last.text == label && nowMs - last.bornMs < 250) return
        val cx = boardSize.width / 2f
        val cy = boardSize.height / 2f
        val r = min(boardSize.width, boardSize.height) / 2f / RIM_SCALE
        floaters.add(Floater(label, boardPos.x + cx + p.x * r, boardPos.y + cy + p.y * r, System.currentTimeMillis()))
        nowMs = System.currentTimeMillis()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScreenHeader("Value Checker", navController)

            val s = selected
            Text(
                when {
                    s == null -> "Touch or slide over the board"
                    s.ring == Ring.MISS -> "Miss"
                    else -> "${s.label}  =  ${s.score}"
                },
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Dartboard(
                modifier = Modifier.padding(8.dp).onGloballyPositioned {
                    boardPos = it.positionInParent()
                    boardSize = it.size
                },
                geometry = ValueGeo,
                showValues = true,
                focus = focus,
                onPointer = { p ->
                    focus = p
                    if (p != null) {
                        val hit = Board.hitTest(p.x, p.y, ValueGeo)
                        if (hit != selected) spawn(hit, p)
                        selected = hit
                    }
                }
            )

            Text(
                "Inner numbers = trebles, outer numbers = doubles. Slide your finger to magnify.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Floating numbers overlay — rise from the finger and exit at the top of the screen
        if (floaters.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val paint = Paint().apply {
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                    setShadowLayer(12f, 0f, 0f, android.graphics.Color.BLACK)
                }
                for (f in floaters) {
                    val t = ((nowMs - f.bornMs) / FLOAT_MS.toFloat()).coerceIn(0f, 1f)
                    val eased = 1f - (1f - t) * (1f - t)             // ease-out: fast away from the finger
                    val y = f.startY - eased * (f.startY + 80f)       // ends above the top edge
                    val scale = 1f + 0.6f * minOf(1f, t * 3f)         // pops up to size quickly
                    val alpha = if (t < 0.8f) 1f else (1f - (t - 0.8f) / 0.2f)
                    paint.textSize = size.height * 0.07f * scale
                    paint.color = android.graphics.Color.argb((255 * alpha).toInt().coerceIn(0, 255), 0xD4, 0xAF, 0x37)
                    val liftOffFinger = minOf(1f, t * 4f) * size.height * 0.08f
                    drawContext.canvas.nativeCanvas.drawText(f.text, f.startX, y - liftOffFinger + paint.textSize * 0.35f, paint)
                }
            }
        }
    }
}

private const val FLOAT_MS = 1400L
