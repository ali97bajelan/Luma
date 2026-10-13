package com.luma.lifeos.language

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun explicitChoiceOverridesDeviceLanguage() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve("fr", listOf("fa-IR")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve("en", listOf("fr-FR")))
        assertEquals(AppLanguage.PERSIAN, AppLanguage.resolve("fa", listOf("en-US")))
    }
    @Test fun regionalDeviceLocalesResolveByLanguageInPriorityOrder() {
        assertEquals(AppLanguage.PERSIAN, AppLanguage.resolve(null, listOf("de-DE", "fa-IR", "fr")))
        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve(null, listOf("fr-CA", "en-US")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(null, listOf("en-GB", "fa")))
    }
    @Test fun unsupportedOrEmptyDeviceLocalesFallBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(null, listOf("de", "ar")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(null, emptyList()))
    }
    @Test fun invalidSavedChoiceFallsBackToSupportedDeviceLanguage() {
        assertEquals(AppLanguage.PERSIAN, AppLanguage.resolve("invalid", listOf("fa-IR")))
        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve("", listOf("fr-FR")))
    }
}
