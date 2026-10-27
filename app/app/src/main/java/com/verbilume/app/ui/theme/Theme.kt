package com.verbilume.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class VerbiColors(
    val isDark: Boolean,
    val paper: Color,
    val raised: Color,
    val sunken: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val accent: Color,
    val accentDeep: Color,
    val brass: Color,
    val brassBright: Color,
    val teal: Color,
    val line: Color,
    val glow: Color,
    val heroScrim: Color,
)

val LocalVerbiColors = staticCompositionLocalOf {
    VerbiColors(
        isDark = false,
        paper = Paper,
        raised = PaperRaised,
        sunken = PaperSunken,
        ink = Ink,
        inkSoft = InkSoft,
        inkFaint = InkFaint,
        accent = Terracotta,
        accentDeep = TerracottaDeep,
        brass = Brass,
        brassBright = BrassBright,
        teal = Teal,
        line = LineLight,
        glow = BrassBright,
        heroScrim = Ink,
    )
}

private val LightScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = PaperRaised,
    primaryContainer = Blush,
    onPrimaryContainer = TerracottaDeep,
    secondary = Teal,
    onSecondary = PaperRaised,
    secondaryContainer = Color(0xFFD3E5DF),
    onSecondaryContainer = Color(0xFF133B37),
    tertiary = Brass,
    onTertiary = PaperRaised,
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkSoft,
    outline = Color(0xFF9A9182),
    outlineVariant = LineLight,
    error = Color(0xFF9E3B2F),
    onError = PaperRaised,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
)

private val DarkScheme = darkColorScheme(
    primary = TerracottaNight,
    onPrimary = Night,
    primaryContainer = Color(0xFF5A2A1F),
    onPrimaryContainer = Color(0xFFFFD9CC),
    secondary = TealNight,
    onSecondary = Night,
    secondaryContainer = Color(0xFF1B403B),
    onSecondaryContainer = Color(0xFFC7F1E9),
    tertiary = BrassNight,
    onTertiary = Night,
    background = Night,
    onBackground = Starlight,
    surface = NightRaised,
    onSurface = Starlight,
    surfaceVariant = Color(0xFF172936),
    onSurfaceVariant = StarlightSoft,
    outline = Color(0xFF68757B),
    outlineVariant = LineDark,
    error = Color(0xFFF2A08A),
    onError = Night,
    inverseSurface = Starlight,
    inverseOnSurface = Night,
)

@Composable
fun VerbilumeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        VerbiColors(
            isDark = true,
            paper = Night,
            raised = NightRaised,
            sunken = NightSunken,
            ink = Starlight,
            inkSoft = StarlightSoft,
            inkFaint = StarlightFaint,
            accent = TerracottaNight,
            accentDeep = Color(0xFFF0A184),
            brass = BrassNight,
            brassBright = BrassNight,
            teal = TealNight,
            line = LineDark,
            glow = BrassNight,
            heroScrim = Night,
        )
    } else {
        VerbiColors(
            isDark = false,
            paper = Paper,
            raised = PaperRaised,
            sunken = PaperSunken,
            ink = Ink,
            inkSoft = InkSoft,
            inkFaint = InkFaint,
            accent = Terracotta,
            accentDeep = TerracottaDeep,
            brass = Brass,
            brassBright = BrassBright,
            teal = Teal,
            line = LineLight,
            glow = BrassBright,
            heroScrim = Ink,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalVerbiColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = VerbiTypography,
            content = content,
        )
    }
}
