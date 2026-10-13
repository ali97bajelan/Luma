package com.luma.lifeos.language

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Applies locale before activity creation so Compose and platform dialogs agree. */
object AppLanguages {
    private const val PREFERENCES = "app_language"
    private const val SELECTED = "selected_tag"

    fun current(context: Context): AppLanguage {
        val selected: String?
        val device: LocaleList
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            selected = manager.applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language
            device = manager.systemLocales
        } else {
            selected = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(SELECTED, null)
            device = Resources.getSystem().configuration.locales
        }
        return AppLanguage.resolve(selected, (0 until device.size()).map { device[it].toLanguageTag() })
    }

    fun wrap(context: Context): Context {
        val locale = current(context).locale
        // java.time and java.text formatters throughout the app use this process default.
        // This changes only Luma's process, never the device language or stored values.
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    /** Return false when persistence fails; keep the current screen and report a retry. */
    fun select(activity: Activity, language: AppLanguage): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                val manager = activity.getSystemService(LocaleManager::class.java)
                val locales = LocaleList(language.locale)
                if (manager.applicationLocales != locales) {
                    manager.applicationLocales = locales // Platform recreates the activity with saved state.
                }
            } else {
                if (!activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                        .putString(SELECTED, language.tag).commit()) return false
                if (activity.resources.configuration.locales[0].language != language.tag) activity.recreate()
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
