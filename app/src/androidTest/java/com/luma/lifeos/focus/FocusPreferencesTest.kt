package com.luma.lifeos.focus

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusPreferencesTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun selectionsStartEmptyAndPersistAsDefensivePackageSnapshots() {
        val preferences = newPreferences()
        val mutableSelection = mutableSetOf("org.example.reader")

        assertTrue(preferences.selectedPackages().isEmpty())
        assertTrue(preferences.setAllowedPackages(mutableSelection))
        mutableSelection += "org.example.calendar"
        val returnedSelection = preferences.selectedPackages().toMutableSet().apply { clear() }

        assertTrue(returnedSelection.isEmpty())
        assertEquals(setOf("org.example.reader"), preferences.selectedPackages())
    }

    @Test fun packageChangesFromAnotherPreferencesInstanceReachExistingObservers() = runBlocking {
        val storageName = "focus_preferences_test_${UUID.randomUUID()}"
        val firstWindow = FocusPreferences(context, storageName)
        val secondWindow = FocusPreferences(context, storageName)
        val collector = async {
            withTimeout(5_000) {
                firstWindow.allowedPackages.first { "org.example.calendar" in it }
            }
        }

        assertTrue(secondWindow.setPackageAllowed("org.example.reader", true))
        assertTrue(secondWindow.setPackageAllowed("org.example.calendar", true))

        assertEquals(
            setOf("org.example.reader", "org.example.calendar"),
            collector.await(),
        )
    }

    @Test fun blankPackagesAreRejectedAndRemovingAStaleSelectionIsDurable() {
        val preferences = newPreferences()
        assertFalse(preferences.setPackageAllowed("  ", true))
        assertTrue(preferences.setAllowedPackages(setOf("org.example.removed")))

        assertTrue(preferences.setPackageAllowed("org.example.removed", false))
        assertTrue(preferences.selectedPackages().isEmpty())
    }

    private fun newPreferences() = FocusPreferences(context, "focus_preferences_test_${UUID.randomUUID()}")
}
