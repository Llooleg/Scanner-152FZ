package com.example.safecopy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Neutral99,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Slate40,
    onSecondary = Neutral99,
    secondaryContainer = Slate90,
    onSecondaryContainer = Teal10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    surfaceContainer = Neutral95,
    surfaceContainerLow = Neutral95,
    error = Red40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Slate80,
    secondaryContainer = Slate40,
    background = Neutral6,
    onBackground = Neutral95,
    surface = Neutral6,
    onSurface = Neutral95,
    surfaceVariant = Neutral20,
    surfaceContainer = Neutral20,
    surfaceContainerLow = Neutral20,
    error = Red80,
)

// Динамические цвета (Material You) отключены: фирменный цвет приложения задан макетами ТЗ.
@Composable
fun SafecopyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
