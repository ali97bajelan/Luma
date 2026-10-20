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
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWidgetStore(
    private val database: LumaDatabase,
    @Suppress("unused") private val clock: Clock = Clock.systemUTC(),
) : WidgetStore {
    override suspend fun ensureConfigured(instance: WidgetInstance) {
        database.widgets().insertInstanceIfAbsent(
            WidgetInstanceEntity(
                widgetId = instance.id,
                kind = instance.kind.name,
                showDetails = instance.showDetails,
                feedbackJson = null,
            ),
        )
    }

    override suspend fun configure(instance: WidgetInstance) = database.withTransaction {
        val dao = database.widgets()
        val existing = dao.instance(instance.id)
        val configurationChanged = existing != null &&
            (existing.kind != instance.kind.name || existing.showDetails != instance.showDetails)
        if (configurationChanged) {
            dao.deleteCommands(instance.id)
        }
        val feedback = existing?.takeIf {
            it.kind == instance.kind.name && it.showDetails == instance.showDetails
        }?.feedbackJson
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
            dao.deleteCommandsBefore(now.minus(7, ChronoUnit.DAYS).toEpochMilli())
            val stored = dao.instance(widgetId) ?: return@withTransaction null
            val kind = WidgetKind.valueOf(stored.kind)
            val instance = stored.toDomain(kind)
            val feedback = stored.feedbackJson?.let(::decodeWidgetActionResult)
            val issuer = WidgetCommandIssuer(dao, widgetId, now.toEpochMilli())

            val snapshot = when (kind) {
                WidgetKind.HABITS -> {
                    val trackingZone = database.habits().settings()?.trackingZoneId
                        ?.let(ZoneId::of) ?: deviceZone
                    val date = LocalDate.ofInstant(now, trackingZone)
                    val summary = database.habits().widgetSummary(date.toString())
                    val rows = if (instance.showDetails && rowLimit > 0) {
                        database.habits().widgetDayRows(date.toString(), rowLimit).map { row ->
                            val token = if (row.target != null && row.scheduleId != null &&
                                row.scheduleState == "ACTIVE" && !row.skipRemaining && row.count < row.target
                            ) {
                                issuer.tokenFor(
                                    WidgetCommandPayload.HabitLog(
                                        habitId = UUID.fromString(row.habitId),
                                        date = date,
                                        zone = trackingZone,
                                        scheduleId = UUID.fromString(row.scheduleId),
                                        effectiveFrom = LocalDate.parse(requireNotNull(row.effectiveFrom)),
                                        target = row.target,
                                        weekdayMask = requireNotNull(row.weekdayMask),
                                        scheduleState = requireNotNull(row.scheduleState),
                                        skipRemaining = row.skipRemaining,
                                        expectedCount = row.count,
                                        logId = UUID.randomUUID(),
                                    ),
                                )
                            } else {
                                null
                            }
                            WidgetRow.Habit(
                                id = UUID.fromString(row.habitId),
                                title = row.name,
                                count = row.count,
                                target = requireNotNull(row.target),
                                skipped = row.skipRemaining,
                                actionToken = token,
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
                            val token = issuer.tokenFor(
                                WidgetCommandPayload.TaskComplete(
                                    taskId = UUID.fromString(row.taskId),
                                    date = date,
                                    expectedRevision = UUID.fromString(row.revisionToken),
                                    completedRevision = UUID.randomUUID(),
                                ),
                            )
                            WidgetRow.Task(
                                id = UUID.fromString(row.taskId),
                                title = row.title,
                                dueDate = LocalDate.parse(requireNotNull(row.dueDate)),
                                actionToken = token,
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
            issuer.rejectSuperseded()
            snapshot.copy(feedback = feedbackFor(dao, widgetId))
        }
    }

    override suspend fun execute(
        widgetId: Int,
        token: UUID,
        now: Instant,
        deviceZone: ZoneId,
    ): WidgetActionResult {
        try {
            return database.withTransaction {
                val dao = database.widgets()
                dao.deleteCommandsBefore(now.minus(7, ChronoUnit.DAYS).toEpochMilli())
                val command = dao.command(token.toString())
                    ?: return@withTransaction WidgetActionResult(WidgetOutcome.INVALID)
                WidgetCommandExecutor(database).execute(widgetId, command, now, deviceZone)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val result = WidgetActionResult(WidgetOutcome.FAILED, retryToken = token)
            try {
                database.withTransaction {
                    val dao = database.widgets()
                    val command = dao.command(token.toString()) ?: return@withTransaction
                    if (command.widgetId != widgetId || command.status != WidgetCommandExecutor.READY) {
                        return@withTransaction
                    }
                    val instance = dao.instance(widgetId) ?: return@withTransaction
                    val previous = instance.feedbackJson?.let(::decodeWidgetActionResult)
                    previous?.retryToken?.takeIf { it != token }?.let { previousRetry ->
                        dao.finishReadyCommand(
                            previousRetry.toString(), WidgetCommandExecutor.REJECTED,
                            encodeWidgetActionResult(WidgetActionResult(WidgetOutcome.STALE)),
                        )
                    }
                    val retained = result.copy(undoToken = previous?.undoToken?.takeIf { it != token })
                    dao.updateFeedback(widgetId, encodeWidgetActionResult(retained))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The original command remains READY; a later delivery can retry it.
            }
            return result
        }
    }

    override suspend fun dismissFeedback(widgetId: Int) = database.withTransaction {
        val dao = database.widgets()
        val feedback = dao.instance(widgetId)?.feedbackJson?.let(::decodeWidgetActionResult)
        listOfNotNull(feedback?.retryToken, feedback?.undoToken).distinct().forEach { token ->
            dao.finishReadyCommand(
                token.toString(), WidgetCommandExecutor.REJECTED,
                encodeWidgetActionResult(WidgetActionResult(WidgetOutcome.INVALID)),
            )
        }
        dao.clearFeedback(widgetId)
        Unit
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

    private suspend fun feedbackFor(dao: WidgetDao, widgetId: Int): WidgetActionResult? {
        val instance = dao.instance(widgetId) ?: return null
        val previous = instance.feedbackJson?.let(::decodeWidgetActionResult) ?: return null
        suspend fun readyToken(token: UUID?) = token?.takeIf {
            dao.command(it.toString())?.let { command ->
                command.widgetId == widgetId && command.status == WidgetCommandExecutor.READY
            } == true
        }
        val current = previous.copy(
            retryToken = readyToken(previous.retryToken),
            undoToken = readyToken(previous.undoToken),
        )
        if (current != previous) dao.updateFeedback(widgetId, encodeWidgetActionResult(current))
        return current
    }

    private inner class WidgetCommandIssuer(
        private val dao: WidgetDao,
        private val widgetId: Int,
        private val nowMillis: Long,
    ) {
        private val current = mutableListOf<WidgetCommandEntity>()
        private val retainedTokens = mutableSetOf<String>()
        private val readyByFingerprint = mutableMapOf<String, MutableList<WidgetCommandEntity>>()
        private var loaded = false

        suspend fun tokenFor(payload: WidgetCommandPayload): UUID {
            if (!loaded) {
                loaded = true
                dao.readyCommands(widgetId).also { commands ->
                    current += commands
                    commands.forEach { command ->
                        readyByFingerprint.getOrPut(command.fingerprint) { mutableListOf() }.add(command)
                    }
                }
            }
            val candidates = readyByFingerprint[payload.fingerprint()].orEmpty()
            val matching = candidates.filter { command ->
                command.action == payload.action &&
                    WidgetCommandPayload.decode(command.action, command.payloadJson)
                        ?.fingerprint() == command.fingerprint
            }
            val reusable = matching.firstOrNull()
            matching.drop(1).forEach { duplicate ->
                dao.finishReadyCommand(
                    duplicate.token, WidgetCommandExecutor.REJECTED,
                    encodeWidgetActionResult(WidgetActionResult(WidgetOutcome.STALE)),
                )
            }
            val command = reusable ?: WidgetCommandEntity(
                token = UUID.randomUUID().toString(),
                widgetId = widgetId,
                action = payload.action,
                payloadJson = payload.encode(),
                fingerprint = payload.fingerprint(),
                status = WidgetCommandExecutor.READY,
                outcomeJson = null,
                createdAt = nowMillis,
            ).also { dao.insertCommand(it) }
            retainedTokens += command.token
            return UUID.fromString(command.token)
        }

        suspend fun rejectSuperseded() {
            (current + dao.readyCommands(widgetId)).distinctBy(WidgetCommandEntity::token)
                .filter { it.token !in retainedTokens && it.status == WidgetCommandExecutor.READY }
                .forEach { command ->
                    dao.finishReadyCommand(
                        command.token, WidgetCommandExecutor.REJECTED,
                        encodeWidgetActionResult(WidgetActionResult(WidgetOutcome.STALE)),
                    )
                }
        }
    }
}
