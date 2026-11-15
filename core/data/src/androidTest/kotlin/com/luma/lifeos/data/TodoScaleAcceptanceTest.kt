package com.luma.lifeos.data

import android.os.Build
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.data.todo.TodoEntity
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoListQuery
import com.luma.lifeos.domain.todo.TodoSort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoScaleAcceptanceTest {
    @Test fun tenThousandTasksKeepRepositoryPagesBoundedAndReportReadLatency() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "todo-scale-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, LumaDatabase::class.java, databaseName).build()
        try {
            val today = LocalDate.parse("2026-10-19")
            val baseCreatedAt = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
            val completedAt = Instant.parse("2026-10-18T12:00:00Z").toEpochMilli()
            database.withTransaction {
                repeat(TASK_COUNT) { index ->
                    val taskId = UUID.nameUUIDFromBytes("td07-scale-task-$index".toByteArray()).toString()
                    val revision = UUID.nameUUIDFromBytes("td07-scale-revision-$index".toByteArray()).toString()
                    val createdAt = baseCreatedAt + index
                    val dueDate = when (index % 10) {
                        0 -> today.plusDays(1).toString()
                        1 -> null
                        else -> today.minusDays((index % 30).toLong()).toString()
                    }
                    database.todos().insert(
                        TodoEntity(
                            id = taskId,
                            title = "Scale task $index",
                            note = null,
                            dueDate = dueDate,
                            completedAt = if (index % 25 == 0) completedAt else null,
                            createdAt = createdAt,
                            updatedAt = createdAt,
                            revisionToken = revision,
                        ),
                    )
                }
            }

            val repository = RoomTodoRepository(
                database,
                Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC),
            )
            val openPage = repository.observeList(completed = false, limit = 50).first()
            val todayPage = repository.observeToday(today, limit = 3).first()
            assertEquals(9_600, openPage.total)
            assertEquals(50, openPage.items.size)
            assertEquals(7_800, todayPage.total)
            assertEquals(3, todayPage.items.size)

            repeat(WARM_UPS) {
                repository.observeList(completed = false, limit = 50).first()
                repository.observeToday(today, limit = 3).first()
            }
            val listSamples = measureSamples(SAMPLE_COUNT) {
                val page = repository.observeList(completed = false, limit = 50).first()
                assertEquals(9_600, page.total)
                assertEquals(50, page.items.size)
            }
            val todaySamples = measureSamples(SAMPLE_COUNT) {
                val page = repository.observeToday(today, limit = 3).first()
                assertEquals(7_800, page.total)
                assertEquals(3, page.items.size)
            }
            val listP50 = percentile(listSamples, 0.50)
            val listP95 = percentile(listSamples, 0.95)
            val todayP50 = percentile(todaySamples, 0.50)
            val todayP95 = percentile(todaySamples, 0.95)
            android.util.Log.i(
                "TodoScaleAcceptance",
                "TD07 task fixture=$TASK_COUNT open=${openPage.total} today=${todayPage.total}; " +
                    "device=${Build.MODEL} api=${Build.VERSION.SDK_INT}; " +
                    "open_ms_p50=$listP50 open_ms_p95=$listP95 " +
                    "today_ms_p50=$todayP50 today_ms_p95=$todayP95",
            )
            assertTrue("Initial open-page p95 exceeded 300 ms: $listP95", listP95 <= 300.0)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun tenThousandRowsKeepFamilyProgressAndMatchingPagesBounded() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "todo-family-scale-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, LumaDatabase::class.java, databaseName).build()
        try {
            val today = LocalDate.parse("2026-11-15")
            val createdAt = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
            val familyRootUuid = UUID.nameUUIDFromBytes("ti12-scale-family-root".toByteArray())
            val familyRootId = familyRootUuid.toString()
            val familyRevision = stableId("ti12-scale-family-revision")
            database.withTransaction {
                database.todos().insert(
                    TodoEntity(
                        id = familyRootId,
                        title = "Scale family root",
                        note = null,
                        dueDate = null,
                        completedAt = null,
                        createdAt = createdAt,
                        updatedAt = createdAt,
                        revisionToken = stableId("ti12-scale-family-root-revision"),
                        importanceCode = TodoImportance.HIGH.code,
                        familyRevisionToken = familyRevision,
                    ),
                )
                repeat(FAMILY_CHILDREN) { index ->
                    val dueDate = when (index % 3) {
                        0 -> today.toString()
                        1 -> today.minusDays(1).toString()
                        else -> today.plusDays(1).toString()
                    }
                    database.todos().insert(
                        TodoEntity(
                            id = stableId("ti12-scale-family-child-$index"),
                            title = "Scale family child $index",
                            note = null,
                            dueDate = dueDate,
                            completedAt = if (index % 10 == 0) createdAt + 1_000 else null,
                            createdAt = createdAt + index + 1,
                            updatedAt = createdAt + index + 1,
                            revisionToken = stableId("ti12-scale-family-child-revision-$index"),
                            importanceCode = when (index % 4) {
                                0 -> TodoImportance.HIGH.code
                                1 -> TodoImportance.MEDIUM.code
                                2 -> TodoImportance.LOW.code
                                else -> TodoImportance.NONE.code
                            },
                            parentId = familyRootId,
                            siblingPosition = index.toLong(),
                            familyRevisionToken = familyRevision,
                        ),
                    )
                }
                repeat(STANDALONE_ROOTS) { index ->
                    val dueDate = when (index % 3) {
                        0 -> today.toString()
                        1 -> today.minusDays(1).toString()
                        else -> today.plusDays(1).toString()
                    }
                    val taskId = stableId("ti12-scale-standalone-$index")
                    database.todos().insert(
                        TodoEntity(
                            id = taskId,
                            title = "Scale standalone $index",
                            note = null,
                            dueDate = dueDate,
                            completedAt = if (index % 25 == 0) createdAt + 2_000 else null,
                            createdAt = createdAt + FAMILY_CHILDREN + index + 1,
                            updatedAt = createdAt + FAMILY_CHILDREN + index + 1,
                            revisionToken = stableId("ti12-scale-standalone-revision-$index"),
                            importanceCode = when (index % 4) {
                                0 -> TodoImportance.HIGH.code
                                1 -> TodoImportance.MEDIUM.code
                                2 -> TodoImportance.LOW.code
                                else -> TodoImportance.NONE.code
                            },
                            familyRevisionToken = stableId("ti12-scale-standalone-family-$index"),
                        ),
                    )
                }
            }

            val repository = RoomTodoRepository(
                database,
                Clock.fixed(Instant.parse("2026-11-15T08:00:00Z"), ZoneOffset.UTC),
            )
            val expectedOpenRoots = 1 + (0 until STANDALONE_ROOTS).count { it % 25 != 0 }
            val expectedOpenHighMatches =
                1 +
                    (0 until STANDALONE_ROOTS).count { it % 25 != 0 && it % 4 == 0 } +
                    (0 until FAMILY_CHILDREN).count { it % 10 != 0 && it % 4 == 0 }
            val expectedTodayMatches =
                (0 until STANDALONE_ROOTS).count { it % 25 != 0 && it % 3 != 2 } +
                    (0 until FAMILY_CHILDREN).count { it % 10 != 0 && it % 3 != 2 }

            val roots = repository.observeRows(TodoListQuery(completed = false), limit = 50).first()
            val highMatches = repository.observeRows(
                TodoListQuery(completed = false, importance = TodoImportance.HIGH, sort = TodoSort.IMPORTANCE),
                limit = 50,
            ).first()
            val childPrefix = repository.observeChildren(familyRootUuid, limit = 50).first()
            val todayPrefix = repository.observeTodayRows(today, limit = 50).first()
            assertEquals(10_000, FAMILY_CHILDREN + STANDALONE_ROOTS + 1)
            assertEquals(expectedOpenRoots, roots.total)
            assertEquals(50, roots.items.size)
            assertEquals(expectedOpenHighMatches, highMatches.total)
            assertEquals(50, highMatches.items.size)
            assertTrue(highMatches.items.all { it.task.importance == TodoImportance.HIGH })
            assertEquals(FAMILY_CHILDREN, childPrefix.total)
            assertEquals(50, childPrefix.items.size)
            assertEquals(100, childPrefix.familyProgress?.completed)
            assertEquals(FAMILY_CHILDREN, childPrefix.familyProgress?.total)
            assertEquals(expectedTodayMatches, todayPrefix.total)
            assertEquals(50, todayPrefix.items.size)
            assertTrue(todayPrefix.items.all { it.task.dueDate?.isAfter(today) != true })

            val childSearch = repository.searchOpenRows("Scale family child 997", offset = 0, limit = 10)
            assertEquals(1, childSearch.total)
            assertEquals(familyRootUuid, childSearch.items.single().parent?.id)

            repeat(FAMILY_WARMUPS) {
                repository.observeRows(TodoListQuery(completed = false), limit = 50).first()
                repository.observeChildren(familyRootUuid, limit = 50).first()
                repository.observeTodayRows(today, limit = 50).first()
            }
            val familySamples = measureSamples(FAMILY_SAMPLES) {
                val page = repository.observeChildren(familyRootUuid, limit = 50).first()
                assertEquals(FAMILY_CHILDREN, page.total)
                assertEquals(50, page.items.size)
                assertEquals(FAMILY_CHILDREN, page.familyProgress?.total)
            }
            val matchingSamples = measureSamples(FAMILY_SAMPLES) {
                val page = repository.observeRows(
                    TodoListQuery(completed = false, importance = TodoImportance.HIGH, sort = TodoSort.IMPORTANCE),
                    limit = 50,
                ).first()
                assertEquals(expectedOpenHighMatches, page.total)
                assertEquals(50, page.items.size)
            }
            val todaySamples = measureSamples(FAMILY_SAMPLES) {
                val page = repository.observeTodayRows(today, limit = 50).first()
                assertEquals(expectedTodayMatches, page.total)
                assertEquals(50, page.items.size)
            }
            android.util.Log.i(
                "TodoFamilyScaleAcceptance",
                "records=10000 family_children=$FAMILY_CHILDREN api=${Build.VERSION.SDK_INT} device=${Build.MODEL}; " +
                    "children_ms_p95=${percentile(familySamples, 0.95)} " +
                    "high_matches_ms_p95=${percentile(matchingSamples, 0.95)} " +
                    "today_ms_p95=${percentile(todaySamples, 0.95)}",
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun stableId(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray()).toString()

    private suspend fun measureSamples(count: Int, read: suspend () -> Unit): List<Double> =
        (0 until count).map {
            val started = SystemClock.elapsedRealtimeNanos()
            read()
            (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        }.sorted()

    private fun percentile(sortedSamples: List<Double>, percentile: Double): Double =
        sortedSamples[(kotlin.math.ceil(percentile * sortedSamples.size).toInt() - 1).coerceAtLeast(0)]

    private companion object {
        const val TASK_COUNT = 10_000
        const val WARM_UPS = 5
        const val SAMPLE_COUNT = 40
        const val FAMILY_CHILDREN = 1_000
        const val STANDALONE_ROOTS = 8_999
        const val FAMILY_WARMUPS = 5
        const val FAMILY_SAMPLES = 30
    }
}
