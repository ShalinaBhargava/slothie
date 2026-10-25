package app.slothie.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Warm, dim, bedtime colours. The whole app is dark on purpose: it is meant to be glanced at. */
object Palette {
    val bg = Color(0xFF1B1A17)
    val surface = Color(0xFF26241F)
    val surfaceHigh = Color(0xFF312E27)
    val moss = Color(0xFF8FB996)
    val mossDim = Color(0xFF4F6B55)
    val bark = Color(0xFF6B4F3A)
    val fur = Color(0xFFB49A7C)
    val furLight = Color(0xFFEEDFC8)
    val furDark = Color(0xFF4A3426)
    val ochre = Color(0xFFF5B726)
    val ember = Color(0xFFE97B77)
    val sky = Color(0xFF8BB2DE)
    val text = Color(0xFFF2EFE6)
    val textDim = Color(0xFFB8B2A4)
}

private val scheme = darkColorScheme(
    primary = Palette.moss,
    onPrimary = Palette.bg,
    primaryContainer = Palette.mossDim,
    onPrimaryContainer = Palette.text,
    secondary = Palette.ochre,
    onSecondary = Palette.bg,
    tertiary = Palette.sky,
    error = Palette.ember,
    background = Palette.bg,
    onBackground = Palette.text,
    surface = Palette.surface,
    onSurface = Palette.text,
    surfaceVariant = Palette.surfaceHigh,
    onSurfaceVariant = Palette.textDim,
    surfaceContainer = Palette.surface,
    surfaceContainerHigh = Palette.surfaceHigh,
    outline = Color(0xFF4A463D),
)

@Composable
fun SlothieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
