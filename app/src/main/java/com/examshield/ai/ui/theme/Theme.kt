package com.examshield.ai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SpaceColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NebulaPurple,
    tertiary = ThreatRed,
    background = CosmicBackground,
    surface = DarkMatterSurface,
    onPrimary = CosmicBackground,
    onSecondary = StarlightWhite,
    onTertiary = StarlightWhite,
    onBackground = StarlightWhite,
    onSurface = StarlightWhite,
    surfaceVariant = DarkMatterSurface,
    onSurfaceVariant = StarlightWhite
)

@Composable
fun ExamShieldAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Always use the space color scheme to enforce the sci-fi look regardless of phone system settings.
    val colorScheme = SpaceColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
