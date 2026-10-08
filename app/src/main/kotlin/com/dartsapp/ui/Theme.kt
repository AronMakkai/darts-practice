package com.dartsapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val Night = Color(0xFF1A0A28)       // game background: the deep purple of the menu's night ground
val NearBlack = Color(0xFF231033)
val Charcoal = Color(0xFF301A44)
val Red = Color(0xFFC8102E)
val DarkRed = Color(0xFF7A0A1C)
val Gold = Color(0xFFD4AF37)
val PaleGold = Color(0xFFF1DC9A)
val BrightGold = Color(0xFFFFE27A)
val OffWhite = Color(0xFFF2F2F2)
val Grey = Color(0xFFB0B0B0)
val NeonPink = Color(0xFFFF4FA3)
val NeonTeal = Color(0xFF2EF2FF)

private val DartsColors = darkColorScheme(
    primary = Red,
    onPrimary = OffWhite,
    primaryContainer = DarkRed,
    onPrimaryContainer = OffWhite,
    secondary = Gold,
    onSecondary = Black,
    secondaryContainer = Charcoal,
    onSecondaryContainer = Gold,
    tertiary = Gold,
    onTertiary = Black,
    background = Night,
    onBackground = OffWhite,
    surface = NearBlack,
    onSurface = OffWhite,
    surfaceVariant = Charcoal,
    onSurfaceVariant = Grey,
    outline = Gold,
    error = Red,
    onError = OffWhite
)

@Composable
fun DartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DartsColors, content = content)
}
