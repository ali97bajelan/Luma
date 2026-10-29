package com.luma.lifeos.focus

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.focus.RoomFocusRepository
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusShortcutHandoffTest {
    @Test fun handoffCheckpointsBeforeLaunchUsesLatestSelectionAndKeepsTimerRunning() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        val preferencesName = "focus_handoff_test_${UUID.randomUUID()}"
        val preferences = FocusPreferences(context, preferencesName)
        val secondWindowPreferences = FocusPreferences(context, preferencesName)
        val clock = MutableClock(sample(0, 10_000))
        val platform = FakePlatform(context.packageName)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val runtime = FocusRuntime(
            coordinator = FocusCoordinator(RoomFocusRepository(database), clock),
            preferences = preferences,
            allowedApps = AllowedFocusApps(platform),
            scope = scope,
        )
        try {
            runtime.initialize()
            awaitInitialized(runtime)
            assertTrue(runtime.start(UUID.randomUUID(), UUID.randomUUID()) is FocusWriteResult.Applied)
            assertTrue(secondWindowPreferences.setPackageAllowed(FIXTURE_PACKAGE, true))

            clock.current = sample(5_000, 15_000)
            assertEquals(FocusShortcutResult.LAUNCHED, runtime.launchSelectedApp(FIXTURE_PACKAGE))
            assertEquals(5_000L, runtime.state.value.session?.creditedMillis)
            assertEquals(FocusState.RUNNING, runtime.state.value.session?.state)
            assertEquals(1, platform.launchAttempts)

            platform.launchFailure = SecurityException("fixture launch denied")
            clock.current = sample(10_000, 20_000)
            assertEquals(FocusShortcutResult.UNAVAILABLE, runtime.launchSelectedApp(FIXTURE_PACKAGE))
            assertEquals(10_000L, runtime.state.value.session?.creditedMillis)
            assertTrue(runtime.state.value.shortcutFailed)
            assertEquals(FocusState.RUNNING, runtime.state.value.session?.state)

            platform.launchFailure = null
            assertEquals(FocusShortcutResult.LAUNCHED, runtime.launchSelectedApp(FIXTURE_PACKAGE))
            assertFalse(runtime.state.value.shortcutFailed)

            assertTrue(secondWindowPreferences.setPackageAllowed(FIXTURE_PACKAGE, false))
            clock.current = sample(15_000, 25_000)
            assertEquals(FocusShortcutResult.NOT_SELECTED, runtime.launchSelectedApp(FIXTURE_PACKAGE))
            assertEquals(15_000L, runtime.state.value.session?.creditedMillis)
            assertEquals(FocusState.RUNNING, runtime.state.value.session?.state)
            assertEquals(3, platform.launchAttempts)
        } finally {
            runtime.state.value.session?.takeIf { it.state == FocusState.RUNNING }?.let { session ->
                runtime.act(UUID.randomUUID(), session, FocusAction.STOP)
            }
            scope.cancel()
            database.close()
        }
    }

    private suspend fun awaitInitialized(runtime: FocusRuntime): FocusRuntimeState = withTimeout(10_000) {
        val ready = runtime.state.first { !it.loading && (it.initialized || it.loadFailed) }
        assertFalse("Focus runtime initialization failed", ready.loadFailed)
        ready
    }

    private class MutableClock(var current: FocusTimeSample) : FocusClock {
        override fun sample() = current
    }

    private class FakePlatform(override val ownPackageName: String) : FocusAppPlatform {
        var launchAttempts = 0
        var launchFailure: RuntimeException? = null

        override fun launcherPackages() = listOf(FIXTURE_PACKAGE, FIXTURE_PACKAGE)

        override fun resolveApp(packageName: String): AllowedFocusApps.App? =
            AllowedFocusApps.App(packageName, "Disposable focus fixture")

        override fun launch(packageName: String) {
            launchAttempts++
            launchFailure?.let { throw it }
        }
    }

    private fun sample(deltaMillis: Long, elapsedMillis: Long) = FocusTimeSample(
        wall = Instant.parse("2026-10-30T12:00:00Z").plusMillis(deltaMillis),
        elapsedMillis = elapsedMillis,
        bootId = "focus-test-boot",
    )

    private companion object {
        const val FIXTURE_PACKAGE = "com.example.focusfixture"
    }
}
