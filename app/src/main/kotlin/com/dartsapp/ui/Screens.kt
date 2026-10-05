package com.dartsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun MainMenuScreen(navController: NavHostController) {
    Box(modifier = Modifier.fillMaxSize()) {
        VectorBackdrop(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.9f))
            ChromeTitle("METRO", modifier = Modifier.fillMaxWidth().height(74.dp))
            ChromeTitle("DARTS", modifier = Modifier.fillMaxWidth().height(74.dp))
            Spacer(Modifier.height(30.dp))
            RetroButton("DARTS GAME") { navController.navigate("game") }
            RetroButton("DARTS IRL") { navController.navigate("irl") }
            RetroButton("SETTINGS") { navController.navigate("settings") }
            Spacer(Modifier.weight(1f))
        }
        TvFilter(modifier = Modifier.fillMaxSize())
    }
}

/** Sub-menu on the same backdrop: a chrome heading and a stack of retro buttons. */
@Composable
fun SubMenuScreen(navController: NavHostController, title: String, entries: List<Pair<String, String>>) {
    Box(modifier = Modifier.fillMaxSize()) {
        VectorBackdrop(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.9f))
            ChromeTitle(title, modifier = Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.height(26.dp))
            for ((label, route) in entries) {
                RetroButton(label) { navController.navigate(route) }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { navController.popBackStack() }) {
                Text("< BACK", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold)
            }
            Spacer(Modifier.weight(1f))
        }
        TvFilter(modifier = Modifier.fillMaxSize())
    }
}

/**
 * 80s chrome lettering: a hard "horizon" gradient — blue sky at the top fading to white, then a
 * brown ground band to white at the bottom — with a dark outline and a drop shadow.
 */
@Composable
private fun ChromeTitle(text: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            isFakeBoldText = true
            letterSpacing = 0.12f
            textSize = h * 0.92f
        }
        // Fit the width
        val measured = paint.measureText(text)
        if (measured > w * 0.94f) paint.textSize *= (w * 0.94f) / measured
        val cx = w / 2f
        val baseline = h * 0.5f + paint.textSize * 0.36f
        val top = baseline - paint.textSize * 0.74f
        val bottom = baseline + paint.textSize * 0.04f

        // Drop shadow
        paint.style = android.graphics.Paint.Style.FILL
        paint.shader = null
        paint.color = android.graphics.Color.argb(200, 0, 0, 0)
        drawContext.canvas.nativeCanvas.drawText(text, cx + h * 0.05f, baseline + h * 0.06f, paint)
        // Outline
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = h * 0.06f
        paint.strokeJoin = android.graphics.Paint.Join.ROUND
        paint.color = android.graphics.Color.rgb(0x10, 0x10, 0x18)
        drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, paint)
        // Chrome fill: blue (top) -> white -> hard horizon -> brown -> white (bottom)
        paint.style = android.graphics.Paint.Style.FILL
        paint.shader = android.graphics.LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(
                android.graphics.Color.rgb(0x1E, 0x5A, 0xE8),
                android.graphics.Color.rgb(0x9C, 0xD4, 0xFF),
                android.graphics.Color.rgb(0xFF, 0xFF, 0xFF),
                android.graphics.Color.rgb(0x6B, 0x3A, 0x12),
                android.graphics.Color.rgb(0xB8, 0x7A, 0x3A),
                android.graphics.Color.rgb(0xFF, 0xFF, 0xFF)
            ),
            floatArrayOf(0f, 0.3f, 0.49f, 0.51f, 0.72f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, paint)
        paint.shader = null
        // Thin top highlight
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = h * 0.012f
        paint.color = android.graphics.Color.argb(160, 255, 255, 255)
        drawContext.canvas.nativeCanvas.drawText(text, cx, baseline - h * 0.012f, paint)
    }
}

/**
 * CRT television look over the menu: scanlines, a soft vignette and curved-corner darkening, a slow
 * rolling band and a faint flicker. Purely visual — it does not intercept touches.
 */
@Composable
private fun TvFilter(modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Scanlines
        val step = 4f
        var y = 0f
        while (y < h) {
            drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Size(w, 1.5f))
            y += step
        }
        // Rolling band
        val bandY = ((t * 0.12f) % 1f) * (h + 200f) - 100f
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent),
                startY = bandY, endY = bandY + 160f
            ),
            Offset(0f, bandY), Size(w, 160f)
        )
        // Vignette + curved corners
        drawRect(
            androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f)),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.78f
            )
        )
        // Flicker
        val flicker = (sin(t * 37f) * 0.5f + sin(t * 11.3f)) * 0.012f
        if (flicker > 0f) drawRect(Color.White.copy(alpha = flicker))
        // Slight colour fringe at the edges
        drawRect(Color(0xFF3060FF).copy(alpha = 0.06f), Offset(0f, 0f), Size(3f, h))
        drawRect(Color(0xFFFF3030).copy(alpha = 0.06f), Offset(w - 3f, 0f), Size(3f, h))
    }
}

@Composable
private fun RetroButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
        border = BorderStroke(2.dp, Gold),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Black.copy(alpha = 0.55f), contentColor = PaleGold),
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(58.dp)
    ) {
        Text(label, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    }
}

/**
 * Flat-shaded vector backdrop in the spirit of late-80s polygon games: a perspective grid rolling
 * towards the viewer, a horizon glow, flat mountain polygons and a big low-poly dartboard "sun".
 */
@Composable
private fun VectorBackdrop(modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            t = (withFrameNanos { it } - start) / 1_000_000_000f
        }
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.42f
        val vp = Offset(w / 2f, horizonY)

        // Sky: flat bands (no gradients — polygons only)
        drawRect(Black)
        drawRect(Color(0xFF14060A), topLeft = Offset(0f, horizonY - h * 0.16f), size = Size(w, h * 0.16f))
        drawRect(Color(0xFF2A0A12), topLeft = Offset(0f, horizonY - h * 0.07f), size = Size(w, h * 0.07f))

        // Low-poly dartboard sun: 20 flat wedges
        val sunC = Offset(w * 0.72f, horizonY - h * 0.17f)
        val sunR = h * 0.13f
        for (i in 0 until 20) {
            val a0 = Math.toRadians((i * 18 - 99).toDouble())
            val a1 = Math.toRadians(((i + 1) * 18 - 99).toDouble())
            val path = Path().apply {
                moveTo(sunC.x, sunC.y)
                lineTo(sunC.x + sunR * cos(a0).toFloat(), sunC.y + sunR * sin(a0).toFloat())
                lineTo(sunC.x + sunR * cos(a1).toFloat(), sunC.y + sunR * sin(a1).toFloat())
                close()
            }
            drawPath(path, if (i % 2 == 0) Red else Color(0xFF5A0C1A))
        }
        drawCircle(Gold, sunR * 0.14f, sunC)
        drawCircle(Black, sunR, sunC, style = Stroke(width = sunR * 0.05f))

        // City skyline: two depths of flat-shaded tower blocks with lit windows (Rampage-style)
        fun skyline(baseY: Float, maxH: Float, color: Color, windowColor: Color, seed: Int, count: Int, parallax: Float) {
            var x = -w * 0.05f + ((t * parallax) % (w * 0.3f))
            var i = 0
            while (x < w * 1.1f) {
                val k = ((i * 13 + seed * 7) % 10) / 10f
                val bw = w * (0.05f + 0.06f * (((i * 5 + seed) % 4) / 4f))
                val bh = maxH * (0.35f + 0.65f * k)
                drawRect(color, Offset(x, baseY - bh), Size(bw, bh))
                // Side face, darker
                drawRect(color.copy(alpha = 1f).let { Color(it.red * 0.6f, it.green * 0.6f, it.blue * 0.6f) }, Offset(x + bw * 0.78f, baseY - bh), Size(bw * 0.22f, bh))
                // Antenna on some
                if ((i + seed) % 3 == 0) drawRect(color, Offset(x + bw * 0.4f, baseY - bh - maxH * 0.12f), Size(2f, maxH * 0.12f))
                // Windows: a grid, some lit
                val cols = (bw / (w * 0.016f)).toInt().coerceAtLeast(1)
                val rows = (bh / (h * 0.022f)).toInt()
                for (r in 0 until rows) for (c in 0 until cols) {
                    val lit = ((r * 7 + c * 3 + i * 5 + seed) % 5) < 2
                    if (lit) drawRect(windowColor, Offset(x + bw * 0.06f + c * (w * 0.016f), baseY - bh + h * 0.01f + r * (h * 0.022f)), Size(w * 0.008f, h * 0.012f))
                }
                x += bw + w * 0.012f
                i++
            }
        }
        skyline(horizonY + h * 0.002f, h * 0.22f, Color(0xFF161622), Color(0xFF5A4A28), 1, 14, 2f)
        skyline(horizonY + h * 0.002f, h * 0.14f, Color(0xFF26262F), Color(0xFFE8C14A), 2, 10, 6f)

        // Horizon line
        drawLine(Gold, Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 2f)

        // Ground: perspective grid rolling towards the viewer
        val gridColor = Color(0xFF7A1424)
        val lines = 14
        for (i in -lines..lines) {
            val x = w / 2f + i * (w * 1.6f / lines)
            drawLine(gridColor, vp, Offset(x, h), strokeWidth = 1.5f)
        }
        val speed = 0.35f
        val rows = 12
        for (r in 0 until rows) {
            val f = ((r + (t * speed) % 1f) / rows)   // 0 at horizon .. 1 at viewer
            val y = horizonY + (h - horizonY) * f * f  // perspective spacing
            val alpha = (0.25f + 0.75f * f).coerceIn(0f, 1f)
            drawLine(gridColor.copy(alpha = alpha), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f + 1.5f * f)
        }
    }
}

/** Shared top bar with a back button and title. */
@Composable
fun ScreenHeader(title: String, navController: NavHostController, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { navController.popBackStack() }) { Text("< Back", color = Gold) }
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/** Big "remaining" display used by both checkout modes. */
@Composable
fun RemainingDisplay(start: Int, remaining: Int, tip: String, message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Checkout $start", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(remaining.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Gold)
        if (tip.isNotEmpty()) Text(tip, fontSize = 18.sp, color = PaleGold, textAlign = TextAlign.Center)
        if (message.isNotEmpty()) Text(message, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
    }
}
