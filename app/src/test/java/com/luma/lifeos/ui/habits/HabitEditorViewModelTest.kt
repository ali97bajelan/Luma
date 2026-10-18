package com.luma.lifeos.ui.habits

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneId.of("UTC"))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun createDraftRestoresAndBlankUnitStaysNull() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val saved = SavedStateHandle()
        val first = HabitEditorViewModel(repo, null, saved, clock)
        advanceUntilIdle()
        first.setName("Read in French")
        first.setUnitLabel("   ")
        first.setTarget("4")

        val restored = HabitEditorViewModel(repo, null, saved, clock)
        advanceUntilIdle()
        assertEquals("Read in French", restored.state.value.draft.name)
        assertEquals("   ", restored.state.value.draft.unitLabel)
        restored.save(); advanceUntilIdle()

        val created = repo.definitions.values.single { it.name == "Read in French" }
        assertEquals(null, created.unitLabel)
        assertTrue(restored.state.value.completed)
    }

    @Test fun repeatedSaveIsBlockedAndInvalidUnicodeLengthKeepsAcceptedDraft() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val editor = HabitEditorViewModel(repo, null, SavedStateHandle(), clock)
        advanceUntilIdle()
        editor.setName("Water")
        editor.setName("💧".repeat(101))
        assertEquals("Water", editor.state.value.draft.name)
        assertTrue(editor.state.value.textError)

        editor.setName("Read")
        editor.save(); editor.save()
        assertTrue(editor.state.value.busy)
        advanceUntilIdle()
        assertTrue(editor.state.value.completed)
        assertEquals(1, repo.createCalls)
    }

    @Test fun aFailedSaveRetainsTheRestorableDraft() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock).apply { failNextRename = true }
        val existing = repo.definitions.values.single()
        val saved = SavedStateHandle()
        val editor = HabitEditorViewModel(repo, existing.id, saved, clock)
        advanceUntilIdle()
        editor.setName("Updated read")
        editor.save(); advanceUntilIdle()
        assertFalse(editor.state.value.completed)

        val restored = HabitEditorViewModel(repo, existing.id, saved, clock)
        advanceUntilIdle()
        assertEquals("Updated read", restored.state.value.draft.name)
    }

    @Test fun changingATargetPreservesPendingLifecycleAndWeekdays() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val existing = repo.definitions.values.single()
        val pendingWeekdays = setOf(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.THURSDAY)
        repo.versions.getValue(existing.id) += com.luma.lifeos.domain.habit.HabitSchedule(
            java.util.UUID(0, 4), existing.id, existing.start.plusDays(21), 12, pendingWeekdays,
            com.luma.lifeos.domain.habit.HabitLifecycle.PAUSED,
        )
        val editor = HabitEditorViewModel(repo, existing.id, SavedStateHandle(), clock)
        advanceUntilIdle()
        editor.setTarget("10")
        editor.save(); advanceUntilIdle()

        val pending = repo.versions.getValue(existing.id).single { it.effectiveFrom == existing.start.plusDays(21) }
        assertEquals(10, pending.target)
        assertEquals(pendingWeekdays, pending.weekdays)
        assertEquals(com.luma.lifeos.domain.habit.HabitLifecycle.PAUSED, pending.lifecycle)
    }

    @Test fun futureStartTargetEditKeepsTheInitialEffectiveDate() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = java.util.UUID(0, 5)
        val start = LocalDate.parse("2026-10-25")
        repo.create(id, com.luma.lifeos.domain.habit.HabitSaveInput("Future", null, start, 2,
            java.time.DayOfWeek.entries.toSet(), ZoneId.of("UTC")))
        val editor = HabitEditorViewModel(repo, id, SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(start, editor.state.value.effectiveDate)
        editor.setTarget("4")

        editor.save(); advanceUntilIdle()

        val schedule = repo.versions.getValue(id).single()
        assertEquals(start, schedule.effectiveFrom)
        assertEquals(4, schedule.target)
        assertEquals(com.luma.lifeos.domain.habit.HabitLifecycle.ACTIVE, schedule.lifecycle)
    }

    @Test fun emptyWeekdayDraftSurvivesRecreation() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val saved = SavedStateHandle()
        val editor = HabitEditorViewModel(repo, null, saved, clock)
        advanceUntilIdle()
        editor.setName("Read")
        editor.setWeekdays(emptySet())
        assertEquals(HabitEditorFieldError.WEEKDAYS_REQUIRED, editor.state.value.fieldError)

        val restored = HabitEditorViewModel(repo, null, saved, clock)
        advanceUntilIdle()

        assertTrue(restored.state.value.draft.weekdays.isEmpty())
        assertEquals(HabitEditorError.INPUT, restored.state.value.error)
        assertEquals(HabitEditorFieldError.WEEKDAYS_REQUIRED, restored.state.value.fieldError)
    }

    @Test fun blankHabitNameHasAFieldSpecificValidationError() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val repo = HabitRepositoryFixture(clock)
        val editor = HabitEditorViewModel(repo, null, saved, clock)
        advanceUntilIdle()
        editor.save()

        assertEquals(HabitEditorError.INPUT, editor.state.value.error)
        assertEquals(HabitEditorFieldError.NAME_REQUIRED, editor.state.value.fieldError)

        val restored = HabitEditorViewModel(repo, null, saved, clock)
        advanceUntilIdle()
        assertEquals(HabitEditorError.INPUT, restored.state.value.error)
        assertEquals(HabitEditorFieldError.NAME_REQUIRED, restored.state.value.fieldError)
    }
}
