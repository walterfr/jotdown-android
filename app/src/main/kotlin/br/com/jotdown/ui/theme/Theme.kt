package br.com.jotdown.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Indigo50  = Color(0xFFEEF2FF)
val Indigo100 = Color(0xFFE0E7FF)
val Indigo200 = Color(0xFFC7D2FE)
val Indigo400 = Color(0xFF818CF8)
val Indigo500 = Color(0xFF6366F1)
val Indigo600 = Color(0xFF4F46E5)
val Indigo700 = Color(0xFF4338CA)
val Indigo900 = Color(0xFF1E1B4B)

val Amber400  = Color(0xFFFBBF24)
val Amber500  = Color(0xFFF59E0B)

val Slate800  = Color(0xFF1E293B)
val Slate900  = Color(0xFF0F172A)

private val LightColors = lightColorScheme(
    primary          = Indigo600,
    onPrimary        = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary        = Amber500,
    onSecondary      = Color.White,
    background       = Color(0xFFF8FAFC),
    onBackground     = Color(0xFF1E293B),
    surface          = Color.White,
    onSurface        = Color(0xFF1E293B),
    surfaceVariant   = Color(0xFFF1F5F9),
    outline          = Color(0xFFCBD5E1)
)

private val DarkColors = darkColorScheme(
    primary          = Indigo400,
    onPrimary        = Indigo900,
    primaryContainer = Indigo700,
    onPrimaryContainer = Indigo100,
    secondary        = Amber400,
    onSecondary      = Slate900,
    background       = Slate900,
    onBackground     = Color(0xFFE2E8F0),
    surface          = Slate800,
    onSurface        = Color(0xFFE2E8F0),
    surfaceVariant   = Color(0xFF1E293B),
    outline          = Color(0xFF334155)
)

@Composable
fun JotdownTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = Typography(),
        content     = content
    )
}
