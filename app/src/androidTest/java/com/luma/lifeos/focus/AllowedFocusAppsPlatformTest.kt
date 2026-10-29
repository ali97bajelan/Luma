package com.luma.lifeos.focus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllowedFocusAppsPlatformTest {
    @Test fun launcherDiscoveryOmitsLumaDeduplicatesPackagesAndSortsLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apps = AllowedFocusApps(context).discover()
        val labels = apps.map { it.label }

        assertFalse(apps.any { it.packageName == context.packageName })
        assertEquals(apps.size, apps.map { it.packageName }.toSet().size)
        assertTrue(labels.zipWithNext().all { (left, right) ->
            String.CASE_INSENSITIVE_ORDER.compare(left, right) <= 0
        })
    }

    @Test fun selectedPackageWithoutALauncherIsUnavailableWithoutLaunchingAnything() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = "com.example.missing.${UUID.randomUUID().toString().replace("-", "")}"

        assertEquals(
            AllowedFocusApps.LaunchResult.UNAVAILABLE,
            AllowedFocusApps(context).launch(packageName, setOf(packageName)),
        )
    }
}
