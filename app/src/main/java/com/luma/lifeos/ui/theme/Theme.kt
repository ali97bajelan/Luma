package com.luma.lifeos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF245E51),
    secondary = Color(0xFF5E6258),
    background = Color(0xFFF8F8F2),
    surface = Color(0xFFF8F8F2),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FD2BF),
    secondary = Color(0xFFC5C9BB),
    background = Color(0xFF111512),
    surface = Color(0xFF111512),
)

@Composable
fun LifeOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
