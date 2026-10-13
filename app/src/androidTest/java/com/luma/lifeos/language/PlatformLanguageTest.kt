package com.luma.lifeos.language

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.MainActivity
import org.junit.Assert.*
import org.junit.Test

class PlatformLanguageTest {
    @Test fun explicitChoiceSurvivesClosingAndLaunchingNewActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previous = AppLanguages.current(context)
        try {
            for (language in AppLanguage.entries) {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { assertTrue(AppLanguages.select(it, language)) }
                }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        assertEquals(language, AppLanguages.current(it))
                        assertEquals(language.tag, it.resources.configuration.locales[0].language)
                    }
                }
            }
        } finally {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { AppLanguages.select(it, previous) }
            }
        }
    }

    @Test fun nativeLanguageSettingsAndReturningToDeviceDefaultAreAuthoritative() {
        if (Build.VERSION.SDK_INT < 33) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        fun set(locales: LocaleList) = instrumentation.runOnMainSync { manager.applicationLocales = locales }
        try {
            for (language in listOf(AppLanguage.PERSIAN, AppLanguage.FRENCH)) {
                set(LocaleList(language.locale))
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { assertEquals(language.tag, it.resources.configuration.locales[0].language) }
                }
            }
            set(LocaleList.getEmptyLocaleList())
            val device = manager.systemLocales
            val expected = AppLanguage.resolve(null, (0 until device.size()).map { device[it].toLanguageTag() })
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertEquals(expected.tag, it.resources.configuration.locales[0].language) }
            }
            assertTrue(manager.applicationLocales.isEmpty)
        } finally { set(original) }
    }
}
