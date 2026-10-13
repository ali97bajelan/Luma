package com.luma.lifeos.language

import java.util.Locale

/** Stored tags are stable identifiers, never translated display labels. */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"), PERSIAN("fa"), FRENCH("fr");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun resolve(selected: String?, deviceLanguages: List<String>): AppLanguage =
            entries.firstOrNull { it.tag == selected }
                ?: deviceLanguages.firstNotNullOfOrNull { deviceTag ->
                    entries.firstOrNull { it.tag == Locale.forLanguageTag(deviceTag).language }
                } ?: ENGLISH
    }
}
