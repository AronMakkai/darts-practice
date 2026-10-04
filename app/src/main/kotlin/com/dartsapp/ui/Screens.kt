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
            RetroTitle("DARTS")
            RetroTitle("PRACTICE", small = true)
            Spacer(Modifier.height(36.dp))
            RetroButton("CHECKOUT") { navController.navigate("checkout") }
            RetroButton("DARTLESS CHECKOUT") { navController.navigate("dartless") }
            RetroButton("VALUE CHECKER") { navController.navigate("valuechecker") }
            RetroButton("METRONOME") { navController.navigate("metronome") }
            RetroButton("SETTINGS") { navController.navigate("settings") }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RetroTitle(text: String, small: Boolean = false) {
    val size = if (small) 30.sp else 56.sp
    Box {
        // Red offset shadow, then the gold face: flat two-tone like a shaded polygon
        Text(
            text, fontSize = size, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            letterSpacing = if (small) 8.sp else 6.sp, color = Red,
            modifier = Modifier.offset(x = 3.dp, y = 4.dp)
        )
        Text(
            text, fontSize = size, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            letterSpacing = if (small) 8.sp else 6.sp, color = Gold
        )
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

        // Mountains: two layers of flat polygons
        fun mountains(baseY: Float, amp: Float, color: Color, seed: Int) {
            val path = Path()
            path.moveTo(0f, baseY)
            val n = 9
            for (i in 0..n) {
                val x = w * i / n
                val k = ((i * 7 + seed * 3) % 5) / 4f
                path.lineTo(x, baseY - amp * (0.3f + 0.7f * k))
            }
            path.lineTo(w, baseY)
            path.close()
            drawPath(path, color)
        }
        mountains(horizonY, h * 0.10f, Color(0xFF1C1C1C), 1)
        mountains(horizonY, h * 0.06f, Color(0xFF2B2B2B), 2)

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
