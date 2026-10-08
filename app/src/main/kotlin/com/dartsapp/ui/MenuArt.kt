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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The menu's 80s poster girl: flat vector, big wavy hair, black high-cut swimsuit with a neon pink
 * trim, one hand on her hip and three darts held up in the other. Fits a box about 1:2.
 */
@Composable
fun MenuGirl(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { drawMenuGirl() }
}

private fun DrawScope.drawMenuGirl() {
    val w = size.width; val h = size.height
    fun p(x: Float, y: Float) = Offset(w * x, h * y)
    fun poly(c: Color, vararg pts: Float) {
        val path = Path().apply {
            moveTo(w * pts[0], h * pts[1])
            var i = 2
            while (i < pts.size) { lineTo(w * pts[i], h * pts[i + 1]); i += 2 }
            close()
        }
        drawPath(path, c)
    }
    fun oval(c: Color, x: Float, y: Float, ow: Float, oh: Float) = drawOval(c, p(x, y), Size(w * ow, h * oh))
    fun limb(c: Color, x0: Float, y0: Float, x1: Float, y1: Float, thick: Float) =
        drawLine(c, p(x0, y0), p(x1, y1), strokeWidth = h * thick, cap = StrokeCap.Round)

    val hair = Color(0xFF5A3420); val hairLight = Color(0xFF8C5630)
    val skin = Color(0xFFE8A27C); val skinShade = Color(0xFFC67C5A)
    val suit = Color(0xFF180C1E); val trim = Color(0xFFFF4FA3)

    // Hair, behind everything: big 80s volume down past the shoulders
    oval(hair, 0.36f, 0.035f, 0.28f, 0.14f)
    oval(hair, 0.29f, 0.10f, 0.18f, 0.14f); oval(hair, 0.53f, 0.10f, 0.18f, 0.14f)
    oval(hair, 0.27f, 0.19f, 0.17f, 0.15f); oval(hair, 0.56f, 0.19f, 0.17f, 0.15f)
    oval(hair, 0.30f, 0.28f, 0.14f, 0.12f); oval(hair, 0.57f, 0.28f, 0.14f, 0.13f)
    limb(hairLight, 0.33f, 0.14f, 0.30f, 0.24f, 0.008f); limb(hairLight, 0.67f, 0.15f, 0.70f, 0.25f, 0.008f)
    limb(hairLight, 0.32f, 0.27f, 0.35f, 0.36f, 0.008f); limb(hairLight, 0.66f, 0.28f, 0.63f, 0.38f, 0.008f)

    // Neck and body
    poly(skin, 0.47f, 0.16f, 0.53f, 0.16f, 0.535f, 0.225f, 0.465f, 0.225f)
    poly(skin, 0.37f, 0.225f, 0.63f, 0.225f, 0.61f, 0.30f, 0.575f, 0.40f, 0.625f, 0.50f, 0.375f, 0.50f, 0.425f, 0.40f, 0.39f, 0.30f)
    // Legs: the far one in shade
    poly(skin, 0.375f, 0.49f, 0.50f, 0.575f, 0.49f, 0.68f, 0.475f, 0.78f, 0.47f, 0.80f, 0.47f, 0.93f, 0.465f, 0.985f, 0.415f, 0.985f, 0.43f, 0.93f, 0.415f, 0.80f, 0.39f, 0.70f, 0.37f, 0.58f)
    poly(skinShade, 0.50f, 0.575f, 0.625f, 0.49f, 0.63f, 0.58f, 0.61f, 0.70f, 0.585f, 0.80f, 0.585f, 0.93f, 0.61f, 0.985f, 0.545f, 0.985f, 0.54f, 0.93f, 0.53f, 0.80f, 0.52f, 0.68f)
    // Swimsuit: high-cut one-piece with a V neck
    poly(suit, 0.415f, 0.225f, 0.445f, 0.225f, 0.50f, 0.31f, 0.555f, 0.225f, 0.585f, 0.225f, 0.61f, 0.30f, 0.575f, 0.40f, 0.625f, 0.49f, 0.53f, 0.575f, 0.47f, 0.575f, 0.375f, 0.49f, 0.425f, 0.40f, 0.39f, 0.30f)
    limb(trim, 0.42f, 0.29f, 0.445f, 0.40f, 0.005f); limb(trim, 0.445f, 0.40f, 0.41f, 0.48f, 0.005f)

    // Head
    oval(skin, 0.42f, 0.065f, 0.16f, 0.115f)
    poly(skinShade, 0.52f, 0.07f, 0.58f, 0.10f, 0.575f, 0.15f, 0.53f, 0.18f)
    poly(hair, 0.40f, 0.12f, 0.43f, 0.05f, 0.50f, 0.03f, 0.58f, 0.05f, 0.61f, 0.12f, 0.57f, 0.085f, 0.53f, 0.10f, 0.50f, 0.075f, 0.46f, 0.10f, 0.43f, 0.085f)
    val eye = Color(0xFF28181E)
    oval(eye, 0.455f, 0.112f, 0.026f, 0.009f); oval(eye, 0.519f, 0.112f, 0.026f, 0.009f)
    limb(hair, 0.452f, 0.103f, 0.482f, 0.100f, 0.004f); limb(hair, 0.518f, 0.100f, 0.548f, 0.103f, 0.004f)
    oval(Color(0xFFD6286E), 0.482f, 0.148f, 0.036f, 0.012f)

    // Shoulders, the near arm on her hip, the far arm raised with the darts
    oval(skin, 0.355f, 0.215f, 0.06f, 0.05f); oval(skinShade, 0.585f, 0.215f, 0.06f, 0.05f)
    limb(skin, 0.385f, 0.245f, 0.315f, 0.36f, 0.032f); limb(skin, 0.315f, 0.36f, 0.40f, 0.455f, 0.028f)
    limb(skinShade, 0.615f, 0.245f, 0.77f, 0.25f, 0.032f); limb(skinShade, 0.77f, 0.25f, 0.74f, 0.15f, 0.028f)
    // Three darts fanned out of the hand: steel barrels, teal flights
    val hand = p(0.74f, 0.14f)
    for (deg in intArrayOf(-115, -90, -65)) {
        val a = Math.toRadians(deg.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val end = Offset(hand.x + h * 0.045f * ca, hand.y + h * 0.09f * sa)
        drawLine(Color(0xFFC8C8D2), hand, end, strokeWidth = h * 0.006f, cap = StrokeCap.Round)
        val pa = a + Math.PI / 2
        val tip = Offset(end.x + h * 0.012f * ca, end.y + h * 0.025f * sa)
        val side = Offset((h * 0.01f * cos(pa)).toFloat(), (h * 0.01f * sin(pa)).toFloat())
        drawPath(Path().apply { moveTo(end.x, end.y); lineTo(tip.x + side.x, tip.y + side.y); lineTo(tip.x - side.x, tip.y - side.y); close() }, NeonTeal)
    }
    oval(skinShade, 0.72f, 0.13f, 0.04f, 0.035f)
}

/**
 * Menu entry as an 80s neon sign: a pink glass tube frame with a teal inner tube, little arrow
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
            val pink = android.graphics.Color.rgb(0xFF, 0x4F, 0xA3)
            val teal = android.graphics.Color.rgb(0x2E, 0xF2, 0xFF)
            tube(5f, pink, 4.5f)
            tube(13f, teal, 2f)
            // Arrow ornaments at each end, pointing at the label
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = teal; paint.alpha = (255 * glow.coerceAtMost(1f)).toInt()
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
                textSize = 17.sp.toPx(); letterSpacing = 0.18f
            }
            val base = h / 2f + text.textSize * 0.35f
            text.color = pink; text.alpha = (230 * glow.coerceAtMost(1f)).toInt()
            text.maskFilter = android.graphics.BlurMaskFilter(text.textSize * 0.35f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            canvas.drawText(label, w / 2f, base, text)
            text.maskFilter = null
            text.color = android.graphics.Color.rgb(0xFF, 0xE6, 0xF3); text.alpha = (255 * glow.coerceAtMost(1f)).toInt()
            canvas.drawText(label, w / 2f, base, text)
        }
    }
}
