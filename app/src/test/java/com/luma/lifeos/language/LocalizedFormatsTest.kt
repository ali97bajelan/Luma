package com.luma.lifeos.language

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class LocalizedFormatsTest {
    private fun inLocale(tag: String, check: () -> Unit) {
        val previous = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag(tag)); check() }
        finally { Locale.setDefault(previous) }
    }

    @Test fun persianTimeUsesPersianDigitsWithoutChangingStoredDate() = inLocale("fa") {
        assertEquals("۰۹:۰۵", LocalTime.of(9, 5).format(localizedTime(use24Hour = true)))
        val date = LocalDate.of(2026, 10, 13)
        val display = date.format(localizedDate(FormatStyle.MEDIUM))
        assertTrue(display, display.contains("۲۰۲۶"))
        assertEquals("2026-10-13", date.toString())
    }

    @Test fun frenchDateAndEnglishTimeFollowSelectedLocale() {
        inLocale("fr") { assertEquals("13 oct. 2026", LocalDate.of(2026, 10, 13).format(localizedDate(FormatStyle.MEDIUM))) }
        inLocale("en") { assertEquals("09:05", LocalTime.of(9, 5).format(localizedTime(use24Hour = true))) }
    }
}
