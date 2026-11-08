package br.com.projeto40dias.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object P40Colors {
    val Background = Color(0xFF0E0F11)
    val Surface = Color(0xFF16181B)
    val Card = Color(0xFF1C1F23)
    val CardHigh = Color(0xFF24282D)
    val Lime = Color(0xFFC6FF00)
    val LimeDim = Color(0xFF7A9C00)
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9AA0A6)
    val Track = Color(0xFF2A2D31)
    val Warning = Color(0xFFFFB300)
    val Danger = Color(0xFFFF5252)

    /** Legenda obrigatória: medido × estimado × meta. */
    val Measured = Lime
    val Estimated = Color(0xFF4FC3F7)
    val Goal = Color(0xFFFFFFFF)

    val Protein = Color(0xFFFF7043)
    val Carbs = Color(0xFF4FC3F7)
    val Fat = Color(0xFFFFCA28)
    val Water = Color(0xFF29B6F6)
}

private val scheme = darkColorScheme(
    primary = P40Colors.Lime,
    onPrimary = Color.Black,
    secondary = P40Colors.Estimated,
    background = P40Colors.Background,
    onBackground = P40Colors.Text,
    surface = P40Colors.Surface,
    onSurface = P40Colors.Text,
    surfaceVariant = P40Colors.Card,
    onSurfaceVariant = P40Colors.TextSecondary,
    surfaceContainer = P40Colors.Surface,
    surfaceContainerHigh = P40Colors.CardHigh,
    surfaceContainerHighest = P40Colors.CardHigh,
    outline = P40Colors.Track,
    error = P40Colors.Danger,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun P40Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
