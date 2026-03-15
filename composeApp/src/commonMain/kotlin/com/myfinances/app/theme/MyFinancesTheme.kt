package com.myfinances.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = FinanceGreen,
    onPrimary = White,
    primaryContainer = SoftMint,
    onPrimaryContainer = DeepInk,
    secondary = SlateBlue,
    onSecondary = White,
    background = Canvas,
    onBackground = DeepInk,
    surface = White,
    onSurface = DeepInk,
    surfaceVariant = Mist,
    onSurfaceVariant = MutedInk,
)

@Composable
fun MyFinancesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        content = content,
    )
}

