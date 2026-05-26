package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CosmicDarkColorScheme = darkColorScheme(
    primary = CometPrimary,
    secondary = CometSecondary,
    tertiary = CometAccent,
    background = CosmicNightBg,
    surface = CosmicNightSurface,
    surfaceVariant = CosmicNightSurfaceVariant,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onTertiary = CosmicNightBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor
)

private val CosmicLightColorScheme = lightColorScheme(
    primary = CometPrimary,
    secondary = CometSecondary,
    tertiary = CometAccent,
    background = TextPrimary,
    surface = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = CosmicNightBg,
    onSurface = CosmicNightBg
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to preserve our beautifully bespoke theme colors
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CosmicDarkColorScheme
        else -> CosmicDarkColorScheme // Default to beautiful cozy dark theme for both, as alarm clock apps shine in night-dark style!
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
