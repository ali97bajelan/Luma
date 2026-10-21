package com.luma.lifeos.data.widget

import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.habit.HabitDayRow
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.widget.WidgetActionResult
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoMissingException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Executes only inside the caller's Room transaction. */
internal class WidgetCommandExecutor(private val database: LumaDatabase) {
    private val widgets get() = database.widgets()

    suspend fun execute(
        widgetId: Int,
        command: WidgetCommandEntity,
        now: Instant,
        deviceZone: ZoneId,
    ): WidgetActionResult {
        if (command.widgetId != widgetId) return result(WidgetOutcome.INVALID)
        if (command.status != READY) return command.resultOrInvalid()

        val instance = widgets.instance(widgetId) ?: return reject(command, WidgetOutcome.INVALID)

        val payload = WidgetCommandPayload.decode(command.action, command.payloadJson)
            ?: return reject(command, WidgetOutcome.INVALID)
        if (payload.fingerprint() != command.fingerprint) {
            return reject(command, WidgetOutcome.INVALID)
        }
        if ((payload is WidgetCommandPayload.HabitUndo || payload is WidgetCommandPayload.TaskUndo) &&
            instance.feedbackJson?.let(::decodeWidgetActionResult)?.undoToken?.toString() != command.token
        ) {
            return reject(command, WidgetOutcome.INVALID)
        }

        return when (payload) {
            is WidgetCommandPayload.HabitLog -> {
                if (instance.kind != WidgetKind.HABITS.name) reject(command, WidgetOutcome.INVALID)
                else habitLog(widgetId, command, payload, now)
            }
            is WidgetCommandPayload.TaskComplete -> {
                if (instance.kind != WidgetKind.TASKS.name) reject(command, WidgetOutcome.INVALID)
                else taskComplete(widgetId, command, payload, now, deviceZone)
            }
            is WidgetCommandPayload.HabitUndo -> {
                if (instance.kind != WidgetKind.HABITS.name) reject(command, WidgetOutcome.INVALID)
                else habitUndo(widgetId, command, payload)
            }
            is WidgetCommandPayload.TaskUndo -> {
                if (instance.kind != WidgetKind.TASKS.name) reject(command, WidgetOutcome.INVALID)
                else taskUndo(widgetId, command, payload, now)
            }
        }
    }

    private suspend fun habitLog(
        widgetId: Int,
        command: WidgetCommandEntity,
        payload: WidgetCommandPayload.HabitLog,
        now: Instant,
    ): WidgetActionResult {
        val settings = database.habits().settings() ?: return reject(command, WidgetOutcome.STALE)
        val trackingZone = runCatching { ZoneId.of(settings.trackingZoneId) }.getOrNull()
            ?: return reject(command, WidgetOutcome.STALE)
        if (payload.zone != trackingZone || payload.date != now.atZone(trackingZone).toLocalDate()) {
            return reject(command, WidgetOutcome.STALE)
        }
        val row = database.habits().widgetHabitState(payload.habitId.toString(), payload.date.toString())
            ?: return reject(command, WidgetOutcome.MISSING)
        if (!row.matches(payload)) return reject(command, WidgetOutcome.STALE)

        val acceptedAt = Instant.ofEpochMilli(now.toEpochMilli())
        val saved = RoomHabitRepository(database, fixedClock(acceptedAt)).log(
            payload.logId,
            HabitLogInput(payload.habitId, payload.date, payload.zone, acceptedAt),
        )
        val undoPayload = WidgetCommandPayload.HabitUndo(
            logId = saved.id,
            habitId = saved.habitId,
            date = saved.date,
            zone = saved.zone,
            occurredAtMillis = saved.occurredAt?.toEpochMilli(),
            recordedAtMillis = saved.recordedAt.toEpochMilli(),
            updatedAtMillis = saved.updatedAt.toEpochMilli(),
        )
        return commitWithUndo(widgetId, command, undoPayload, now)
    }

    private suspend fun taskComplete(
        widgetId: Int,
        command: WidgetCommandEntity,
        payload: WidgetCommandPayload.TaskComplete,
        now: Instant,
        deviceZone: ZoneId,
    ): WidgetActionResult {
        val actualDate = now.atZone(deviceZone).toLocalDate()
        if (payload.date != actualDate) return reject(command, WidgetOutcome.STALE)
        val dao = database.todos()
        val entity = dao.find(payload.taskId.toString()) ?: return reject(command, WidgetOutcome.MISSING)
        if (entity.completedAt != null) return reject(command, WidgetOutcome.STALE)
        if (entity.revisionToken != payload.expectedRevision.toString()) {
            return reject(command, WidgetOutcome.CONFLICT)
        }
        val dueDate = entity.dueDate?.let(LocalDate::parse)
        if (dueDate == null || dueDate.isAfter(actualDate)) return reject(command, WidgetOutcome.STALE)

        try {
            RoomTodoRepository(database, fixedClock(now)).setCompleted(
                id = payload.taskId,
                expectedRevision = payload.expectedRevision,
                nextRevision = payload.completedRevision,
                completed = true,
            )
            return commitWithUndo(
                widgetId,
                command,
                WidgetCommandPayload.TaskUndo(
                    taskId = payload.taskId,
                    completedRevision = payload.completedRevision,
                    reopenedRevision = UUID.randomUUID(),
                ),
                now,
            )
        } catch (_: TodoMissingException) {
            return reject(command, WidgetOutcome.MISSING)
        } catch (_: TodoConflictException) {
            return reject(command, WidgetOutcome.CONFLICT)
        }
    }

    private suspend fun habitUndo(
        widgetId: Int,
        command: WidgetCommandEntity,
        payload: WidgetCommandPayload.HabitUndo,
    ): WidgetActionResult {
        val current = database.habits().log(payload.logId.toString())
        if (current == null) return commitWithoutUndo(widgetId, command, WidgetOutcome.UNDONE)
        if (!current.matches(payload)) {
            return reject(command, WidgetOutcome.CONFLICT, updateFeedback = true)
        }
        RoomHabitRepository(database).deleteLog(payload.logId)
        return commitWithoutUndo(widgetId, command, WidgetOutcome.UNDONE)
    }

    private suspend fun taskUndo(
        widgetId: Int,
        command: WidgetCommandEntity,
        payload: WidgetCommandPayload.TaskUndo,
        now: Instant,
    ): WidgetActionResult {
        val current = database.todos().find(payload.taskId.toString())
            ?: return reject(command, WidgetOutcome.MISSING, updateFeedback = true)
        if (current.revisionToken != payload.completedRevision.toString() || current.completedAt == null) {
            return reject(command, WidgetOutcome.CONFLICT, updateFeedback = true)
        }
        try {
            RoomTodoRepository(database, fixedClock(now)).setCompleted(
                id = payload.taskId,
                expectedRevision = payload.completedRevision,
                nextRevision = payload.reopenedRevision,
                completed = false,
            )
        } catch (_: TodoMissingException) {
            return reject(command, WidgetOutcome.MISSING, updateFeedback = true)
        } catch (_: TodoConflictException) {
            return reject(command, WidgetOutcome.CONFLICT, updateFeedback = true)
        }
        return commitWithoutUndo(widgetId, command, WidgetOutcome.UNDONE)
    }

    private suspend fun commitWithUndo(
        widgetId: Int,
        command: WidgetCommandEntity,
        payload: WidgetCommandPayload,
        now: Instant,
    ): WidgetActionResult {
        replaceFeedback(widgetId, skipToken = command.token)
        val undoToken = UUID.randomUUID()
        widgets.insertCommand(
            WidgetCommandEntity(
                token = undoToken.toString(),
                widgetId = widgetId,
                action = payload.action,
                payloadJson = payload.encode(),
                fingerprint = payload.fingerprint(),
                status = READY,
                outcomeJson = null,
                createdAt = now.toEpochMilli(),
            ),
        )
        val result = WidgetActionResult(WidgetOutcome.SAVED, undoToken = undoToken)
        widgets.updateFeedback(widgetId, encodeWidgetActionResult(result))
        check(widgets.finishReadyCommand(command.token, COMMITTED, encodeWidgetActionResult(result)) == 1) {
            "Widget command was no longer ready after applying its mutation"
        }
        return result
    }

    private suspend fun commitWithoutUndo(
        widgetId: Int,
        command: WidgetCommandEntity,
        outcome: WidgetOutcome,
    ): WidgetActionResult {
        replaceFeedback(widgetId, skipToken = command.token)
        val result = WidgetActionResult(outcome)
        widgets.updateFeedback(widgetId, encodeWidgetActionResult(result))
        check(widgets.finishReadyCommand(command.token, COMMITTED, encodeWidgetActionResult(result)) == 1) {
            "Widget command was no longer ready after applying its action"
        }
        return result
    }

    private suspend fun reject(
        command: WidgetCommandEntity,
        outcome: WidgetOutcome,
        updateFeedback: Boolean = false,
    ): WidgetActionResult {
        val result = result(outcome)
        check(widgets.finishReadyCommand(command.token, REJECTED, encodeWidgetActionResult(result)) == 1) {
            "Widget command was no longer ready while recording its rejection"
        }
        if (updateFeedback) {
            replaceFeedback(command.widgetId, skipToken = command.token)
            widgets.updateFeedback(command.widgetId, encodeWidgetActionResult(result))
        }
        return result
    }

    private suspend fun replaceFeedback(widgetId: Int, skipToken: String) {
        val previous = widgets.instance(widgetId)?.feedbackJson?.let(::decodeWidgetActionResult)
            ?: return
        listOfNotNull(previous.retryToken, previous.undoToken).distinct().forEach { token ->
            if (token.toString() != skipToken) {
                widgets.finishReadyCommand(
                    token.toString(), REJECTED,
                    encodeWidgetActionResult(result(WidgetOutcome.STALE)),
                )
            }
        }
    }

    private fun result(outcome: WidgetOutcome) = WidgetActionResult(outcome)

    private fun fixedClock(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

    private fun HabitDayRow.matches(payload: WidgetCommandPayload.HabitLog): Boolean =
        habitId == payload.habitId.toString() && startDate <= payload.date.toString() &&
            scheduleId == payload.scheduleId.toString() && effectiveFrom == payload.effectiveFrom.toString() &&
            target == payload.target && weekdayMask == payload.weekdayMask &&
            scheduleState == payload.scheduleState && scheduleState == "ACTIVE" &&
            (weekdayMask!! and (1 shl (payload.date.dayOfWeek.value - 1))) != 0 &&
            skipRemaining == payload.skipRemaining && !payload.skipRemaining &&
            count == payload.expectedCount && count < target!!

    private fun com.luma.lifeos.data.habit.HabitLogEntity.matches(payload: WidgetCommandPayload.HabitUndo): Boolean =
        id == payload.logId.toString() && habitId == payload.habitId.toString() &&
            habitDate == payload.date.toString() && zoneId == payload.zone.id &&
            occurredAt == payload.occurredAtMillis && recordedAt == payload.recordedAtMillis &&
            updatedAt == payload.updatedAtMillis

    companion object {
        const val READY = "READY"
        const val COMMITTED = "COMMITTED"
        const val REJECTED = "REJECTED"
    }
}
