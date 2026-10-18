package com.luma.lifeos.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView

private val LightColors = lightColorScheme(
    primary = Color(0xFF245E51),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE8DC),
    onPrimaryContainer = Color(0xFF0A2F27),
    inversePrimary = Color(0xFF91D1BC),
    secondary = Color(0xFF5E6258),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E4D7),
    onSecondaryContainer = Color(0xFF1A1E17),
    tertiary = Color(0xFF775A35),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5DEBB),
    onTertiaryContainer = Color(0xFF291A06),
    background = Color(0xFFF8F8F2),
    onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFF8F8F2),
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFE5E7DE),
    onSurfaceVariant = Color(0xFF44483F),
    surfaceTint = Color(0xFF245E51),
    inverseSurface = Color(0xFF2F312C),
    inverseOnSurface = Color(0xFFF0F1E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74796F),
    outlineVariant = Color(0xFFC4C8BE),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFD9DBD2),
    surfaceBright = Color(0xFFF8F8F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3EA),
    surfaceContainer = Color(0xFFECEDE5),
    surfaceContainerHigh = Color(0xFFE6E8DF),
    surfaceContainerHighest = Color(0xFFE0E2DA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FD2BF),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0D5144),
    onPrimaryContainer = Color(0xFFBCEEDB),
    inversePrimary = Color(0xFF356B5D),
    secondary = Color(0xFFC5C9BB),
    onSecondary = Color(0xFF2F332D),
    secondaryContainer = Color(0xFF454A42),
    onSecondaryContainer = Color(0xFFE0E4D7),
    tertiary = Color(0xFFE3C094),
    onTertiary = Color(0xFF422D0B),
    tertiaryContainer = Color(0xFF5B421F),
    onTertiaryContainer = Color(0xFFF8DFB9),
    background = Color(0xFF111512),
    onBackground = Color(0xFFE1E3DC),
    surface = Color(0xFF111512),
    onSurface = Color(0xFFE1E3DC),
    surfaceVariant = Color(0xFF424840),
    onSurfaceVariant = Color(0xFFC4C8BE),
    surfaceTint = Color(0xFF9FD2BF),
    inverseSurface = Color(0xFFE1E3DC),
    inverseOnSurface = Color(0xFF2F312C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9389),
    outlineVariant = Color(0xFF424840),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF111512),
    surfaceBright = Color(0xFF373A34),
    surfaceContainerLowest = Color(0xFF0C100D),
    surfaceContainerLow = Color(0xFF191D19),
    surfaceContainer = Color(0xFF1D211D),
    surfaceContainerHigh = Color(0xFF272B27),
    surfaceContainerHighest = Color(0xFF323632),
)

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
        content = content,
    )
}

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
