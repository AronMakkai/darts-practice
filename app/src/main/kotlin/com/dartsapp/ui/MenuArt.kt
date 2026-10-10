package com.dartsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The menu's 80s poster girl, flat vector: standing in profile facing left, head turned to camera,
 * huge wavy hair down her back, black high-cut one-piece with a neon trim, near hand on her hip
 * (elbow out) and three darts in the other hand. Fits a box about 1:2.
 */
@Composable
fun MenuGirl(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { drawMenuGirl() }
}

private fun DrawScope.drawMenuGirl() {
    val w = size.width; val h = size.height
    /** Closed smooth shape through the midpoints of the control points (x, y pairs, 0..1). */
    fun smooth(c: Color, vararg pts: Float) {
        val n = pts.size / 2
        fun px(i: Int) = w * pts[(i % n) * 2]
        fun py(i: Int) = h * pts[(i % n) * 2 + 1]
        val path = Path().apply {
            moveTo((px(0) + px(1)) / 2f, (py(0) + py(1)) / 2f)
            for (i in 1..n) quadraticBezierTo(px(i), py(i), (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f)
            close()
        }
        drawPath(path, c)
    }
    fun oval(c: Color, x: Float, y: Float, ow: Float, oh: Float) = drawOval(c, Offset(w * x, h * y), Size(w * ow, h * oh))
    fun limb(c: Color, x0: Float, y0: Float, x1: Float, y1: Float, thick: Float) =
        drawLine(c, Offset(w * x0, h * y0), Offset(w * x1, h * y1), strokeWidth = h * thick, cap = StrokeCap.Round)

    smooth(Color(0xFF5C3622), 0.380f, 0.030f, 0.500f, -0.010f, 0.640f, 0.010f, 0.760f, 0.060f, 0.840f, 0.120f, 0.800f, 0.180f, 0.880f, 0.250f, 0.820f, 0.310f, 0.880f, 0.380f, 0.800f, 0.420f, 0.850f, 0.500f, 0.740f, 0.500f, 0.700f, 0.420f, 0.640f, 0.330f, 0.600f, 0.240f, 0.520f, 0.210f, 0.400f, 0.220f, 0.330f, 0.250f, 0.300f, 0.180f, 0.260f, 0.120f, 0.310f, 0.070f)  // hairBack
    smooth(Color(0xFFC8805C), 0.470f, 0.560f, 0.600f, 0.580f, 0.585f, 0.680f, 0.560f, 0.780f, 0.565f, 0.880f, 0.575f, 0.965f, 0.590f, 0.985f, 0.530f, 0.985f, 0.525f, 0.900f, 0.515f, 0.800f, 0.500f, 0.700f)  // legFar
    smooth(Color(0xFFECAA80), 0.440f, 0.200f, 0.560f, 0.210f, 0.585f, 0.280f, 0.565f, 0.360f, 0.560f, 0.420f, 0.605f, 0.500f, 0.615f, 0.570f, 0.570f, 0.620f, 0.480f, 0.620f, 0.400f, 0.580f, 0.400f, 0.500f, 0.410f, 0.420f, 0.380f, 0.340f, 0.360f, 0.270f, 0.400f, 0.220f)  // torso
    smooth(Color(0xFFECAA80), 0.400f, 0.550f, 0.500f, 0.580f, 0.560f, 0.610f, 0.530f, 0.700f, 0.490f, 0.790f, 0.470f, 0.880f, 0.445f, 0.965f, 0.430f, 0.990f, 0.370f, 0.990f, 0.400f, 0.965f, 0.415f, 0.880f, 0.425f, 0.790f, 0.410f, 0.700f, 0.390f, 0.630f)  // legNear
    smooth(Color(0xFF160C1C), 0.430f, 0.210f, 0.470f, 0.210f, 0.500f, 0.300f, 0.530f, 0.380f, 0.555f, 0.430f, 0.600f, 0.500f, 0.585f, 0.555f, 0.500f, 0.600f, 0.430f, 0.600f, 0.400f, 0.550f, 0.405f, 0.470f, 0.410f, 0.410f, 0.375f, 0.340f, 0.365f, 0.270f, 0.390f, 0.220f)  // suit
    limb(Color(0xFFFF4FA3), 0.385f, 0.300f, 0.405f, 0.400f, 0.005f)
    limb(Color(0xFFFF4FA3), 0.405f, 0.400f, 0.410f, 0.500f, 0.005f)
    smooth(Color(0xFFECAA80), 0.465f, 0.175f, 0.535f, 0.175f, 0.540f, 0.210f, 0.500f, 0.228f, 0.460f, 0.215f)  // neck
    oval(Color(0xFFECAA80), 0.405f, 0.070f, 0.170f, 0.125f)
    smooth(Color(0xFFC8805C), 0.530f, 0.080f, 0.575f, 0.110f, 0.570f, 0.165f, 0.525f, 0.193f)  // faceShade
    smooth(Color(0xFF5C3622), 0.390f, 0.165f, 0.390f, 0.075f, 0.460f, 0.035f, 0.560f, 0.040f, 0.620f, 0.095f, 0.615f, 0.165f, 0.585f, 0.105f, 0.540f, 0.085f, 0.490f, 0.100f, 0.440f, 0.090f, 0.415f, 0.115f)  // fringe
    limb(Color(0xFF965C34), 0.660f, 0.080f, 0.740f, 0.200f, 0.007f)
    limb(Color(0xFF965C34), 0.740f, 0.240f, 0.800f, 0.360f, 0.007f)
    limb(Color(0xFF965C34), 0.640f, 0.220f, 0.700f, 0.340f, 0.007f)
    limb(Color(0xFF965C34), 0.330f, 0.100f, 0.310f, 0.190f, 0.007f)
    limb(Color(0xFF965C34), 0.700f, 0.400f, 0.760f, 0.480f, 0.007f)
    limb(Color(0xFF965C34), 0.560f, 0.030f, 0.640f, 0.070f, 0.007f)
    oval(Color(0xFF28181E), 0.448f, 0.123f, 0.028f, 0.011f)
    oval(Color(0xFF28181E), 0.512f, 0.123f, 0.028f, 0.011f)
    limb(Color(0xFF5C3622), 0.448f, 0.113f, 0.480f, 0.110f, 0.004f)
    limb(Color(0xFF5C3622), 0.510f, 0.110f, 0.542f, 0.113f, 0.004f)
    oval(Color(0xFFC83C5A), 0.474f, 0.165f, 0.036f, 0.013f)
    oval(Color(0xFFECAA80), 0.485f, 0.205f, 0.070f, 0.050f)
    limb(Color(0xFFECAA80), 0.520f, 0.235f, 0.660f, 0.355f, 0.030f)
    limb(Color(0xFFECAA80), 0.660f, 0.355f, 0.575f, 0.470f, 0.026f)
    oval(Color(0xFFECAA80), 0.550f, 0.450f, 0.050f, 0.035f)
    limb(Color(0xFFC8805C), 0.415f, 0.240f, 0.370f, 0.350f, 0.024f)
    limb(Color(0xFFC8805C), 0.370f, 0.350f, 0.330f, 0.450f, 0.021f)
    // Three darts in the front hand, pointing forward: steel barrels, teal flights
    val hx = 0.325f; val hy = 0.46f
    for (deg in intArrayOf(150, 165, 180)) {
        val a = Math.toRadians(deg.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val end = Offset(w * (hx + 0.10f * ca), h * (hy + 0.05f * sa))
        drawLine(Color(0xFFCDCDD7), Offset(w * hx, h * hy), end, strokeWidth = h * 0.005f, cap = StrokeCap.Round)
        val pa = a + Math.PI / 2
        val tip = Offset(end.x + w * 0.03f * ca, end.y + h * 0.015f * sa)
        val side = Offset(w * 0.012f * cos(pa).toFloat(), h * 0.006f * sin(pa).toFloat())
        drawPath(Path().apply { moveTo(end.x, end.y); lineTo(tip.x + side.x, tip.y + side.y); lineTo(tip.x - side.x, tip.y - side.y); close() }, NeonTeal)
    }
    oval(Color(0xFFC8805C), 0.31f, 0.445f, 0.035f, 0.028f)
}

/**
 * Menu entry as an 80s neon sign: a red glass tube frame with a yellow inner tube, little arrow
 * ornaments at each end, and the label glowing in neon. Flickers now and then like a real sign;
 * lights up fully while pressed.
 */
@Composable
fun NeonSignButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1_000_000_000f
    }
    val seed = remember(label) { (label.hashCode() and 0xFFFF) / 6553.5f }
    Box(
        modifier = modifier.fillMaxWidth(0.8f).padding(vertical = 5.dp).height(46.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(46.dp)) {
            // A short flicker every few seconds, at a different moment for each sign
            val cycle = (t + seed) % 4.3f
            val flick = if (cycle < 0.08f || (cycle > 0.16f && cycle < 0.21f)) 0.45f else 1f
            val glow = if (pressed) 1.25f else flick
            val w = size.width; val h = size.height
            val canvas = drawContext.canvas.nativeCanvas
            val paint = android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeJoin = android.graphics.Paint.Join.ROUND }
            val r = h * 0.32f
            // Dark backing plate so the sign reads over the sky
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.argb(150, 0x1E, 0x08, 0x2C)
            canvas.drawRoundRect(android.graphics.RectF(4f, 4f, w - 4f, h - 4f), r, r, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            fun tube(inset: Float, color: Int, width: Float) {
                val rect = android.graphics.RectF(inset, inset, w - inset, h - inset)
                val rr = (r - inset * 0.5f).coerceAtLeast(2f)
                paint.strokeWidth = width * 3.2f
                paint.color = color; paint.alpha = (110 * glow).toInt().coerceIn(0, 255)
                paint.maskFilter = android.graphics.BlurMaskFilter(width * 2.6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(rect, rr, rr, paint)
                paint.maskFilter = null
                paint.strokeWidth = width
                paint.color = color; paint.alpha = (255 * glow.coerceAtMost(1f)).toInt()
                canvas.drawRoundRect(rect, rr, rr, paint)
                // the bright core of the glass
                paint.strokeWidth = width * 0.35f
                paint.color = android.graphics.Color.WHITE; paint.alpha = (200 * glow.coerceAtMost(1f)).toInt()
                canvas.drawRoundRect(rect, rr, rr, paint)
            }
            val red = android.graphics.Color.rgb(0xFF, 0x2A, 0x3D)
            val yellow = android.graphics.Color.rgb(0xFF, 0xD9, 0x66)   // a touch towards the board's cream
            tube(5f, red, 4.5f)
            tube(13f, yellow, 2f)
            // Arrow ornaments at each end, pointing at the label
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = yellow; paint.alpha = (255 * glow.coerceAtMost(1f)).toInt()
            for (side in intArrayOf(-1, 1)) {
                val cx = if (side < 0) h * 0.62f else w - h * 0.62f
                val cy = h / 2f
                val s = h * 0.12f
                val path = android.graphics.Path().apply {
                    moveTo(cx - side * s, cy - s); lineTo(cx + side * s, cy); lineTo(cx - side * s, cy + s); close()
                }
                canvas.drawPath(path, paint)
            }
            // The label in neon: glow pass then a pale core
            val text = android.graphics.Paint().apply {
                isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                textSize = 15.sp.toPx(); letterSpacing = 0.12f
            }
            val base = h / 2f + text.textSize * 0.35f
            text.color = red; text.alpha = (230 * glow.coerceAtMost(1f)).toInt()
            text.maskFilter = android.graphics.BlurMaskFilter(text.textSize * 0.35f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            canvas.drawText(label, w / 2f, base, text)
            text.maskFilter = null
            text.color = android.graphics.Color.rgb(0xFF, 0xF0, 0xC8); text.alpha = (255 * glow.coerceAtMost(1f)).toInt()
            canvas.drawText(label, w / 2f, base, text)
        }
    }
}


/**
 * A neon-tube frame drawn behind the content: a soft glow plus a bright glass line with a white
 * core, on a translucent purple plate. [lit] = false gives a dim, switched-off tube.
 */
fun Modifier.neonFrame(color: Color, lit: Boolean = true, corner: androidx.compose.ui.unit.Dp = 10.dp): Modifier = this.drawBehind {
    val canvas = drawContext.canvas.nativeCanvas
    val r = corner.toPx()
    val inset = 3.dp.toPx()
    val rect = android.graphics.RectF(inset, inset, size.width - inset, size.height - inset)
    val p = android.graphics.Paint().apply { isAntiAlias = true }
    p.style = android.graphics.Paint.Style.FILL
    p.color = android.graphics.Color.argb(170, 0x1E, 0x08, 0x2C)
    canvas.drawRoundRect(rect, r, r, p)
    p.style = android.graphics.Paint.Style.STROKE
    val c = android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    if (lit) {
        p.strokeWidth = 3.dp.toPx() * 2.4f; p.color = c; p.alpha = 110
        p.maskFilter = android.graphics.BlurMaskFilter(3.dp.toPx() * 2f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(rect, r, r, p)
        p.maskFilter = null
    }
    p.strokeWidth = 2.dp.toPx(); p.color = c; p.alpha = if (lit) 255 else 90
    canvas.drawRoundRect(rect, r, r, p)
    if (lit) {
        p.strokeWidth = 0.7.dp.toPx(); p.color = android.graphics.Color.WHITE; p.alpha = 190
        canvas.drawRoundRect(rect, r, r, p)
    }
}
