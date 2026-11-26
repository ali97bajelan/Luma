package com.luma.lifeos.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal enum class AppTheme(val preferenceValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark"),
}

internal const val THEME_KEY = "selected_theme"

internal fun readAppTheme(preferences: SharedPreferences): AppTheme =
    AppTheme.entries.firstOrNull {
        it.preferenceValue == preferences.getString(THEME_KEY, null)
    } ?: AppTheme.SYSTEM

internal fun AppTheme.resolveDarkTheme(systemIsDark: Boolean): Boolean = when (this) {
    AppTheme.SYSTEM -> systemIsDark
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}

@Composable
internal fun rememberThemePreferences(): SharedPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { context.getSharedPreferences("app_theme", Context.MODE_PRIVATE) }
}

/** Observe the same durable choice in every themed window and in the settings panel. */
@Composable
internal fun rememberAppTheme(preferences: SharedPreferences = rememberThemePreferences()): AppTheme {
    fun read() = readAppTheme(preferences)

    var theme by remember(preferences) { mutableStateOf(read()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == THEME_KEY || key == null) theme = read()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        theme = read()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return theme
}
