package com.dartsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
            Spacer(Modifier.weight(0.5f))
            ChromeTitle("METRO", modifier = Modifier.fillMaxWidth().height(74.dp))
            ChromeTitle("DARTS", modifier = Modifier.fillMaxWidth().height(74.dp))
            // Title up in the sky, the signs a little lower: the gap scales with the phone's height
            Spacer(Modifier.weight(0.6f))
            RetroButton("DARTS GAME") { navController.navigate("game") }
            RetroButton("DARTS IRL") { navController.navigate("irl") }
            RetroButton("SETTINGS") { navController.navigate("settings") }
            Spacer(Modifier.weight(0.65f))
        }
        TvFilter(modifier = Modifier.fillMaxSize())
    }
}

/** Sub-menu on the same backdrop: a chrome heading and a stack of retro buttons. */
@Composable
fun SubMenuScreen(navController: NavHostController, title: String, entries: List<Pair<String, String>>, showPace: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val presets = remember { com.dartsapp.logic.TimingPresets.load(context) }
    var paceName by remember { mutableStateOf(com.dartsapp.logic.TimingPresets.selectedOrDefault(context, presets)) }
    var paceOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        VectorBackdrop(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.5f))
            ChromeTitle(title, modifier = Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.weight(0.7f))
            for ((label, route) in entries) {
                RetroButton(label) { navController.navigate(route) }
            }
            if (showPace) {
                // Which pace every game is played at: a pro's rhythm or one you recorded in the Metronome
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(
                        onClick = { paceOpen = true },
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Black.copy(alpha = 0.6f), contentColor = PaleGold),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("PACE: ", fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, fontSize = 12.sp, color = Grey)
                        Text(paceName.uppercase(), fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, fontSize = 12.sp)
                        Text("  ▾", fontSize = 12.sp, color = Gold)
                    }
                    DropdownMenu(expanded = paceOpen, onDismissRequest = { paceOpen = false }) {
                        for (p in presets) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(p.name, color = if (p.name == paceName) Gold else OffWhite)
                                        Text(p.summary(), fontSize = 11.sp, color = Grey)
                                    }
                                },
                                onClick = { paceName = p.name; com.dartsapp.logic.TimingPresets.setSelected(context, p.name); paceOpen = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Record my own pace…", color = PaleGold) },
                            onClick = { paceOpen = false; navController.navigate("metronome") }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { navController.popBackStack() }) {
                Text("< BACK", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold)
            }
            Spacer(Modifier.weight(0.45f))
        }
        TvFilter(modifier = Modifier.fillMaxSize())
    }
}

/**
 * 80s chrome lettering reflecting the sunset: purple-pink sky at the top fading to a hot yellow-white,
 * a hard horizon, then magenta and orange below — with a deep purple outline, a drop shadow and a
 * Miami neon halo (teal outside, hot pink inside).
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

        // Neon glow: two blurred passes, a wide soft magenta-pink halo and a tighter hot one
        paint.style = android.graphics.Paint.Style.FILL
        paint.shader = null
        paint.maskFilter = android.graphics.BlurMaskFilter(h * 0.36f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        paint.color = android.graphics.Color.argb(190, 0x00, 0xE5, 0xFF)
        drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, paint)
        paint.maskFilter = android.graphics.BlurMaskFilter(h * 0.14f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        paint.color = android.graphics.Color.argb(230, 0xFF, 0x2E, 0x97)
        drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, paint)
        paint.maskFilter = null
        // Drop shadow
        paint.color = android.graphics.Color.argb(190, 0x2A, 0x06, 0x3A)
        drawContext.canvas.nativeCanvas.drawText(text, cx + h * 0.05f, baseline + h * 0.06f, paint)
        // Outline
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = h * 0.06f
        paint.strokeJoin = android.graphics.Paint.Join.ROUND
        paint.color = android.graphics.Color.rgb(0x2A, 0x0A, 0x40)
        drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, paint)
        // Chrome fill reflecting the sunset: violet (top) -> pink -> yellow-white -> hard horizon -> magenta -> orange -> cream
        paint.style = android.graphics.Paint.Style.FILL
        paint.shader = android.graphics.LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(
                android.graphics.Color.rgb(0x6A, 0x2C, 0xD8),
                android.graphics.Color.rgb(0xFF, 0x6F, 0xB5),
                android.graphics.Color.rgb(0xFF, 0xF4, 0xC8),
                android.graphics.Color.rgb(0x9C, 0x12, 0x6A),
                android.graphics.Color.rgb(0xFF, 0x7A, 0x3D),
                android.graphics.Color.rgb(0xFF, 0xF1, 0xD6)
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
            drawRect(Color.Black.copy(alpha = 0.26f), Offset(0f, y), Size(w, 2f))
            y += step
        }
        // Faint RGB phosphor stripes
        var x = 0f
        while (x < w) {
            drawRect(Color(0xFFFF4040).copy(alpha = 0.035f), Offset(x, 0f), Size(1f, h))
            drawRect(Color(0xFF40FF40).copy(alpha = 0.035f), Offset(x + 1f, 0f), Size(1f, h))
            drawRect(Color(0xFF4060FF).copy(alpha = 0.035f), Offset(x + 2f, 0f), Size(1f, h))
            x += 3f
        }
        // Rolling band
        val bandY = ((t * 0.12f) % 1f) * (h + 200f) - 100f
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                startY = bandY, endY = bandY + 220f
            ),
            Offset(0f, bandY), Size(w, 220f)
        )
        // Vignette + curved corners
        drawRect(
            androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.85f)),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.72f
            )
        )
        // Flicker
        val flicker = (sin(t * 37f) * 0.5f + sin(t * 11.3f)) * 0.035f
        if (flicker > 0f) drawRect(Color.White.copy(alpha = flicker))
        // Slight colour fringe at the edges
        drawRect(Color(0xFF3060FF).copy(alpha = 0.2f), Offset(0f, 0f), Size(6f, h))
        drawRect(Color(0xFFFF3030).copy(alpha = 0.2f), Offset(w - 6f, 0f), Size(6f, h))
    }
}

@Composable
private fun RetroButton(label: String, onClick: () -> Unit) = NeonSignButton(label, onClick)

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

        // Sunset sky: deep violet overhead through magenta and hot pink to orange and gold at the horizon
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color(0xFF1C0B45), 0.35f to Color(0xFF5A1A7A), 0.62f to Color(0xFFC21E77),
                0.8f to Color(0xFFFF4F6E), 0.92f to Color(0xFFFF8C42), 1f to Color(0xFFFFCE70),
                startY = 0f, endY = horizonY
            ),
            topLeft = Offset.Zero, size = Size(w, horizonY)
        )
        // A few thin streaks of cloud lit from below
        for (k in 0 until 4) {
            val cy = horizonY * (0.45f + k * 0.11f)
            val cw = w * (0.35f + 0.12f * ((k * 3) % 4))
            val cx0 = ((k * 0.37f + t * 0.004f * (k + 1)) % 1f) * (w + cw) - cw
            drawRoundRect(Color(0xFFFFB3C7).copy(alpha = 0.22f), Offset(cx0, cy), Size(cw, h * 0.006f), androidx.compose.ui.geometry.CornerRadius(h * 0.003f))
        }

        // Low-poly dartboard sun sinking towards the horizon, in sunset colours, with a warm glow
        val sunC = Offset(w * 0.72f, horizonY - h * 0.24f)
        val sunR = h * 0.13f
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFFFE08A).copy(alpha = 0.75f), Color(0xFFFF6F91).copy(alpha = 0.25f), Color.Transparent), center = sunC, radius = sunR * 2.4f),
            radius = sunR * 2.4f, center = sunC
        )
        for (i in 0 until 20) {
            val a0 = Math.toRadians((i * 18 - 99).toDouble())
            val a1 = Math.toRadians(((i + 1) * 18 - 99).toDouble())
            val path = Path().apply {
                moveTo(sunC.x, sunC.y)
                lineTo(sunC.x + sunR * cos(a0).toFloat(), sunC.y + sunR * sin(a0).toFloat())
                lineTo(sunC.x + sunR * cos(a1).toFloat(), sunC.y + sunR * sin(a1).toFloat())
                close()
            }
            drawPath(path, if (i % 2 == 0) Color(0xFFFFB13B) else Color(0xFFFF4F8B))
        }
        drawCircle(Color(0xFFFFF1A8), sunR * 0.14f, sunC)
        // Synthwave slices through the lower half of the sun
        for (k in 0 until 5) {
            val y = sunC.y + sunR * (0.15f + k * 0.18f)
            drawRect(Color(0xFFFF6A6A), Offset(sunC.x - sunR, y), Size(sunR * 2f, sunR * (0.03f + k * 0.022f)))
        }

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
        skyline(horizonY + h * 0.002f, h * 0.22f, Color(0xFF3A1650), Color(0xFFFF8FD0), 1, 14, 2f)
        skyline(horizonY + h * 0.002f, h * 0.14f, Color(0xFF261038), Color(0xFF6FF3FF), 2, 10, 6f)

        // Palm trees framing the scene, in silhouette against the sunset: a ringed, curving trunk lit
        // pink on the sun side, a crown of drooping feathery fronds that sway, and a few coconuts
        fun palm(baseX: Float, baseY: Float, height: Float, lean: Float, dark: Color, rim: Color, phase: Float) {
            val top = Offset(baseX + lean * height, baseY - height)
            val ctrl = Offset(baseX + lean * height * 0.15f, baseY - height * 0.55f)
            fun trunkAt(u: Float): Offset {
                val v = 1f - u
                return Offset(v * v * baseX + 2 * v * u * ctrl.x + u * u * top.x, v * v * baseY + 2 * v * u * ctrl.y + u * u * top.y)
            }
            // Trunk: stacked segments, each a little narrower, with a darker ring at every joint
            val segs = 14
            for (k in 0 until segs) {
                val u0 = k / segs.toFloat(); val u1 = (k + 1) / segs.toFloat()
                val a = trunkAt(u0); val b = trunkAt(u1)
                val w0 = height * (0.042f - 0.022f * u0); val w1 = height * (0.042f - 0.022f * u1)
                val seg = Path().apply {
                    moveTo(a.x - w0, a.y); lineTo(b.x - w1 * 1.12f, b.y); lineTo(b.x + w1 * 1.12f, b.y); lineTo(a.x + w0, a.y); close()
                }
                drawPath(seg, dark)
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(b.x - w1 * 1.1f, b.y), Offset(b.x + w1 * 1.1f, b.y), strokeWidth = height * 0.006f)
                // rim light down the side facing the sun
                val side = if (lean < 0f) -1f else 1f
                drawLine(rim, Offset(a.x - side * w0 * 0.8f, a.y), Offset(b.x - side * w1 * 0.8f, b.y), strokeWidth = height * 0.006f)
            }
            // Fronds: a curved spine with leaflets down both sides, longest near the crown
            val fronds = 9
            for (f in 0 until fronds) {
                val sway = sin(t * 0.9f + f * 0.7f + phase) * 3f
                val ang = Math.toRadians((-200 + f * (220.0 / (fronds - 1)) + sway).toDouble())
                val len = height * (0.40f + 0.10f * ((f * 7) % 3) / 2f)
                val droop = len * (0.55f + 0.35f * kotlin.math.abs(cos(ang)).toFloat())
                val tip = Offset(top.x + len * cos(ang).toFloat(), top.y + len * sin(ang).toFloat() + droop)
                val mid = Offset(top.x + len * 0.6f * cos(ang).toFloat(), top.y + len * 0.6f * sin(ang).toFloat() - len * 0.18f)
                fun spineAt(u: Float): Offset {
                    val v = 1f - u
                    return Offset(v * v * top.x + 2 * v * u * mid.x + u * u * tip.x, v * v * top.y + 2 * v * u * mid.y + u * u * tip.y)
                }
                val leaflets = 16
                var prev = spineAt(0f)
                for (k in 1..leaflets) {
                    val u = k / leaflets.toFloat()
                    val pt = spineAt(u)
                    drawLine(dark, prev, pt, strokeWidth = height * (0.012f * (1f - u) + 0.003f), cap = StrokeCap.Round)
                    val dx = pt.x - prev.x; val dy = pt.y - prev.y
                    val dl = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                    val nx = -dy / dl; val ny = dx / dl
                    val leaf = len * 0.24f * (1f - u * 0.7f)
                    for (sgn in intArrayOf(-1, 1)) {
                        val end = Offset(pt.x + (nx * sgn * 0.7f + dx / dl * 0.45f) * leaf, pt.y + (ny * sgn * 0.7f + dy / dl * 0.45f) * leaf + leaf * 0.55f)
                        drawPath(Path().apply { moveTo(prev.x, prev.y); lineTo(end.x, end.y); lineTo(pt.x, pt.y); close() }, dark)
                    }
                    prev = pt
                }
            }
            // Coconuts tucked under the crown
            for (c in 0 until 3) drawCircle(dark, height * 0.028f, Offset(top.x + (c - 1) * height * 0.035f, top.y + height * 0.035f + (c % 2) * height * 0.015f))
            drawCircle(rim, height * 0.008f, Offset(top.x - height * 0.03f, top.y + height * 0.03f))
        }
        val palmDark = Color(0xFF1A0726)
        val palmRim = Color(0xFFFF6FA8).copy(alpha = 0.55f)
        // a smaller, hazier one further back, then the two framing the scene
        palm(w * 0.70f, horizonY + h * 0.005f, h * 0.17f, -0.12f, Color(0xFF3A1650), Color(0xFFFF8FB8).copy(alpha = 0.35f), 2.1f)
        palm(w * 0.06f, horizonY + h * 0.03f, h * 0.3f, 0.18f, palmDark, palmRim, 0f)
        palm(w * 0.93f, horizonY + h * 0.03f, h * 0.26f, -0.22f, palmDark, palmRim, 1.3f)

        // Horizon line: a hot neon edge
        drawLine(Color(0xFFFF4FA3).copy(alpha = 0.5f), Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 8f)
        drawLine(Color(0xFFFFD3EC), Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 2f)

        // Ground: deep purple with a neon grid rolling towards the viewer
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3B0F52), Color(0xFF14051F)), startY = horizonY, endY = h),
            topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY)
        )
        val gridColor = Color(0xFFFF2E97)
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
