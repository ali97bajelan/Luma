package com.luma.lifeos.focus

import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.createFocusSession
import com.luma.lifeos.widget.WidgetDestination
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNavigationGuardTest {
    @Test fun runningSessionRetainsOneExplicitPendingDestinationUntilConsumed() {
        val state = runningState()
        val guard = FocusNavigationGuard().intercept(
            state,
            FocusPendingDestination.Widget(WidgetDestination.Today),
        )

        assertTrue(guard.isGuarded(state))
        assertEquals(FocusPendingDestination.Widget(WidgetDestination.Today), guard.pendingDestination)

        val (pending, cleared) = guard.consumePending()
        assertEquals(FocusPendingDestination.Widget(WidgetDestination.Today), pending)
        assertNull(cleared.pendingDestination)
    }

    @Test fun pausedOrReleasedSessionDoesNotQueueAnExternalDestination() {
        val running = runningState()
        val session = running.session!!
        val pausedSession = session.copy(
            state = FocusState.PAUSED,
            runs = session.runs.map { run ->
                run.copy(endedAt = run.startedAt, rawEndedAt = run.startedAt)
            },
        )
        val paused = running.copy(session = pausedSession)
        val destination = FocusPendingDestination.Route("today")

        assertFalse(FocusNavigationGuard().isGuarded(paused))
        assertNull(FocusNavigationGuard().intercept(paused, destination).pendingDestination)

        val emergencyReleased = running.copy(
            emergencyExitSessionId = running.session!!.id,
            emergencyExitConfirmed = true,
        )
        assertFalse(FocusNavigationGuard().isGuarded(emergencyReleased))
    }

    private fun runningState(): FocusRuntimeState = FocusRuntimeState(
        initialized = true,
        loading = false,
        session = createFocusSession(
            sessionId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            commandId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        ),
    )
}
