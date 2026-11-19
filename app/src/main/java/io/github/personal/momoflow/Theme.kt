package io.github.personal.momoflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Derived from the reference app's visible design tokens, independently implemented in Compose.
val Ink = Color(0xFF1C1C1E)
val MutedInk = Color(0xFF636366)
val QuietBlue = Color(0xFF007AFF)
val CanvasColor = Color(0xFFFFFFFF)
val SoftBlue = Color(0xFFF0F7FF)
val Amber = Color(0xFFFF9500)
val PaperStroke = Color(0xFFE8EAF0)
val CardSubtle = Color(0xFFF6F7FA)
val CorrectGreen = Color(0xFF15803D)
val WrongRed = Color(0xFFB91C1C)

private val Palette = lightColorScheme(
    primary = QuietBlue,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Ink,
    background = CanvasColor,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CardSubtle,
    onSurfaceVariant = MutedInk,
    outline = PaperStroke,
    primaryContainer = SoftBlue,
    onPrimaryContainer = Ink
)

@Composable
fun ShiguangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, content = content)
}
