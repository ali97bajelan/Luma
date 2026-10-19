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
    }
}
