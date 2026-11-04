package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusStartInput
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSetupStateTest {
    @Test
    fun startIsEnabledOnlyForValidDrafts() {
        assertFalse(FocusSetupState().canStart)

        val valid = FocusSetupState(name = "  Read chapter 4  ", tag = " Study ")
        assertTrue(valid.canStart)
        assertEquals(FocusStartInput("Read chapter 4", "Study"), valid.normalizedInput)

        assertTrue(FocusSetupState(name = "Read", tag = "").canStart)
        assertFalse(FocusSetupState(name = "Read", tag = "   ").canStart)
        assertFalse(FocusSetupState(name = " ").canStart)
        assertFalse(FocusSetupState(name = "Read\nchapter").canStart)
        assertFalse(FocusSetupState(name = "Read", tag = "x".repeat(51)).canStart)
    }

    @Test
    fun draftSurvivesSavedStateRecreation() {
        val original = FocusSetupState(
            name = "Read chapter 4",
            tag = "Study",
            period = FocusPeriod.THIS_WEEK,
            viewingZoneId = "Asia/Tehran",
            viewingDate = LocalDate.parse("2026-11-04"),
            databaseGeneration = uuid(1),
        )

        val restored = restoreFocusSetupState(
            encodeFocusSetupState(original),
            defaultZoneId = "UTC",
            today = LocalDate.parse("2026-11-05"),
        )

        assertEquals(
            original.copy(viewingDate = LocalDate.parse("2026-11-05"), capturedStart = null, startFailure = null),
            restored,
        )
    }

    @Test
    fun ioFailureKeepsDraftAndCapturedStartInput() {
        val draft = FocusSetupState(name = "Read", tag = "Study")
        val captured = reduceFocusSetupState(
            draft,
            FocusSetupEvent.StartCaptured(FocusStartInput("Read", "Study")),
        )
        val failed = reduceFocusSetupState(captured, FocusSetupEvent.StartIoFailed)

        assertEquals("Read", failed.name)
        assertEquals("Study", failed.tag)
        assertEquals(FocusStartInput("Read", "Study"), failed.capturedStart)
        assertEquals(FocusSetupFailure.IO, failed.startFailure)
        assertFalse(failed.canStart)
    }

    @Test
    fun editingAfterAmbiguousFailureDoesNotChangeRetryPayload() {
        val captured = reduceFocusSetupState(
            FocusSetupState(name = "Original", tag = "Work"),
            FocusSetupEvent.StartCaptured(FocusStartInput("Original", "Work")),
        )
        val failed = reduceFocusSetupState(captured, FocusSetupEvent.StartIoFailed)
        val edited = reduceFocusSetupState(failed, FocusSetupEvent.NameChanged("Changed while waiting"))

        assertEquals("Changed while waiting", edited.name)
        assertEquals(FocusStartInput("Original", "Work"), edited.capturedStart)
        assertFalse(edited.canStart)
    }

    @Test
    fun successfulStartClearsDraftAndCapturedInput() {
        val captured = reduceFocusSetupState(
            FocusSetupState(name = "Read", tag = "Study"),
            FocusSetupEvent.StartCaptured(FocusStartInput("Read", "Study")),
        )

        val completed = reduceFocusSetupState(captured, FocusSetupEvent.StartSucceeded)

        assertEquals("", completed.name)
        assertEquals("", completed.tag)
        assertNull(completed.capturedStart)
        assertNull(completed.startFailure)
    }

    @Test
    fun databaseGenerationChangeClearsPrivateDraft() {
        val original = FocusSetupState(
            name = "Read",
            tag = "Study",
            databaseGeneration = uuid(1),
        )

        val replaced = reduceFocusSetupState(original, FocusSetupEvent.GenerationChanged(uuid(2)))

        assertEquals("", replaced.name)
        assertEquals("", replaced.tag)
        assertNull(replaced.capturedStart)
        assertEquals(uuid(2), replaced.databaseGeneration)
    }

    @Test
    fun aNewSessionStillShowsItsTerminalScreenAfterStartingAnotherWasSelected() {
        assertFalse(shouldShowFocusTerminal(
            uuid(1), requestedSetupSessionId = focusTerminalRequestKey(uuid(1), generation = null),
        ))
        assertTrue(shouldShowFocusTerminal(
            uuid(2), requestedSetupSessionId = focusTerminalRequestKey(uuid(1), generation = null),
        ))
        assertTrue(shouldShowFocusTerminal(uuid(1), requestedSetupSessionId = null))
    }

    @Test
    fun databaseReplacementDoesNotReuseAStartAnotherRequestForAnIdenticalSessionId() {
        val sessionId = uuid(1)
        val oldGeneration = uuid(10)
        val newGeneration = uuid(11)
        val requestKey = focusTerminalRequestKey(sessionId, oldGeneration)

        assertFalse(shouldShowFocusTerminal(sessionId, requestKey, oldGeneration))
        assertTrue(shouldShowFocusTerminal(sessionId, requestKey, newGeneration))
    }

    @Test
    fun periodAndViewingZoneChangesRebuildInsightRanges() {
        val date = LocalDate.parse("2026-03-08")
        val today = FocusSetupState(
            period = FocusPeriod.TODAY,
            viewingZoneId = "UTC",
            viewingDate = date,
            databaseGeneration = uuid(3),
        )
        val todaySelection = focusInsightsSelection(today)
        val weekState = reduceFocusSetupState(today, FocusSetupEvent.PeriodChanged(FocusPeriod.THIS_WEEK))
        val weekSelection = focusInsightsSelection(weekState)
        val zoneState = reduceFocusSetupState(
            reduceFocusSetupState(weekState, FocusSetupEvent.PeriodChanged(FocusPeriod.TODAY)),
            FocusSetupEvent.ViewingContextChanged(date, "America/New_York"),
        )
        val zoneSelection = focusInsightsSelection(zoneState)
        val allTime = focusInsightsSelection(
            reduceFocusSetupState(zoneState, FocusSetupEvent.PeriodChanged(FocusPeriod.ALL_TIME)),
        )

        assertNotEquals(todaySelection.currentRange, weekSelection.currentRange)
        assertNotEquals(weekSelection.currentRange, zoneSelection.currentRange)
        assertEquals(23 * 60 * 60 * 1_000L, durationMillis(zoneSelection.currentRange))
        assertNull(allTime.currentRange)
        assertNull(allTime.previousRange)
        assertEquals(ZoneId.of("America/New_York"), zoneSelection.zone)
    }

    @Test
    fun todayInsightQueryAdvancesWhenTheCurrentDayObserverChangesDate() {
        val generation = uuid(5)
        val before = FocusSetupState(
            period = FocusPeriod.TODAY,
            viewingZoneId = "Asia/Tehran",
            viewingDate = LocalDate.parse("2026-11-04"),
            databaseGeneration = generation,
        )
        val oldQuery = requireNotNull(focusInsightsQueryFor(before, initialized = true, generation))
        val after = reduceFocusSetupState(
            before,
            FocusSetupEvent.ViewingContextChanged(LocalDate.parse("2026-11-05"), "Asia/Tehran"),
        )
        val selected = requireNotNull(focusInsightsQueryFor(after, initialized = true, generation))

        assertNotEquals(oldQuery.range, selected.range)
        assertEquals(FocusInsightsState.Loading(selected), visibleFocusInsightsState(
            selected,
            FocusInsightsState.Ready(oldQuery, com.luma.lifeos.domain.focus.FocusInsights.EMPTY),
        ))
    }

    private fun durationMillis(range: com.luma.lifeos.domain.time.TimeRange?): Long {
        assertTrue(range != null)
        return java.time.Duration.between(range!!.start, range.endExclusive).toMillis()
    }

    private fun uuid(value: Long) = UUID(0, value)
}
