package com.threadmesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColorScheme = lightColorScheme(
    primary = Slate900,
    onPrimary = Slate50,
    secondary = Indigo600,
    onSecondary = Slate50,
    tertiary = Sky600,
    background = Slate50,
    surface = Slate50,
    surfaceVariant = Slate100,
    outline = Slate200
)

@Composable
fun ThreadMeshTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
