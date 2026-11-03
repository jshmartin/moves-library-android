package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every role is set so no Material default purple leaks into components.
// primaryContainer is solid Blaze in both modes: FABs are the brand's loudest accent.
private val DarkColorScheme = darkColorScheme(
    primary = Blaze,
    onPrimary = Ink,
    primaryContainer = Blaze,
    onPrimaryContainer = Ink,
    inversePrimary = BlazeDeep,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = GoldOnContainerDark,
    tertiary = Chalk,
    onTertiary = Ink,
    tertiaryContainer = InkHighest,
    onTertiaryContainer = Chalk,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = InkHigh,
    onSurfaceVariant = ChalkMuted,
    surfaceTint = Blaze,
    inverseSurface = Chalk,
    inverseOnSurface = Ink,
    outline = InkOutline,
    outlineVariant = InkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = InkHighest,
    surfaceDim = Ink,
    surfaceContainerLowest = InkLowest,
    surfaceContainerLow = InkLow,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = InkHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = BlazeDeep,
    onPrimary = Chalk,
    primaryContainer = Blaze,
    onPrimaryContainer = Ink,
    inversePrimary = BlazeLight,
    secondary = GoldDeep,
    onSecondary = Chalk,
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = GoldOnContainerLight,
    tertiary = Ink,
    onTertiary = Chalk,
    tertiaryContainer = ChalkHighest,
    onTertiaryContainer = Ink,
    background = Chalk,
    onBackground = Ink,
    surface = Chalk,
    onSurface = Ink,
    surfaceVariant = ChalkHigh,
    onSurfaceVariant = InkMuted,
    surfaceTint = BlazeDeep,
    inverseSurface = Ink,
    inverseOnSurface = Chalk,
    outline = ChalkOutline,
    outlineVariant = ChalkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = ChalkHighest,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ChalkLow,
    surfaceContainer = ChalkRaised,
    surfaceContainerHigh = ChalkHigh,
    surfaceContainerHighest = ChalkHighest,
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
