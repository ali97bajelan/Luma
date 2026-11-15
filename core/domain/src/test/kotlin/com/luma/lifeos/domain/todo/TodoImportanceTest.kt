package com.luma.lifeos.domain.todo

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TodoImportanceTest {
    @Test
    fun persistedImportanceCodesAreStableAndNoneDiffersFromLow() {
        val values = listOf(
            TodoImportance.NONE to 0,
            TodoImportance.LOW to 1,
            TodoImportance.MEDIUM to 2,
            TodoImportance.HIGH to 3,
        )

        values.forEach { (importance, code) ->
            assertEquals(code, importance.code)
            assertEquals(importance, TodoImportance.fromCode(code))
        }
        assertThrows(IllegalArgumentException::class.java) { TodoImportance.fromCode(-1) }
        assertThrows(IllegalArgumentException::class.java) { TodoImportance.fromCode(4) }
        assertEquals(TodoImportance.NONE, TodoImportance.fromCode(0))
        assertEquals(TodoImportance.LOW, TodoImportance.fromCode(1))
    }

    @Test
    fun titleOnlyInputDefaultsToNoneAndImportanceDoesNotCreateUrgency() {
        val quickCapture = normalizeTodoInput(TodoInput(title = "Capture a thought"))
        val importantWithoutDeadline = normalizeTodoInput(
            TodoInput(title = "Keep this important", importance = TodoImportance.HIGH),
        )
        val dated = normalizeTodoInput(
            TodoInput(
                title = "Schedule separately",
                dueDate = LocalDate.of(2026, 11, 15),
                importance = TodoImportance.LOW,
            ),
        )

        assertEquals(TodoImportance.NONE, quickCapture.importance)
        assertNull(quickCapture.dueDate)
        assertNull(quickCapture.dueTime)
        assertEquals(TodoImportance.HIGH, importantWithoutDeadline.importance)
        assertNull(importantWithoutDeadline.dueDate)
        assertNull(importantWithoutDeadline.dueTime)
        assertEquals(TodoImportance.LOW, dated.importance)
        assertEquals(LocalDate.of(2026, 11, 15), dated.dueDate)
        assertNull(dated.dueTime)
    }
}
