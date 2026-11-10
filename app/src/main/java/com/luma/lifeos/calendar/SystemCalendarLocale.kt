package com.luma.lifeos.calendar

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import java.util.Locale

/** Reads the primary device locale without consulting Luma's app-specific locale override. */
object SystemCalendarLocale {
    fun primary(context: Context): Locale? {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.systemLocales
                ?.let { locales -> if (locales.isEmpty) null else locales[0] }
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locales.let { locales ->
                if (locales.isEmpty) null else locales[0]
            }
        }
        return locale?.takeIf { it.language.isNotBlank() }
    }
}
