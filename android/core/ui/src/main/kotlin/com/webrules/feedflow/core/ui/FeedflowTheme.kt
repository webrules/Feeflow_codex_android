package com.webrules.feedflow.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FeedflowLightBackground = Color(0xFFF2F2F7)
val FeedflowDarkBackground = Color(0xFF0B101B)
val FeedflowLightCard = Color.White
val FeedflowDarkCard = Color(0xFF151C2C)
val FeedflowAccentBlue = Color(0xFF2D62ED)
val FeedflowDarkTextPrimary = Color.White
val FeedflowLightTextPrimary = Color.Black
val FeedflowDarkTextSecondary = Color(0xFF949BA5)
val FeedflowLightTextSecondary = Color(0xFF6D6D72)
val FeedflowDarkInputBackground = Color(0xFF1C2436)
val FeedflowLightInputBackground = Color(0xFFE5E5EA)
val FeedflowDarkSeparator = Color(0xFF273247)
val FeedflowLightSeparator = Color(0xFFD7D7DD)

private val LightColors = lightColorScheme(
    primary = FeedflowAccentBlue,
    background = FeedflowLightBackground,
    surface = FeedflowLightCard,
    surfaceVariant = FeedflowLightInputBackground,
    onSurface = FeedflowLightTextPrimary,
    onSurfaceVariant = FeedflowLightTextSecondary,
    outline = FeedflowLightSeparator,
)

private val DarkColors = darkColorScheme(
    primary = FeedflowAccentBlue,
    background = FeedflowDarkBackground,
    surface = FeedflowDarkCard,
    surfaceVariant = FeedflowDarkInputBackground,
    onSurface = FeedflowDarkTextPrimary,
    onSurfaceVariant = FeedflowDarkTextSecondary,
    outline = FeedflowDarkSeparator,
)

@Composable
fun FeedflowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

fun feedflowColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) DarkColors else LightColors
