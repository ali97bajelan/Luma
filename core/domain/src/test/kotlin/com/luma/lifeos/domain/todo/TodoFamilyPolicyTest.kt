package com.luma.lifeos.domain.todo

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TodoFamilyPolicyTest {
    private val createdAt = Instant.parse("2026-11-01T09:00:00Z")

    @Test
    fun childShapeRequiresParentAndNonNegativeSiblingPositionTogether() {
        val rootId = id(1)

        assertThrows(IllegalArgumentException::class.java) {
            task(2, parentId = rootId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            task(2, siblingPosition = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            task(2, parentId = rootId, siblingPosition = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            task(2, parentId = id(2), siblingPosition = 0)
        }

        val child = task(2, parentId = rootId, siblingPosition = 0)
        assertEquals(rootId, child.parentId)
        assertEquals(0L, child.siblingPosition)
    }

    @Test
    fun rowContextAndProgressMustDescribeTheSameFamily() {
        val root = task(1)
        val child = task(2, parentId = root.id, siblingPosition = 0)
        val context = TodoParentContext(root.id, root.title)

        assertEquals(context, TodoTaskRow(child, context, null).parent)
        assertEquals(TodoChildProgress(completed = 1, total = 3), TodoTaskRow(
            root,
            parent = null,
            progress = TodoChildProgress(completed = 1, total = 3),
        ).progress)

        assertThrows(IllegalArgumentException::class.java) {
            TodoTaskRow(child, TodoParentContext(id(3), "Other parent"), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodoTaskRow(child, context, TodoChildProgress(completed = 0, total = 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodoChildProgress(completed = 0, total = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodoChildProgress(completed = 2, total = 1)
        }
    }

    @Test
    fun boundedChildPagesKeepFullFamilyProgressAndRejectDuplicateRows() {
        val root = task(1)
        val child = task(2, parentId = root.id, siblingPosition = 0)
        val progress = TodoChildProgress(completed = 1, total = 101)
        val page = TodoRowPage(
            items = listOf(TodoTaskRow(child, TodoParentContext(root.id, root.title), null)),
            total = 101,
            familyProgress = progress,
        )

        assertEquals(101, page.total)
        assertEquals(progress, page.familyProgress)
        assertThrows(IllegalArgumentException::class.java) {
            TodoRowPage(items = listOf(TodoTaskRow(child, page.items.single().parent, null), TodoTaskRow(child, page.items.single().parent, null)), total = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodoRowPage(items = listOf(page.items.single()), total = 0)
        }
    }

    private fun task(
        number: Int,
        parentId: UUID? = null,
        siblingPosition: Long? = null,
    ) = TodoTask(
        id = id(number),
        title = "Task $number",
        note = null,
        dueDate = null,
        dueTime = null,
        completedAt = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        revision = id(100 + number),
        parentId = parentId,
        siblingPosition = siblingPosition,
    )

    private fun id(value: Int): UUID = UUID.fromString(
        "00000000-0000-0000-0000-${value.toString().padStart(12, '0')}",
    )
}
