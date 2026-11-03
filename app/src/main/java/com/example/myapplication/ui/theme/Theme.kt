package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Blaze,
    onPrimary = Ink,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = Chalk,
    onTertiary = Ink,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = InkHigh,
    onSurfaceVariant = ChalkMuted,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
)

private val LightColorScheme = lightColorScheme(
    primary = BlazeDeep,
    onPrimary = Chalk,
    secondary = GoldDeep,
    onSecondary = Chalk,
    tertiary = Ink,
    onTertiary = Chalk,
    background = Chalk,
    onBackground = Ink,
    surface = Chalk,
    onSurface = Ink,
    surfaceVariant = ChalkHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainer = ChalkRaised,
    surfaceContainerHigh = ChalkHigh,
)

// Dynamic (wallpaper) color is intentionally not used so the Cypher palette always shows.
@Composable
fun CypherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
