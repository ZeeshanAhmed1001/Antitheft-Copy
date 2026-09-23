package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AegisCyanPrimary,
    onPrimary = AegisNavyDark,
    primaryContainer = AegisNavyCard,
    onPrimaryContainer = AegisCyanSecondary,
    secondary = AegisSafetyAmber,
    onSecondary = AegisNavyDark,
    error = AegisAlertCrimson,
    background = AegisNavyDark,
    onBackground = AegisTextPrimary,
    surface = AegisNavySurface,
    onSurface = AegisTextPrimary,
    surfaceVariant = AegisNavyCard,
    onSurfaceVariant = AegisTextSecondary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

