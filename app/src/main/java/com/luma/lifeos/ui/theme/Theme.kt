package com.luma.lifeos.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView

private val LightColors = lightColorScheme(
    primary = Color(0xFF993612),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBA4D28),
    onPrimaryContainer = Color(0xFFFFF1ED),
    inversePrimary = Color(0xFFFFB5A0),
    secondary = Color(0xFF526350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5CD),
    onSecondaryContainer = Color(0xFF304D3C),
    tertiary = Color(0xFF705125),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF8B693B),
    onTertiaryContainer = Color(0xFFFFF1E5),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF1E1B18),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF1E1B18),
    surfaceVariant = Color(0xFFE8E1DC),
    onSurfaceVariant = Color(0xFF57423B),
    surfaceTint = Color(0xFF993612),
    inverseSurface = Color(0xFF33302D),
    inverseOnSurface = Color(0xFFF7EFEA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8B726A),
    outlineVariant = Color(0xFFDEC0B7),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFE0D8D3),
    surfaceBright = Color(0xFFFFF8F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF2ED),
    surfaceContainer = Color(0xFFF4ECE7),
    surfaceContainerHigh = Color(0xFFEEE7E2),
    surfaceContainerHighest = Color(0xFFE8E1DC),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB5A0),
    onPrimary = Color(0xFF5F1600),
    primaryContainer = Color(0xFFFF8C69),
    onPrimaryContainer = Color(0xFF5F1600),
    inversePrimary = Color(0xFF993612),
    secondary = Color(0xFFAECEB8),
    onSecondary = Color(0xFF193627),
    secondaryContainer = Color(0xFF304D3C),
    onSecondaryContainer = Color(0xFFC9EAD4),
    tertiary = Color(0xFFFFB59F),
    onTertiary = Color(0xFF5E1700),
    tertiaryContainer = Color(0xFF782102),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF131413),
    onBackground = Color(0xFFEDE0D8),
    surface = Color(0xFF131413),
    onSurface = Color(0xFFEDE0D8),
    surfaceVariant = Color(0xFF383938),
    onSurfaceVariant = Color(0xFFDCC0B9),
    surfaceTint = Color(0xFFFFB5A0),
    inverseSurface = Color(0xFFEDE0D8),
    inverseOnSurface = Color(0xFF30312F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA48B84),
    outlineVariant = Color(0xFF56423D),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF131413),
    surfaceBright = Color(0xFF383938),
    surfaceContainerLowest = Color(0xFF0D0E0D),
    surfaceContainerLow = Color(0xFF1B1C1B),
    surfaceContainer = Color(0xFF262726),
    surfaceContainerHigh = Color(0xFF2D2E2D),
    surfaceContainerHighest = Color(0xFF383938),
)

// Material 3 1.3.2 predates fixed roles. Keep these shared role extensions
// until the dependency exposes them, without changing the pinned Compose BOM.
private data class LumaFixedColorRoles(
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
)

private val LightFixedColors = LumaFixedColorRoles(
    primaryFixed = Color(0xFFFFDBD0),
    primaryFixedDim = Color(0xFFFFB5A0),
    onPrimaryFixed = Color(0xFF3A0B00),
    onPrimaryFixedVariant = Color(0xFF782102),
)

private val DarkFixedColors = LumaFixedColorRoles(
    primaryFixed = Color(0xFFFFDBD1),
    primaryFixedDim = Color(0xFFFFB5A0),
    onPrimaryFixed = Color(0xFF3A0A00),
    onPrimaryFixedVariant = Color(0xFF782102),
)

private val ColorScheme.fixedRoles: LumaFixedColorRoles
    get() = if (background.luminance() > 0.5f) LightFixedColors else DarkFixedColors

internal val ColorScheme.primaryFixed: Color get() = fixedRoles.primaryFixed
internal val ColorScheme.primaryFixedDim: Color get() = fixedRoles.primaryFixedDim
internal val ColorScheme.onPrimaryFixed: Color get() = fixedRoles.onPrimaryFixed
internal val ColorScheme.onPrimaryFixedVariant: Color get() = fixedRoles.onPrimaryFixedVariant

@Composable
fun LifeOsTheme(content: @Composable () -> Unit) {
    val darkTheme = when (rememberAppTheme()) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.activity()?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT) { darkTheme },
                // Match Activity's default scrims, resolving their contrast from the app choice.
                navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { darkTheme },
            )
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = lumaTypography(LocalConfiguration.current.locales[0].language),
        shapes = LumaShapes,
        content = content,
    )
}

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
