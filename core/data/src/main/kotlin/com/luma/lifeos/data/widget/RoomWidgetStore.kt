package com.luma.lifeos.data.widget

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.domain.widget.WidgetActionResult
import com.luma.lifeos.domain.widget.WidgetInstance
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.widget.WidgetRow
import com.luma.lifeos.domain.widget.WidgetSnapshot
import com.luma.lifeos.domain.widget.WidgetStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class RoomWidgetStore(
    private val database: LumaDatabase,
    @Suppress("unused") private val clock: Clock = Clock.systemUTC(),
) : WidgetStore {
    override suspend fun configure(instance: WidgetInstance) = database.withTransaction {
        val dao = database.widgets()
        val existing = dao.instance(instance.id)
        if (existing != null &&
            (existing.kind != instance.kind.name || existing.showDetails != instance.showDetails)
        ) {
            dao.deleteCommands(instance.id)
        }
        val feedback = existing?.takeIf { it.kind == instance.kind.name }?.feedbackJson
        dao.upsertInstance(
            WidgetInstanceEntity(
                widgetId = instance.id,
                kind = instance.kind.name,
                showDetails = instance.showDetails,
                feedbackJson = feedback,
            ),
        )
    }

    override suspend fun snapshot(
        widgetId: Int,
        now: Instant,
        deviceZone: ZoneId,
        rowLimit: Int,
    ): WidgetSnapshot? {
        require(rowLimit in 0..3) { "Widget row limit must be between zero and three" }
        return database.withTransaction {
            val dao = database.widgets()
            val stored = dao.instance(widgetId) ?: return@withTransaction null
            val kind = WidgetKind.valueOf(stored.kind)
            val instance = stored.toDomain(kind)
            val feedback = stored.feedbackJson?.let(::decodeFeedback)

            when (kind) {
                WidgetKind.HABITS -> {
                    val trackingZone = database.habits().settings()?.trackingZoneId
                        ?.let(ZoneId::of) ?: deviceZone
                    val date = LocalDate.ofInstant(now, trackingZone)
                    val summary = database.habits().widgetSummary(date.toString())
                    val rows = if (instance.showDetails && rowLimit > 0) {
                        database.habits().widgetDayRows(date.toString(), rowLimit).map { row ->
                            WidgetRow.Habit(
                                id = UUID.fromString(row.habitId),
                                title = row.name,
                                count = row.count,
                                target = requireNotNull(row.target),
                                skipped = row.skipRemaining,
                                actionToken = null,
                            )
                        }
                    } else {
                        emptyList()
                    }
                    WidgetSnapshot(
                        instance = instance,
                        date = date,
                        zone = trackingZone,
                        total = summary.total,
                        completed = summary.completed,
                        hasAnyItems = summary.hasAnyItems,
                        rows = rows,
                        feedback = feedback,
                        updatedAt = now,
                    )
                }

                WidgetKind.TASKS -> {
                    val date = LocalDate.ofInstant(now, deviceZone)
                    val summary = database.todos().widgetTodaySummary(date.toString())
                    val rows = if (instance.showDetails && rowLimit > 0) {
                        database.todos().widgetTodayRows(date.toString(), rowLimit).map { row ->
                            WidgetRow.Task(
                                id = UUID.fromString(row.taskId),
                                title = row.title,
                                dueDate = LocalDate.parse(requireNotNull(row.dueDate)),
                                actionToken = null,
                            )
                        }
                    } else {
                        emptyList()
                    }
                    WidgetSnapshot(
                        instance = instance,
                        date = date,
                        zone = deviceZone,
                        total = summary.total,
                        completed = null,
                        hasAnyItems = summary.hasAnyItems,
                        rows = rows,
                        feedback = feedback,
                        updatedAt = now,
                    )
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun execute(
        widgetId: Int,
        token: UUID,
        now: Instant,
        deviceZone: ZoneId,
    ): WidgetActionResult = WidgetActionResult(WidgetOutcome.INVALID)

    override suspend fun dismissFeedback(widgetId: Int) {
        database.widgets().clearFeedback(widgetId)
    }

    override suspend fun remove(widgetId: Int) {
        database.widgets().deleteInstance(widgetId)
    }

    override suspend fun remap(oldIds: List<Int>, newInstances: List<WidgetInstance>) {
        require(newInstances.map { it.id }.distinct().size == newInstances.size) {
            "Remapped widget IDs must be unique"
        }
        database.withTransaction {
            val dao = database.widgets()
            (oldIds + newInstances.map { it.id }).distinct().forEach { dao.deleteInstance(it) }
            newInstances.forEach { instance ->
                dao.upsertInstance(
                    WidgetInstanceEntity(
                        widgetId = instance.id,
                        kind = instance.kind.name,
                        showDetails = false,
                        feedbackJson = null,
                    ),
                )
            }
        }
    }

    override fun observeInvalidations(): Flow<Unit> = database.invalidationTracker.createFlow(
        "habit_settings", "habit", "habit_schedule", "habit_log", "habit_day_choice", "todo_task",
    ).map { Unit }

    private fun WidgetInstanceEntity.toDomain(kind: WidgetKind) = WidgetInstance(
        id = widgetId,
        kind = kind,
        showDetails = showDetails,
    )

    private fun decodeFeedback(value: String): WidgetActionResult {
        val json = JSONObject(value)
        return WidgetActionResult(
            outcome = WidgetOutcome.valueOf(json.getString("outcome")),
            retryToken = json.uuidOrNull("retryToken"),
            undoToken = json.uuidOrNull("undoToken"),
        )
    }

    private fun JSONObject.uuidOrNull(name: String): UUID? =
        if (!has(name) || isNull(name)) null else UUID.fromString(getString(name))
}
