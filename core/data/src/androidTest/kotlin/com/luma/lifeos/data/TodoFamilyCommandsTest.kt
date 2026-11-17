package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.domain.todo.TodoChildProgress
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoConfirmationRequiredException
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoListQuery
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoMoveDirection
import com.luma.lifeos.domain.todo.TodoParentContext
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoSort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoFamilyCommandsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-11-15T08:00:00Z")
    private lateinit var database: LumaDatabase
    private lateinit var todos: TodoRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        todos = RoomTodoRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun childFieldsAndProgressAreIndependentAndChildPagesKeepTheFullCount() = runBlocking {
        val today = LocalDate.of(2026, 11, 15)
        val parent = createRoot("parent", TodoInput("Plan a trip"))
        val highToday = createChild(
            "high-child",
            parent.id,
            TodoInput("Book a train", dueDate = today, dueTime = LocalTime.of(9, 5), importance = TodoImportance.HIGH),
        )
        val lowOverdue = createChild(
            "low-child",
            parent.id,
            TodoInput("Check the route", dueDate = today.minusDays(1), importance = TodoImportance.LOW),
        )
        val completedLow = todos.setCompleted(lowOverdue.id, lowOverdue.revision, revision("complete-low"), true)

        val roots = todos.observeRows(TodoListQuery(completed = false), limit = 1).first()
        assertEquals(1, roots.total)
        assertEquals(listOf(parent.id), roots.items.map { it.task.id })
        assertEquals(TodoChildProgress(completed = 1, total = 2), roots.items.single().progress)

        val firstChildPage = todos.observeChildren(parent.id, limit = 1).first()
        assertEquals(2, firstChildPage.total)
        assertEquals(TodoChildProgress(completed = 1, total = 2), firstChildPage.familyProgress)
        assertEquals(highToday.id, firstChildPage.items.single().task.id)
        assertEquals(1, firstChildPage.items.size)
        assertEquals(today, highToday.dueDate)
        assertEquals(LocalTime.of(9, 5), highToday.dueTime)
        assertEquals(TodoImportance.HIGH, highToday.importance)
        assertNull(parent.dueDate)
        assertEquals(TodoImportance.NONE, parent.importance)
        assertEquals(completedLow.completedAt, todos.get(lowOverdue.id)?.completedAt)

        val flatHighMatches = todos.observeRows(
            TodoListQuery(completed = false, importance = TodoImportance.HIGH),
            limit = 50,
        ).first()
        assertEquals(1, flatHighMatches.total)
        assertEquals(highToday.id, flatHighMatches.items.single().task.id)
        assertEquals(TodoParentContext(parent.id, parent.title), flatHighMatches.items.single().parent)
        assertNull(flatHighMatches.items.single().progress)

        val todayRows = todos.observeTodayRows(today, limit = 50).first()
        assertEquals(1, todayRows.total)
        assertEquals(highToday.id, todayRows.items.single().task.id)
        assertEquals(TodoParentContext(parent.id, parent.title), todayRows.items.single().parent)

        val matchingSearch = todos.searchOpenRows("Book a train", offset = 0, limit = 10)
        assertEquals(1, matchingSearch.total)
        assertEquals(highToday.id, matchingSearch.items.single().task.id)
        assertEquals(TodoParentContext(parent.id, parent.title), matchingSearch.items.single().parent)
    }

    @Test
    fun childCreationRejectsMissingCompletedAndChildParents() = runBlocking {
        val missingParent = id("missing-parent")
        assertThrows(TodoMissingException::class.java) {
            runBlocking { todos.createChild(id("orphan"), revision("orphan"), missingParent, TodoInput("Orphan")) }
        }

        val completedParent = createRoot("completed-parent", TodoInput("Completed parent"))
        todos.setCompleted(completedParent.id, completedParent.revision, revision("complete-parent"), true)
        assertThrows(TodoConflictException::class.java) {
            runBlocking {
                todos.createChild(
                    id("child-under-completed"),
                    revision("child-under-completed"),
                    completedParent.id,
                    TodoInput("Unsupported child"),
                )
            }
        }

        val openRoot = createRoot("open-root", TodoInput("Open root"))
        val child = createChild("direct-child", openRoot.id, TodoInput("Direct child"))
        assertThrows(TodoConflictException::class.java) {
            runBlocking {
                todos.createChild(
                    id("grandchild"),
                    revision("grandchild"),
                    child.id,
                    TodoInput("Unsupported grandchild"),
                )
            }
        }
        assertNull(todos.get(id("orphan")))
        assertNull(todos.get(id("child-under-completed")))
        assertNull(todos.get(id("grandchild")))
    }

    @Test
    fun familyCompletionAndUndoPreservePreviouslyCompletedChildren() = runBlocking {
        val parent = createRoot("completion-parent", TodoInput("Finish the move"))
        val alreadyCompletedChild = createChild("already-completed", parent.id, TodoInput("Pack books"))
        val openChild = createChild("open-child", parent.id, TodoInput("Pack kitchen"))
        val previous = todos.setCompleted(
            alreadyCompletedChild.id,
            alreadyCompletedChild.revision,
            revision("complete-first-child"),
            true,
        )

        val preview = todos.prepareCompletion(parent.id, completed = true)
        assertTrue(preview.confirmationRequired)
        assertEquals(1, preview.openChildCount)
        assertEquals(2, preview.changedTaskCount)

        val commandId = id("complete-family-command")
        val receipt = todos.applyCompletion(preview.planId, commandId)
        assertEquals(2, receipt.changedTaskCount)
        assertTrue(receipt.undoAvailable)
        assertTrue(requireNotNull(todos.get(parent.id)).isCompleted)
        assertTrue(requireNotNull(todos.get(openChild.id)).isCompleted)
        val preservedCompletedChild = requireNotNull(todos.get(alreadyCompletedChild.id))
        assertEquals(previous.completedAt, preservedCompletedChild.completedAt)
        assertEquals(previous.revision, preservedCompletedChild.revision)
        assertEquals(receipt, todos.applyCompletion(preview.planId, commandId))

        val undo = todos.undoCompletion(commandId, id("undo-family-command"))
        assertEquals(2, undo.changedTaskCount)
        assertFalse(requireNotNull(todos.get(parent.id)).isCompleted)
        assertFalse(requireNotNull(todos.get(openChild.id)).isCompleted)
        val preservedCompletedChildAfterUndo = requireNotNull(todos.get(alreadyCompletedChild.id))
        assertEquals(previous.completedAt, preservedCompletedChildAfterUndo.completedAt)
        assertEquals(previous.revision, preservedCompletedChildAfterUndo.revision)
        assertEquals(undo, todos.undoCompletion(commandId, id("undo-family-command")))
    }

    @Test
    fun staleCompletionPreviewCannotCompleteAChildAddedAfterConfirmation() = runBlocking {
        val parent = createRoot("stale-parent", TodoInput("Ship a package"))
        val existing = createChild("stale-existing-child", parent.id, TodoInput("Wrap it"))
        val preview = todos.prepareCompletion(parent.id, completed = true)
        val added = createChild("stale-added-child", parent.id, TodoInput("Add a note"))

        assertThrows(TodoConflictException::class.java) {
            runBlocking { todos.applyCompletion(preview.planId, id("stale-completion-command")) }
        }
        assertFalse(requireNotNull(todos.get(parent.id)).isCompleted)
        assertFalse(requireNotNull(todos.get(existing.id)).isCompleted)
        assertFalse(requireNotNull(todos.get(added.id)).isCompleted)
        assertEquals(2, todos.observeChildren(parent.id, limit = 50).first().total)
    }

    @Test
    fun reopeningChildUnderCompletedParentReopensBothAtomically() = runBlocking {
        val parent = createRoot("reopen-parent", TodoInput("Publish a report"))
        val child = createChild("reopen-child", parent.id, TodoInput("Review figures"))
        val completedChild = todos.setCompleted(child.id, child.revision, revision("complete-review"), true)
        val completedParent = todos.setCompleted(parent.id, parent.revision, revision("complete-report"), true)
        assertTrue(completedParent.isCompleted)
        assertTrue(requireNotNull(todos.get(completedChild.id)).isCompleted)

        val preview = todos.prepareCompletion(child.id, completed = false)
        assertTrue(preview.reopensParent)
        assertTrue(preview.confirmationRequired)
        assertEquals(2, preview.changedTaskCount)

        val receipt = todos.applyCompletion(preview.planId, id("reopen-family-command"))
        assertEquals(2, receipt.changedTaskCount)
        assertFalse(requireNotNull(todos.get(parent.id)).isCompleted)
        assertFalse(requireNotNull(todos.get(child.id)).isCompleted)
    }

    @Test
    fun familyDeleteRequiresPreviewAndRemovesOnlyTheConfirmedFamily() = runBlocking {
        val parent = createRoot("delete-parent", TodoInput("Clear the desk"))
        val first = createChild("delete-first", parent.id, TodoInput("File papers"))
        val second = createChild("delete-second", parent.id, TodoInput("Recycle drafts"))

        val preview = todos.prepareDelete(parent.id)
        assertEquals(2, preview.childCount)
        val failure = assertThrows(TodoConfirmationRequiredException::class.java) {
            runBlocking { todos.delete(parent.id, parent.revision) }
        }
        assertEquals(parent.id, failure.taskId)
        assertEquals(3, failure.affectedTaskCount)

        todos.applyDelete(preview.planId, id("delete-family-command"))
        assertNull(todos.get(parent.id))
        assertNull(todos.get(first.id))
        assertNull(todos.get(second.id))
        assertEquals(0, todos.observeRows(TodoListQuery(completed = false), limit = 50).first().total)
    }

    @Test
    fun siblingMovesPreserveStableIdsAndDoNotRotateFamilyAtAnEdge() = runBlocking {
        val parent = createRoot("order-parent", TodoInput("Organize photos"))
        val first = createChild("order-first", parent.id, TodoInput("Create folders"))
        val second = createChild("order-second", parent.id, TodoInput("Sort albums"))
        val third = createChild("order-third", parent.id, TodoInput("Archive duplicates"))
        val initialFamilyRevision = requireNotNull(todos.get(parent.id)).familyRevision
        val firstBeforeEdgeMove = requireNotNull(todos.get(first.id))

        todos.moveChild(first.id, first.revision, revision("edge-no-op"), TodoMoveDirection.UP)
        assertEquals(firstBeforeEdgeMove, todos.get(first.id))
        assertEquals(initialFamilyRevision, requireNotNull(todos.get(parent.id)).familyRevision)

        todos.moveChild(second.id, second.revision, revision("move-second-up"), TodoMoveDirection.UP)
        val moved = todos.observeChildren(parent.id, limit = 50).first()
        assertEquals(listOf(second.id, first.id, third.id), moved.items.map { it.task.id })
        assertEquals(listOf(0L, 1L, 2L), moved.items.map { it.task.siblingPosition })
        assertNotEquals(initialFamilyRevision, requireNotNull(todos.get(parent.id)).familyRevision)
        assertEquals(setOf(first.id, second.id, third.id), moved.items.map { it.task.id }.toSet())
        assertThrows(TodoConflictException::class.java) {
            runBlocking {
                todos.moveChild(second.id, second.revision, revision("stale-second-move"), TodoMoveDirection.UP)
            }
        }
        assertEquals(listOf(second.id, first.id, third.id), todos.observeChildren(parent.id, limit = 50).first().items.map { it.task.id })
    }

    @Test
    fun oneHundredAndOneChildrenKeepStableOrderAcrossPageBoundaryAfterCompletion() = runBlocking {
        val parent = createRoot("page-boundary-parent", TodoInput("Review a large checklist"))
        val children = (0 until 101).map { index ->
            createChild("page-boundary-child-$index", parent.id, TodoInput("Checklist item $index"))
        }
        val completed = todos.setCompleted(
            children[50].id,
            children[50].revision,
            revision("page-boundary-completion"),
            completed = true,
        )
        todos.moveChild(
            completed.id,
            completed.revision,
            revision("page-boundary-move"),
            TodoMoveDirection.UP,
        )

        val firstPage = todos.observeChildren(parent.id, limit = 50).first()
        assertEquals(101, firstPage.total)
        assertEquals(50, firstPage.items.size)
        assertEquals(children[50].id, firstPage.items.last().task.id)
        assertTrue(firstPage.items.last().task.isCompleted)
        assertEquals(49L, firstPage.items.last().task.siblingPosition)
        assertEquals(TodoChildProgress(completed = 1, total = 101), firstPage.familyProgress)

        val allChildren = todos.observeChildren(parent.id, limit = 101).first()
        val expectedOrder = children.map { it.id }.toMutableList().apply {
            remove(children[50].id)
            add(49, children[50].id)
        }
        assertEquals(expectedOrder, allChildren.items.map { it.task.id })
        assertEquals((0L..100L).toList(), allChildren.items.map { it.task.siblingPosition })
    }

    @Test
    fun importanceFilteringOccursBeforeTheBoundedPrefixAndIncludesChildrenOnce() = runBlocking {
        val date = LocalDate.of(2026, 11, 16)
        repeat(51) { index ->
            createRoot(
                "low-root-$index",
                TodoInput("Low root $index", dueDate = date, importance = TodoImportance.LOW),
            )
        }
        val noneParent = createRoot("none-parent", TodoInput("None parent"))
        val matchingChild = createChild(
            "matching-high-child",
            noneParent.id,
            TodoInput("High child", dueDate = date.plusDays(1), importance = TodoImportance.HIGH),
        )
        val anotherHigh = createRoot(
            "another-high",
            TodoInput("High root", dueDate = date, importance = TodoImportance.HIGH),
        )

        val highPage = todos.observeRows(
            TodoListQuery(completed = false, importance = TodoImportance.HIGH, sort = TodoSort.IMPORTANCE),
            limit = 1,
        ).first()
        assertEquals(2, highPage.total)
        assertEquals(listOf(anotherHigh.id), highPage.items.map { it.task.id })

        val laterHigh = todos.observeRows(
            TodoListQuery(completed = false, importance = TodoImportance.HIGH, sort = TodoSort.IMPORTANCE),
            limit = 2,
        ).first()
        assertEquals(2, laterHigh.total)
        assertEquals(setOf(anotherHigh.id, matchingChild.id), laterHigh.items.map { it.task.id }.toSet())
        assertEquals(2, laterHigh.items.map { it.task.id }.distinct().size)

        val nonePage = todos.observeRows(
            TodoListQuery(completed = false, importance = TodoImportance.NONE),
            limit = 50,
        ).first()
        assertEquals(1, nonePage.total)
        assertEquals(noneParent.id, nonePage.items.single().task.id)
    }

    private suspend fun createRoot(label: String, input: TodoInput) =
        todos.create(id(label), revision("$label-initial"), input)

    private suspend fun createChild(label: String, parentId: UUID, input: TodoInput) =
        todos.createChild(id(label), revision("$label-initial"), parentId, input)

    private fun id(label: String): UUID = UUID.nameUUIDFromBytes("ti-qa01-$label".toByteArray())

    private fun revision(label: String): UUID =
        UUID.nameUUIDFromBytes("ti-qa01-revision-$label".toByteArray())
}
