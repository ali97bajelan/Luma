package com.luma.lifeos.ui.habits

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.habit.HabitLog
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate.parse("2026-10-18")
    private val zone = ZoneId.of("UTC")
    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), zone)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun rapidActivationsKeepDistinctIdsAndCaptureTheSelectedDate() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock).apply { blockLog = CompletableDeferred() }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(day, zone); advanceUntilIdle()

        repeat(3) { vm.log(repo.habitId()) }
        runCurrent()

        val actions = vm.state.value.pendingLogs
        assertEquals(3, actions.size)
        assertEquals(3, actions.map { it.id }.distinct().size)
        assertEquals(day, actions.first().input.date)
        assertEquals(clock.instant(), actions.first().input.occurredAt)
        repo.blockLog?.complete(Unit)
        advanceUntilIdle()
        assertEquals(3, repo.logs.size)
    }

    @Test fun retryAndRecreationReconcileTheSameCommittedAction() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock).apply { failAfterLog = true }
        val saved = SavedStateHandle()
        val vm = HabitsViewModel(repo, saved, clock)
        vm.selectDate(day, zone); advanceUntilIdle(); vm.log(repo.habitId()); advanceUntilIdle()

        val pending = vm.state.value.pendingLogs.single()
        assertEquals(1, repo.logs.size)
        assertEquals("committed, response lost", pending.error)
        vm.retryLog(pending.id); advanceUntilIdle()
        assertEquals(1, repo.logs.size)
        assertEquals(pending.id, repo.logInputs.first().first)

        repo.failAfterLog = false
        val restored = HabitsViewModel(repo, saved, clock)
        restored.selectDate(day, zone); advanceUntilIdle()
        assertTrue(restored.state.value.pendingLogs.isEmpty())
        assertEquals(1, repo.logs.size)
    }

    @Test fun dateNavigationCannotMoveAnAcceptedLog() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = HabitRepositoryFixture(clock).apply { blockLog = gate }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(day, zone); advanceUntilIdle(); vm.log(repo.habitId()); runCurrent()
        vm.selectDate(day.plusDays(1), zone)
        gate.complete(Unit); advanceUntilIdle()

        assertEquals(day, repo.logs.values.single().date)
        assertEquals(day.plusDays(1), vm.state.value.date)
    }

    @Test fun progressCanExceedTargetAndSkipDoesNotRemoveLogs() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        repeat(9) { index ->
            val id = UUID(1, index.toLong())
            repo.logs[id] = com.luma.lifeos.domain.habit.HabitLog(id, repo.habitId(), day, zone,
                clock.instant().minusSeconds(index.toLong()), clock.instant(), clock.instant())
        }
        repo.setSkipRemaining(repo.habitId(), day, true)
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(day, zone); advanceUntilIdle()

        val row = vm.state.value.days.single()
        assertEquals(9, row.day.count)
        assertEquals(8, row.day.schedule?.target)
        assertTrue(row.day.skipRemaining)
        assertTrue(row.canLog)
    }

    @Test fun undoDeletesOnlyTheSelectedRepetition() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val ids = (1L..3L).map { UUID(2, it) }
        ids.forEach { id -> repo.logs[id] = com.luma.lifeos.domain.habit.HabitLog(id, repo.habitId(), day,
            zone, null, clock.instant(), clock.instant()) }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(day, zone); advanceUntilIdle(); vm.undoLog(ids[1]); advanceUntilIdle()

        assertEquals(setOf(ids[0], ids[2]), repo.logs.keys)
        assertEquals(2, vm.state.value.days.single().day.count)
    }

    @Test fun futureDatesDisableLoggingWithAReason() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(day.plusDays(1), zone); advanceUntilIdle()

        val row = vm.state.value.days.single()
        assertFalse(row.canLog)
        assertEquals(HabitLogDisabledReason.FUTURE_DATE, row.disabledReason)
        vm.log(repo.habitId())
        advanceUntilIdle()
        assertTrue(repo.logInputs.isEmpty())
    }

    @Test fun historyLoadsOlderRepetitionsWithoutReplacingTheFirstPage() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        repeat(55) { index ->
            val id = UUID(3, index.toLong())
            repo.logs[id] = com.luma.lifeos.domain.habit.HabitLog(id, repo.habitId(), day, zone,
                null, clock.instant().minusSeconds(index.toLong()), clock.instant())
        }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()
        assertEquals(50, vm.state.value.detail?.history?.size)

        vm.loadMoreHistory(); advanceUntilIdle()

        assertEquals(55, vm.state.value.detail?.history?.size)
        assertEquals(55, vm.state.value.detail?.historyTotal)
    }

    @Test fun aStartedScheduleChangeIsPreviewedForTomorrowAndCanBeCancelled() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(day, zone); advanceUntilIdle()
        vm.updateSchedule(repo.habitId(), target = 10, weekdays = java.time.DayOfWeek.entries.toSet(),
            lifecycle = com.luma.lifeos.domain.habit.HabitLifecycle.ACTIVE)
        advanceUntilIdle()

        assertEquals(day.plusDays(1), vm.state.value.pendingSchedules[repo.habitId()]?.effectiveFrom)
        assertTrue(vm.state.value.days.single().canLog)
        vm.cancelPendingSchedule(repo.habitId()); advanceUntilIdle()
        assertNull(vm.state.value.pendingSchedules[repo.habitId()])
        assertEquals(8, vm.state.value.days.single().day.schedule?.target)
    }

    @Test fun pausingAStartedHabitPreservesItsPendingTargetAndWeekdays() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = repo.habitId()
        val pendingWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        repo.versions.getValue(id) += com.luma.lifeos.domain.habit.HabitSchedule(
            UUID(0, 3), id, day.plusDays(1), 10, pendingWeekdays,
            com.luma.lifeos.domain.habit.HabitLifecycle.ACTIVE,
        )
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(day, zone); advanceUntilIdle()

        vm.changeLifecycle(id, com.luma.lifeos.domain.habit.HabitLifecycle.PAUSED)
        advanceUntilIdle()

        val pending = repo.versions.getValue(id).single { it.effectiveFrom == day.plusDays(1) }
        assertEquals(10, pending.target)
        assertEquals(pendingWeekdays, pending.weekdays)
        assertEquals(com.luma.lifeos.domain.habit.HabitLifecycle.PAUSED, pending.lifecycle)
    }

    @Test fun retryReloadsFailedDetailHistory() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock).apply { failHistoryReads = true }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()
        assertEquals(HabitUiError.LOAD, vm.state.value.detail?.error)

        repo.failHistoryReads = false
        vm.retry(); advanceUntilIdle()

        assertEquals(null, vm.state.value.detail?.error)
        assertEquals(repo.habitId(), vm.state.value.detail?.habit?.id)
    }

    @Test fun logNowPreservesTheSecondOffsetDuringADstOverlap() = runTest(dispatcher) {
        val overlapZone = ZoneId.of("America/New_York")
        val overlapClock = Clock.fixed(Instant.parse("2026-11-01T06:30:00Z"), overlapZone)
        val overlapDate = LocalDate.now(overlapClock.withZone(overlapZone))
        val repo = HabitRepositoryFixture(overlapClock, overlapZone)
        val vm = HabitsViewModel(repo, SavedStateHandle(), overlapClock)
        vm.selectDate(overlapDate, overlapZone); advanceUntilIdle()

        vm.log(repo.habitId()); advanceUntilIdle()

        assertEquals(overlapClock.instant(), repo.logInputs.single().second.occurredAt)
    }

    @Test fun movingCorrectionPreservesLogIdentityAndRecordedAt() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = UUID(8, 1)
        val original = HabitLog(id, repo.habitId(), day, zone, clock.instant().minusSeconds(3600),
            clock.instant(), clock.instant())
        repo.logs[id] = original
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()
        vm.beginCorrection(original)
        vm.changeCorrectionDate(day.minusDays(1))
        vm.changeCorrectionTime(LocalTime.of(8, 15))

        vm.saveCorrection(); advanceUntilIdle()

        val corrected = repo.logs.getValue(id)
        assertEquals(id, corrected.id)
        assertEquals(day.minusDays(1), corrected.date)
        assertEquals(original.recordedAt, corrected.recordedAt)
        assertEquals(Instant.parse("2026-10-17T08:15:00Z"), corrected.occurredAt)
    }

    @Test fun failedCorrectionDraftSurvivesViewModelRecreation() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = UUID(8, 2)
        val original = HabitLog(id, repo.habitId(), day, zone, clock.instant().minusSeconds(3600),
            clock.instant(), clock.instant())
        repo.logs[id] = original
        val saved = SavedStateHandle()
        val vm = HabitsViewModel(repo, saved, clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()
        vm.beginCorrection(original)
        vm.changeCorrectionDate(day.minusDays(2))
        vm.changeCorrectionTime(LocalTime.of(9, 30))
        repo.failNextUpdate = true

        vm.saveCorrection(); advanceUntilIdle()
        assertEquals(HabitCorrectionError.SAVE, vm.state.value.detail?.correctionError)
        assertFalse(vm.state.value.detail?.correctionBusy == true)
        assertEquals(original, repo.logs.getValue(id))

        val restored = HabitsViewModel(repo, saved, clock)
        restored.openDetail(repo.habitId(), day, zone); advanceUntilIdle()

        assertEquals(day.minusDays(2), restored.state.value.detail?.correction?.selectedDate)
        assertEquals(LocalTime.of(9, 30), restored.state.value.detail?.correction?.selectedTime)
        assertTrue(restored.state.value.detail?.correction?.hasChanges == true)
        assertEquals(original, repo.logs.getValue(id))
    }

    @Test fun unchangedPreciseCorrectionSaveClosesWithoutWriting() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = UUID(8, 4)
        val precise = Instant.parse("2026-10-18T09:38:25.276Z")
        val original = HabitLog(id, repo.habitId(), day, zone, precise, clock.instant(), clock.instant())
        repo.logs[id] = original
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()

        vm.beginCorrection(original)
        assertFalse(vm.state.value.detail!!.correction!!.hasChanges)
        vm.saveCorrection(); advanceUntilIdle()

        assertEquals(0, repo.updateCalls)
        assertNull(vm.state.value.detail?.correction)
        assertEquals(precise, repo.logs.getValue(id).occurredAt)
    }

    @Test fun selectingTheDisplayedMinuteIsCleanAndKeepsSubMinutePrecision() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = UUID(8, 5)
        val precise = Instant.parse("2026-10-18T09:38:25.276Z")
        val original = HabitLog(id, repo.habitId(), day, zone, precise, clock.instant(), clock.instant())
        repo.logs[id] = original
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()

        vm.beginCorrection(original)
        vm.changeCorrectionTime(LocalTime.of(9, 38))
        assertFalse(vm.state.value.detail!!.correction!!.hasChanges)
        vm.saveCorrection(); advanceUntilIdle()

        assertEquals(0, repo.updateCalls)
        assertEquals(precise, repo.logs.getValue(id).occurredAt)
    }

    @Test fun dateOnlyCorrectionRetainsPrecisionAndValidOverlapOffset() = runTest(dispatcher) {
        val overlapZone = ZoneId.of("America/New_York")
        val overlapClock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), overlapZone)
        val repo = HabitRepositoryFixture(overlapClock, overlapZone)
        val id = UUID(8, 6)
        val precise = Instant.parse("2026-11-01T06:30:25.276Z") // second 01:30 during the fall overlap
        val original = HabitLog(id, repo.habitId(), LocalDate.parse("2026-11-01"), overlapZone,
            precise, overlapClock.instant(), overlapClock.instant())
        repo.logs[id] = original
        val vm = HabitsViewModel(repo, SavedStateHandle(), overlapClock)
        vm.openDetail(repo.habitId(), original.date, overlapZone); advanceUntilIdle()

        vm.beginCorrection(original)
        vm.changeCorrectionDate(LocalDate.parse("2026-11-02"))
        vm.saveCorrection(); advanceUntilIdle()

        val corrected = repo.logs.getValue(id)
        assertEquals(Instant.parse("2026-11-02T06:30:25.276Z"), corrected.occurredAt)
        assertEquals(overlapZone, corrected.zone)
        assertEquals(original.recordedAt, corrected.recordedAt)
    }

    @Test fun explicitNoTimeSavesNullOccurrence() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = UUID(8, 7)
        val original = HabitLog(id, repo.habitId(), day, zone, clock.instant().minusSeconds(3600),
            clock.instant(), clock.instant())
        repo.logs[id] = original
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()

        vm.beginCorrection(original)
        vm.changeCorrectionTime(null)
        vm.saveCorrection(); advanceUntilIdle()

        assertNull(repo.logs.getValue(id).occurredAt)
        assertEquals(1, repo.updateCalls)
    }

    @Test fun changingTimeUsesMinutePrecision() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = UUID(8, 8)
        val original = HabitLog(id, repo.habitId(), day, zone, clock.instant().minusSeconds(3600),
            clock.instant(), clock.instant())
        repo.logs[id] = original
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()

        vm.beginCorrection(original)
        vm.changeCorrectionTime(LocalTime.of(8, 45, 13, 500_000))
        vm.saveCorrection(); advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-18T08:45:00Z"), repo.logs.getValue(id).occurredAt)
    }

    @Test fun revertingDateReturnsDraftToCleanState() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val original = repo.logs[UUID(8, 9)] ?: HabitLog(UUID(8, 9), repo.habitId(), day, zone,
            clock.instant(), clock.instant(), clock.instant()).also { repo.logs[it.id] = it }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()

        vm.beginCorrection(original)
        vm.changeCorrectionDate(day.minusDays(1))
        assertTrue(vm.state.value.detail!!.correction!!.hasChanges)
        vm.changeCorrectionDate(day)

        assertFalse(vm.state.value.detail!!.correction!!.hasChanges)
        vm.saveCorrection(); advanceUntilIdle()
        assertEquals(0, repo.updateCalls)
    }

    @Test fun futureDateIsReportedOnDateFieldAndRetainsDraft() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val original = HabitLog(UUID(8, 10), repo.habitId(), day, zone, clock.instant().minusSeconds(3600),
            clock.instant(), clock.instant()).also { repo.logs[it.id] = it }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()

        vm.beginCorrection(original)
        vm.changeCorrectionDate(day.plusDays(1))
        vm.saveCorrection(); advanceUntilIdle()

        assertEquals(HabitCorrectionError.DATE, vm.state.value.detail?.correctionError)
        assertNotNull(vm.state.value.detail?.correction)
        assertEquals(0, repo.updateCalls)
    }

    @Test fun aDstGapIsReportedOnTimeField() = runTest(dispatcher) {
        val gapZone = ZoneId.of("America/New_York")
        val gapClock = Clock.fixed(Instant.parse("2026-03-08T10:00:00Z"), gapZone)
        val repo = HabitRepositoryFixture(gapClock, gapZone)
        val gapDate = LocalDate.parse("2026-03-08")
        val original = HabitLog(UUID(8, 11), repo.habitId(), gapDate, gapZone, null,
            gapClock.instant(), gapClock.instant()).also { repo.logs[it.id] = it }
        val vm = HabitsViewModel(repo, SavedStateHandle(), gapClock)
        vm.openDetail(repo.habitId(), gapDate, gapZone); advanceUntilIdle()

        vm.beginCorrection(original)
        vm.changeCorrectionTime(LocalTime.of(2, 30))
        vm.saveCorrection(); advanceUntilIdle()

        assertEquals(HabitCorrectionError.TIME, vm.state.value.detail?.correctionError)
        assertNull(repo.logs.getValue(original.id).occurredAt)
        assertEquals(0, repo.updateCalls)
    }

    @Test fun missingCorrectionLogProducesRecoverableMissingError() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val original = HabitLog(UUID(8, 12), repo.habitId(), day, zone, clock.instant(),
            clock.instant(), clock.instant())
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()
        vm.beginCorrection(original)
        vm.changeCorrectionDate(day.minusDays(1))

        vm.saveCorrection(); advanceUntilIdle()

        assertEquals(HabitCorrectionError.MISSING, vm.state.value.detail?.correctionError)
        assertNotNull(vm.state.value.detail?.correction)
        assertEquals(0, repo.updateCalls)
    }

    @Test fun duplicateCorrectionSaveWhileBusyWritesOnlyOnce() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock).apply { blockUpdate = CompletableDeferred() }
        val original = HabitLog(UUID(8, 13), repo.habitId(), day, zone, clock.instant().minusSeconds(3600),
            clock.instant(), clock.instant()).also { repo.logs[it.id] = it }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(repo.habitId(), day, zone); advanceUntilIdle()
        vm.beginCorrection(original)
        vm.changeCorrectionTime(LocalTime.of(8, 15))
        val expectedDraft = vm.state.value.detail?.correction

        vm.saveCorrection(); runCurrent()
        assertTrue(vm.state.value.detail?.correctionBusy == true)
        repo.changes.emit(Unit); runCurrent()
        assertTrue(vm.state.value.detail?.correctionBusy == true)
        assertEquals(expectedDraft, vm.state.value.detail?.correction)
        vm.saveCorrection(); runCurrent()
        assertEquals(1, repo.updateCalls)
        repo.blockUpdate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.updateCalls)
        assertNull(vm.state.value.detail?.correction)
    }

    @Test fun futureStartArchiveUsesItsInitialScheduleDate() = runTest(dispatcher) {
        val repo = HabitRepositoryFixture(clock)
        val id = UUID(8, 3)
        val start = day.plusDays(7)
        repo.create(id, com.luma.lifeos.domain.habit.HabitSaveInput("Future", null, start, 2,
            DayOfWeek.entries.toSet(), zone))
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.openDetail(id, day, zone); advanceUntilIdle()

        vm.changeLifecycle(id, com.luma.lifeos.domain.habit.HabitLifecycle.ARCHIVED); advanceUntilIdle()

        val schedule = repo.versions.getValue(id).single()
        assertEquals(start, schedule.effectiveFrom)
        assertEquals(com.luma.lifeos.domain.habit.HabitLifecycle.ARCHIVED, schedule.lifecycle)
        vm.openDetail(id, start, zone); advanceUntilIdle()
        assertEquals(id, vm.state.value.detail?.habit?.id)
        assertEquals(start, vm.state.value.detail?.day?.date)
        assertEquals(com.luma.lifeos.domain.habit.HabitLifecycle.ARCHIVED,
            vm.state.value.detail?.day?.schedule?.lifecycle)
    }
}
