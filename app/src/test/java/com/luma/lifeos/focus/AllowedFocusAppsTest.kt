package com.luma.lifeos.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedFocusAppsTest {
    @Test fun discoveryDeduplicatesActivitiesAndOmitsOwnOrUnlaunchablePackages() {
        val platform = FakePlatform(
            ownPackageName = "com.luma",
            packages = listOf("com.luma", "com.example.zebra", "com.example.alpha", "com.example.alpha", "com.missing"),
            apps = mapOf(
                "com.example.zebra" to AllowedFocusApps.App("com.example.zebra", "Zebra"),
                "com.example.alpha" to AllowedFocusApps.App("com.example.alpha", "Alpha"),
            ),
        )

        val apps = AllowedFocusApps(platform).discover()

        assertEquals(listOf("com.example.alpha", "com.example.zebra"), apps.map { it.packageName })
    }

    @Test fun duplicateLabelsShowPackageIdentity() {
        val first = AllowedFocusApps.App("org.example.reader", "Reader")
        val second = AllowedFocusApps.App("net.example.reader", "reader")
        val apps = listOf(first, second)

        assertEquals("Reader · org.example.reader", first.displayName(apps))
        assertEquals("reader · net.example.reader", second.displayName(apps))
        assertEquals("Reader", first.displayName(listOf(first)))
    }

    @Test fun launchRejectsPackagesThatAreNoLongerSelected() {
        val platform = FakePlatform(
            ownPackageName = "com.luma",
            packages = listOf("com.example.reader"),
            apps = mapOf("com.example.reader" to AllowedFocusApps.App("com.example.reader", "Reader")),
        )

        val result = AllowedFocusApps(platform).launch("com.example.reader", emptySet())

        assertEquals(AllowedFocusApps.LaunchResult.NOT_SELECTED, result)
        assertEquals(0, platform.launchAttempts)
        assertEquals(0, platform.resolveAttempts)
    }

    @Test fun launchRechecksAvailabilityAndContainsSecurityFailures() {
        val platform = FakePlatform(
            ownPackageName = "com.luma",
            packages = emptyList(),
            apps = emptyMap(),
        )
        val allowedApps = AllowedFocusApps(platform)

        assertEquals(
            AllowedFocusApps.LaunchResult.UNAVAILABLE,
            allowedApps.launch("com.example.reader", setOf("com.example.reader")),
        )
        platform.apps = mapOf("com.example.reader" to AllowedFocusApps.App("com.example.reader", "Reader"))
        platform.launchFailure = SecurityException("launch denied")
        assertEquals(
            AllowedFocusApps.LaunchResult.UNAVAILABLE,
            allowedApps.launch("com.example.reader", setOf("com.example.reader")),
        )
        assertTrue(platform.launchAttempts > 0)
    }

    private class FakePlatform(
        override val ownPackageName: String,
        private val packages: List<String>,
        var apps: Map<String, AllowedFocusApps.App?>,
    ) : FocusAppPlatform {
        var resolveAttempts = 0
        var launchAttempts = 0
        var launchFailure: RuntimeException? = null

        override fun launcherPackages() = packages

        override fun resolveApp(packageName: String): AllowedFocusApps.App? {
            resolveAttempts++
            return apps[packageName]
        }

        override fun launch(packageName: String) {
            launchAttempts++
            launchFailure?.let { throw it }
        }
    }
}
