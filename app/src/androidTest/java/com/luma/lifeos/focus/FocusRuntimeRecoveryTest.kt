package com.luma.lifeos.focus

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusWriteResult
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusRuntimeRecoveryTest {
    @Test fun changedProcessRequiresExplicitRecoveryAndEndsOnlyAtTheSavedCheckpoint() = runBlocking {
        val (repository, preferences, context) = dependencies()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = newRuntime(repository, preferences, context, firstScope)
            first.initialize()
            awaitInitialized(first)
            assertTrue(first.start(UUID.randomUUID(), UUID.randomUUID()) is FocusWriteResult.Applied)
            val started = requireNotNull(first.state.value.session)
            assertEquals(FocusState.RUNNING, started.state)

            val restarted = newRuntime(repository, preferences, context, secondScope)
            restarted.initialize()
            val recovery = awaitInitialized(restarted)
            assertTrue(recovery.recoveryRequired)
            assertEquals(started.id, recovery.session?.id)
            assertEquals(FocusState.RUNNING, recovery.session?.state)

            val ended = restarted.resolveRecovery(started, FocusRecoveryChoice.END_AT_CHECKPOINT)
            assertTrue(ended is FocusWriteResult.Applied)
            assertEquals(FocusState.INTERRUPTED, restarted.state.value.session?.state)
            assertEquals(
                FocusTerminalReason.PROCESS_RESTART_GAP,
                restarted.state.value.session?.terminalReason,
            )
            assertEquals(0L, restarted.state.value.session?.creditedMillis)
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }

    @Test fun matchingEmergencyMarkerFinalizesAtCheckpointAndClearsTheMarker() = runBlocking {
        val (repository, preferences, context) = dependencies()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = newRuntime(repository, preferences, context, firstScope)
            first.initialize()
            awaitInitialized(first)
            assertTrue(first.start(UUID.randomUUID(), UUID.randomUUID()) is FocusWriteResult.Applied)
            val started = requireNotNull(first.state.value.session)
            assertTrue(first.markEmergencyExit(started.id))

            val restarted = newRuntime(repository, preferences, context, secondScope)
            restarted.initialize()
            val recovered = awaitInitialized(restarted)
            assertFalse(recovered.recoveryRequired)
            assertEquals(started.id, recovered.session?.id)
            assertEquals(FocusState.INTERRUPTED, recovered.session?.state)
            assertEquals(FocusTerminalReason.EMERGENCY_EXIT, recovered.session?.terminalReason)
            assertEquals(0L, recovered.session?.creditedMillis)
            assertNull(preferences.emergencyExitSessionId())
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }

    private fun dependencies(): Triple<FocusRepository, FocusPreferences, Context> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val application = context as LumaApplication
        return Triple(application.storage.focus, FocusPreferences(context), context)
    }

    private fun newRuntime(
        repository: FocusRepository,
        preferences: FocusPreferences,
        context: Context,
        scope: CoroutineScope,
    ) = FocusRuntime(
        coordinator = FocusCoordinator(
            repository = repository,
            clock = AndroidFocusClock(context),
            processInstanceId = UUID.randomUUID(),
        ),
        preferences = preferences,
        allowedApps = AllowedFocusApps(context),
        scope = scope,
    )

    private suspend fun awaitInitialized(runtime: FocusRuntime): FocusRuntimeState = withTimeout(10_000) {
        val ready = runtime.state.first { !it.loading && (it.initialized || it.loadFailed) }
        assertFalse("Focus runtime initialization failed", ready.loadFailed)
        ready
    }
}
