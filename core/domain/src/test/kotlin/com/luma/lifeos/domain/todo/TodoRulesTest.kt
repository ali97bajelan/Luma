package com.luma.lifeos.domain.todo

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoRulesTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val createdAt = Instant.parse("2026-10-01T09:00:00Z")
    private val updatedAt = Instant.parse("2026-10-02T09:00:00Z")

    @Test
    fun titleLimitsCountSupplementaryUnicodeCodePoints() {
        val title200 = "📚".repeat(200)
        val normalized = normalizeTodoInput(input(title = title200))

        assertEquals(title200, normalized.title)
        assertEquals(
            TodoValidationReason.TOO_LONG,
            assertThrows(TodoValidationException::class.java) {
                normalizeTodoInput(input(title = "📚".repeat(201)))
            }.reason,
        )
    }

    @Test
    fun titleRequiresNonWhitespaceSingleLineText() {
        val blank = assertThrows(TodoValidationException::class.java) {
            normalizeTodoInput(input(title = " \t  "))
        }
        val multiline = assertThrows(TodoValidationException::class.java) {
            normalizeTodoInput(input(title = "line one\r\nline two"))
        }
        val unicodeLineSeparator = assertThrows(TodoValidationException::class.java) {
            normalizeTodoInput(input(title = "line one\u2028line two"))
        }

        assertEquals(TodoField.TITLE, blank.field)
        assertEquals(TodoValidationReason.REQUIRED, blank.reason)
        assertEquals(TodoField.TITLE, multiline.field)
        assertEquals(TodoValidationReason.LINE_BREAKS_NOT_ALLOWED, multiline.reason)
        assertEquals(TodoValidationReason.LINE_BREAKS_NOT_ALLOWED, unicodeLineSeparator.reason)
    }

    @Test
    fun noteLimitCountsSupplementaryUnicodeAndBlankNotesBecomeNull() {
        val note4000 = "🪴".repeat(4_000)

        assertEquals(note4000, normalizeTodoInput(input(note = note4000)).note)
        assertEquals(
            TodoValidationReason.TOO_LONG,
            assertThrows(TodoValidationException::class.java) {
                normalizeTodoInput(input(note = "🪴".repeat(4_001)))
            }.reason,
        )
        assertNull(normalizeTodoInput(input(note = " \n\t ")).note)
    }

    @Test
    fun normalizationTrimsTextAndPreservesPersianContent() {
        val persianTitle = "خرید نان"
        val persianNote = "صبح از نانوایی محله"
        val normalized = normalizeTodoInput(input(title = "  $persianTitle  ", note = "  $persianNote  "))

        assertEquals(persianTitle, normalized.title)
        assertEquals(persianNote, normalized.note)
    }

    @Test
    fun dueDatesAcceptOnlyYearsOneThrough9999() {
        assertEquals(LocalDate.of(1, 1, 1), normalizeTodoInput(input(dueDate = LocalDate.of(1, 1, 1))).dueDate)
        assertEquals(LocalDate.of(9999, 12, 31), normalizeTodoInput(input(dueDate = LocalDate.of(9999, 12, 31))).dueDate)

        listOf(LocalDate.of(0, 1, 1), LocalDate.of(10_000, 1, 1)).forEach { unsupported ->
            val error = assertThrows(TodoValidationException::class.java) {
                normalizeTodoInput(input(dueDate = unsupported))
            }
            assertEquals(TodoField.DUE_DATE, error.field)
            assertEquals(TodoValidationReason.OUT_OF_RANGE, error.reason)
        }
    }

    @Test
    fun bucketsUseCivilDatesAndTodayEligibilityExcludesUndatedAndCompletedTasks() {
        val overdue = task(1, dueDate = today.minusDays(1))
        val dueToday = task(2, dueDate = today)
        val upcoming = task(3, dueDate = today.plusDays(1))
        val undated = task(4)
        val completedToday = task(5, dueDate = today, completedAt = updatedAt)

        assertEquals(TodoBucket.OVERDUE, todoBucket(overdue, today))
        assertEquals(TodoBucket.TODAY, todoBucket(dueToday, today))
        assertEquals(TodoBucket.UPCOMING, todoBucket(upcoming, today))
        assertEquals(TodoBucket.NO_DATE, todoBucket(undated, today))
        assertTrue(isTodoTodayEligible(overdue, today))
        assertTrue(isTodoTodayEligible(dueToday, today))
        assertFalse(isTodoTodayEligible(upcoming, today))
        assertFalse(isTodoTodayEligible(undated, today))
        assertFalse(isTodoTodayEligible(completedToday, today))
    }

    @Test
    fun openOrderingUsesDueDateThenCreationTimeThenCanonicalUuidWithUndatedLast() {
        val earlierDue = task(90, dueDate = today.minusDays(1), createdAt = createdAt.plusSeconds(20))
        val sameDateLaterCreated = task(3, dueDate = today, createdAt = createdAt.plusSeconds(1))
        val sameDateUuid2 = task(2, dueDate = today, createdAt = createdAt)
        val sameDateUuid1 = task(1, dueDate = today, createdAt = createdAt)
        val laterDue = task(4, dueDate = today.plusDays(1), createdAt = createdAt.minusSeconds(100))
        val undatedOldest = task(5, createdAt = createdAt.minusSeconds(200))

        assertEquals(
            listOf(earlierDue, sameDateUuid1, sameDateUuid2, sameDateLaterCreated, laterDue, undatedOldest),
            listOf(undatedOldest, laterDue, sameDateLaterCreated, earlierDue, sameDateUuid2, sameDateUuid1)
                .sortedWith(openTodoTaskComparator),
        )
    }

    @Test
    fun completedOrderingUsesCompletionDescendingThenCanonicalUuid() {
        val older = task(3, completedAt = updatedAt.minusSeconds(1))
        val sameInstantUuid2 = task(2, completedAt = updatedAt)
        val sameInstantUuid1 = task(1, completedAt = updatedAt)
        val latest = task(4, completedAt = updatedAt.plusSeconds(1))

        assertEquals(
            listOf(latest, sameInstantUuid1, sameInstantUuid2, older),
            listOf(older, sameInstantUuid2, latest, sameInstantUuid1).sortedWith(completedTodoTaskComparator),
        )
    }

    @Test
    fun taskAllowsCompletionBeforeCreationAfterClockRollbackButRequiresMonotonicUpdatedAt() {
        val completedBeforeCreation = task(
            1,
            completedAt = createdAt.minusSeconds(1),
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        assertEquals(createdAt.minusSeconds(1), completedBeforeCreation.completedAt)
        assertThrows(IllegalArgumentException::class.java) {
            task(2, createdAt = createdAt, updatedAt = createdAt.minusSeconds(1))
        }
    }

    @Test
    fun completionStateIsDerivedOnlyFromCompletionTimestamp() {
        assertFalse(task(1, completedAt = null).isCompleted)
        assertTrue(task(2, completedAt = updatedAt).isCompleted)
    }

    @Test
    fun pageRejectsImpossibleTotalsAndDuplicateTaskIds() {
        val first = task(1)
        val duplicateId = first.copy(title = "another title")

        assertThrows(IllegalArgumentException::class.java) { TodoPage(listOf(first), total = 0) }
        assertThrows(IllegalArgumentException::class.java) { TodoPage(listOf(first, duplicateId), total = 2) }
    }

    private fun input(
        title: String = "Read a book",
        note: String? = null,
        dueDate: LocalDate? = null,
    ) = TodoInput(title = title, note = note, dueDate = dueDate)

    private fun task(
        id: Long,
        dueDate: LocalDate? = null,
        createdAt: Instant = this.createdAt,
        completedAt: Instant? = null,
        updatedAt: Instant = maxOf(this.updatedAt, createdAt),
    ) = TodoTask(
        id = UUID.fromString("00000000-0000-0000-0000-${id.toString().padStart(12, '0')}"),
        title = "Task $id",
        note = null,
        dueDate = dueDate,
        completedAt = completedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        revision = UUID.fromString("10000000-0000-0000-0000-${id.toString().padStart(12, '0')}"),
    )
}
