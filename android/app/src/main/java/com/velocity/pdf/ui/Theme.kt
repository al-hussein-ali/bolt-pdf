package com.velocity.pdf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD32F2F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF3F51B5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E5FF),
    onSecondaryContainer = Color(0xFF001453),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF16181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFE7EBF2),
    onSurfaceVariant = Color(0xFF494E58),
    surfaceContainer = Color(0xFFF0F3F8),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFD0D6E0),
    outlineVariant = Color(0xFFE1E6EE),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6B6B),
    onPrimary = Color(0xFF4A0005),
    primaryContainer = Color(0xFF7D191B),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFF9DB2FF),
    onSecondary = Color(0xFF132258),
    secondaryContainer = Color(0xFF283A7A),
    onSecondaryContainer = Color(0xFFE0E5FF),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFECEEF3),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFECEEF3),
    surfaceVariant = Color(0xFF232732),
    onSurfaceVariant = Color(0xFFA9B0BE),
    surfaceContainer = Color(0xFF1C2029),
    surfaceContainerHigh = Color(0xFF222631),
    outline = Color(0xFF2F3544),
    outlineVariant = Color(0xFF2A303E),
    scrim = Color(0xFF000000),
)

@Composable
fun VelocityPdfTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
