package com.luma.lifeos.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Data
import com.luma.lifeos.MainActivity
import com.luma.lifeos.domain.habit.ReminderSlot
import com.luma.lifeos.domain.focus.FocusInvitationSlot
import com.luma.lifeos.domain.todo.TaskReminderSlot
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal const val EXTRA_KIND = "reminder_kind"
internal const val EXTRA_ACTION = "reminder_action"
internal const val EXTRA_ID = "reminder_id"
internal const val EXTRA_REVISION = "reminder_revision"
internal const val EXTRA_SOURCE_REVISION = "reminder_source_revision"
internal const val EXTRA_DATE = "reminder_date"
internal const val EXTRA_KEY = "reminder_key"
internal const val EXTRA_SCHEDULED = "reminder_scheduled"
internal const val EXTRA_EXPIRES = "reminder_expires"
internal const val EXTRA_MINUTE = "reminder_minute"
internal const val EXTRA_TOKEN = "reminder_token"
internal const val KIND_HABIT = "habit"
internal const val KIND_TASK = "task"
internal const val ACTION_EXTERNAL_REMINDER = "com.luma.lifeos.reminders.ACTION_EXTERNAL"
internal const val ACTION_FOCUS_INVITATION_OPEN = "com.luma.lifeos.focus.invitation.OPEN"
internal const val EXTRA_FOCUS_INVITATION_ACTION = "focus_invitation_action"
internal const val EXTRA_FOCUS_INVITATION_GENERATION = "focus_invitation_generation"
internal const val EXTRA_FOCUS_INVITATION_REVISION = "focus_invitation_revision"
internal const val EXTRA_FOCUS_INVITATION_DATE = "focus_invitation_date"
internal const val EXTRA_FOCUS_INVITATION_DUE_AT = "focus_invitation_due_at"
internal const val EXTRA_FOCUS_INVITATION_TOKEN = "focus_invitation_token"

internal data class ReminderEvent(
    val kind: String,
    val action: String,
    val id: UUID,
    val revision: UUID,
    val sourceRevision: UUID?,
    val date: LocalDate,
    val key: String,
    val scheduledAt: Instant,
    val expiresAt: Instant,
    val minute: Int,
    val token: UUID,
) {
    fun toData() = Data.Builder()
        .putString(EXTRA_KIND, kind).putString(EXTRA_ACTION, action).putString(EXTRA_ID, id.toString())
        .putString(EXTRA_REVISION, revision.toString()).putString(EXTRA_SOURCE_REVISION, sourceRevision?.toString())
        .putString(EXTRA_DATE, date.toString()).putString(EXTRA_KEY, key)
        .putLong(EXTRA_SCHEDULED, scheduledAt.toEpochMilli()).putLong(EXTRA_EXPIRES, expiresAt.toEpochMilli())
        .putInt(EXTRA_MINUTE, minute).putString(EXTRA_TOKEN, token.toString()).build()

    fun toIntent(context: Context): Intent = Intent(context, ReminderActionReceiver::class.java).apply {
        action = "com.luma.lifeos.reminders.${this@ReminderEvent.action}"
        data = identityUri(kind, id, "deliver")
        putExtra(EXTRA_KIND, kind)
        putExtra(EXTRA_ACTION, this@ReminderEvent.action)
        putExtra(EXTRA_ID, id.toString())
        putExtra(EXTRA_REVISION, revision.toString())
        sourceRevision?.let { putExtra(EXTRA_SOURCE_REVISION, it.toString()) }
        putExtra(EXTRA_DATE, date.toString())
        putExtra(EXTRA_KEY, key)
        putExtra(EXTRA_SCHEDULED, scheduledAt.toEpochMilli())
        putExtra(EXTRA_EXPIRES, expiresAt.toEpochMilli())
        putExtra(EXTRA_MINUTE, minute)
        putExtra(EXTRA_TOKEN, token.toString())
    }
}

internal fun ReminderSlot.toEvent(kindAction: String = "DELIVER") = ReminderEvent(
    kind = KIND_HABIT, action = kindAction, id = habitId, revision = revision, sourceRevision = null,
    date = date, key = key, scheduledAt = at, expiresAt = expiresAt,
    minute = key.substringAfter(':').substringBefore(':').toInt(), token = actionToken,
)

internal fun TaskReminderSlot.toEvent(kindAction: String = "DELIVER") = ReminderEvent(
    kind = KIND_TASK, action = kindAction, id = taskId, revision = configRevision, sourceRevision = taskRevision,
    date = dueDate, key = key, scheduledAt = scheduledAt, expiresAt = expiresAt,
    minute = dueMinute, token = actionToken,
)

internal fun ReminderEvent.toHabitSlot() = ReminderSlot(
    habitId = id, date = date, at = scheduledAt, expiresAt = expiresAt, key = key,
    revision = revision, actionToken = token,
)

internal fun ReminderEvent.toTaskSlot() = TaskReminderSlot(
    taskId = id, taskRevision = requireNotNull(sourceRevision), configRevision = revision,
    dueDate = date, dueMinute = minute, scheduledAt = scheduledAt, expiresAt = expiresAt,
    actionToken = token, key = key,
)

internal fun broadcastPendingIntent(context: Context, event: ReminderEvent, operation: String): PendingIntent =
    PendingIntent.getBroadcast(context, stableRequestCode(event.kind, event.id, operation), event.toIntent(context).apply {
        action = "com.luma.lifeos.reminders.$operation"
        data = identityUri(event.kind, event.id, operation)
    }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

internal fun openReminderPendingIntent(context: Context, event: ReminderEvent): PendingIntent =
    PendingIntent.getActivity(context, stableRequestCode(event.kind, event.id, "open"), Intent(context, MainActivity::class.java).apply {
        action = "com.luma.lifeos.reminders.OPEN"
        data = identityUri(event.kind, event.id, "open")
        putExtra(EXTRA_KIND, event.kind)
        putExtra(EXTRA_ID, event.id.toString())
        putExtra(EXTRA_DATE, event.date.toString())
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

internal fun reminderActionPendingIntent(context: Context, event: ReminderEvent): PendingIntent {
    val actionEvent = event.copy(action = event.action)
    val intent = actionEvent.toIntent(context).apply {
        setClass(context, MainActivity::class.java)
        action = ACTION_EXTERNAL_REMINDER
        data = actionIdentityUri(actionEvent)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    return PendingIntent.getActivity(
        context,
        stableRequestCode(event.kind, event.id, event.action),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal fun ReminderEvent.readFrom(intent: Intent): Boolean = runCatching {
    intent.getStringExtra(EXTRA_KIND) == kind && UUID.fromString(intent.getStringExtra(EXTRA_ID)) == id
}.getOrDefault(false)

internal fun reminderEventFrom(data: Data): ReminderEvent? = runCatching {
    ReminderEvent(
        kind = requireNotNull(data.getString(EXTRA_KIND)), action = requireNotNull(data.getString(EXTRA_ACTION)),
        id = UUID.fromString(requireNotNull(data.getString(EXTRA_ID))),
        revision = UUID.fromString(requireNotNull(data.getString(EXTRA_REVISION))),
        sourceRevision = data.getString(EXTRA_SOURCE_REVISION)?.let(UUID::fromString),
        date = LocalDate.parse(requireNotNull(data.getString(EXTRA_DATE))), key = requireNotNull(data.getString(EXTRA_KEY)),
        scheduledAt = Instant.ofEpochMilli(data.getLong(EXTRA_SCHEDULED, 0)),
        expiresAt = Instant.ofEpochMilli(data.getLong(EXTRA_EXPIRES, 0)),
        minute = data.getInt(EXTRA_MINUTE, 0), token = UUID.fromString(requireNotNull(data.getString(EXTRA_TOKEN))),
    ).also { event -> require(event.kind in setOf(KIND_HABIT, KIND_TASK) && event.action in setOf("DELIVER", "LOG_ONE", "COMPLETE", "SNOOZE")) }
}.getOrNull()

internal fun reminderEventFrom(intent: Intent): ReminderEvent? = runCatching {
    reminderEventFrom(Data.Builder()
        .putString(EXTRA_KIND, intent.getStringExtra(EXTRA_KIND))
        .putString(EXTRA_ACTION, intent.getStringExtra(EXTRA_ACTION))
        .putString(EXTRA_ID, intent.getStringExtra(EXTRA_ID))
        .putString(EXTRA_REVISION, intent.getStringExtra(EXTRA_REVISION))
        .putString(EXTRA_SOURCE_REVISION, intent.getStringExtra(EXTRA_SOURCE_REVISION))
        .putString(EXTRA_DATE, intent.getStringExtra(EXTRA_DATE))
        .putString(EXTRA_KEY, intent.getStringExtra(EXTRA_KEY))
        .putLong(EXTRA_SCHEDULED, intent.getLongExtra(EXTRA_SCHEDULED, 0))
        .putLong(EXTRA_EXPIRES, intent.getLongExtra(EXTRA_EXPIRES, 0))
        .putInt(EXTRA_MINUTE, intent.getIntExtra(EXTRA_MINUTE, 0))
        .putString(EXTRA_TOKEN, intent.getStringExtra(EXTRA_TOKEN)).build())
}.getOrNull()

internal fun reminderOpenRequest(intent: Intent): ReminderOpenRequest? = runCatching {
    val kind = requireNotNull(intent.getStringExtra(EXTRA_KIND))
    val id = UUID.fromString(requireNotNull(intent.getStringExtra(EXTRA_ID)))
    val date = LocalDate.parse(requireNotNull(intent.getStringExtra(EXTRA_DATE)))
    require(kind == KIND_HABIT || kind == KIND_TASK)
    ReminderOpenRequest(kind, id, date, "$kind:$id:$date")
}.getOrNull()

internal fun reminderActionRequest(intent: Intent): ReminderActionRequest? {
    if (intent.component?.className != MainActivity::class.java.name || intent.action != ACTION_EXTERNAL_REMINDER) return null
    val event = reminderEventFrom(intent) ?: return null
    if (event.action !in setOf("COMPLETE", "LOG_ONE", "SNOOZE")) return null
    val uri = intent.data ?: return null
    if (uri.scheme != "luma" || uri.authority != "reminder-action" || uri.query != null || uri.fragment != null ||
        uri.userInfo != null || uri.port != -1 || actionIdentityUri(event).toString() != uri.toString()
    ) return null
    return ReminderActionRequest(
        kind = event.kind,
        action = event.action,
        id = event.id,
        revision = event.revision,
        sourceRevision = event.sourceRevision,
        date = event.date,
        key = event.key,
        scheduledAt = event.scheduledAt,
        expiresAt = event.expiresAt,
        minute = event.minute,
        token = event.token,
    )
}

internal fun enqueueReminderAction(context: Context, request: ReminderActionRequest) {
    enqueueReminderEvent(context, request.toEvent())
}

data class ReminderActionRequest internal constructor(
    val kind: String,
    val action: String,
    val id: UUID,
    val revision: UUID,
    val sourceRevision: UUID?,
    val date: LocalDate,
    val key: String,
    val scheduledAt: Instant,
    val expiresAt: Instant,
    val minute: Int,
    val token: UUID,
) {
    internal fun toEvent() = ReminderEvent(
        kind, action, id, revision, sourceRevision, date, key, scheduledAt, expiresAt, minute, token,
    )
}

private fun actionIdentityUri(event: ReminderEvent): Uri = Uri.parse(
    "luma://reminder-action/${event.kind}/${event.id}/${event.action}/${event.token}",
)

data class ReminderOpenRequest(val kind: String, val id: UUID, val date: LocalDate, val token: String)

private fun stableRequestCode(kind: String, id: UUID, operation: String): Int = "$kind:$id:$operation".hashCode()
private fun identityUri(kind: String, id: UUID, operation: String): Uri = Uri.parse("luma://reminder/$kind/$id/$operation")

internal data class FocusInvitationRequest(
    val slot: FocusInvitationSlot,
    val action: String,
    val token: String? = null,
) {
    fun toData(): Data = Data.Builder()
        .putString(EXTRA_FOCUS_INVITATION_ACTION, action)
        .putString(EXTRA_FOCUS_INVITATION_GENERATION, slot.generation.toString())
        .putLong(EXTRA_FOCUS_INVITATION_REVISION, slot.scheduleRevision)
        .putString(EXTRA_FOCUS_INVITATION_DATE, slot.date.toString())
        .putLong(EXTRA_FOCUS_INVITATION_DUE_AT, slot.dueAt.toEpochMilli())
        .putString(EXTRA_FOCUS_INVITATION_TOKEN, token)
        .build()

    fun toIntent(context: Context): Intent {
        val target = when (action) {
            "DELIVER" -> FocusInvitationAlarmReceiver::class.java
            "SNOOZE", "DISABLE" -> FocusInvitationActionReceiver::class.java
            "OPEN" -> MainActivity::class.java
            else -> return Intent()
        }
        return Intent(context, target).apply {
            this.action = if (this@FocusInvitationRequest.action == "OPEN") ACTION_FOCUS_INVITATION_OPEN
                else "com.luma.lifeos.focus.invitation.${this@FocusInvitationRequest.action}"
            data = this@FocusInvitationRequest.identityUri()
            putExtra(EXTRA_FOCUS_INVITATION_ACTION, this@FocusInvitationRequest.action)
            putExtra(EXTRA_FOCUS_INVITATION_GENERATION, slot.generation.toString())
            putExtra(EXTRA_FOCUS_INVITATION_REVISION, slot.scheduleRevision)
            putExtra(EXTRA_FOCUS_INVITATION_DATE, slot.date.toString())
            putExtra(EXTRA_FOCUS_INVITATION_DUE_AT, slot.dueAt.toEpochMilli())
            token?.let { putExtra(EXTRA_FOCUS_INVITATION_TOKEN, it) }
            if (action == "OPEN") {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
    }

    private fun identityUri(): Uri = Uri.parse(
        "luma://focus-invitation/${slot.generation}/${slot.scheduleRevision}/${slot.date}/${slot.dueAt.toEpochMilli()}/$action/${token.orEmpty()}",
    )
}

data class FocusInvitationOpenRequest(
    val slot: FocusInvitationSlot,
    val token: String,
)

internal fun focusInvitationRequestFrom(data: Data): FocusInvitationRequest? = runCatching {
    val action = requireNotNull(data.getString(EXTRA_FOCUS_INVITATION_ACTION))
    val token = data.getString(EXTRA_FOCUS_INVITATION_TOKEN)
    require(action in setOf("DELIVER", "SNOOZE", "DISABLE", "OPEN"))
    require(if (action == "DELIVER") token == null else !token.isNullOrBlank())
    FocusInvitationRequest(
        slot = FocusInvitationSlot(
            generation = UUID.fromString(requireNotNull(data.getString(EXTRA_FOCUS_INVITATION_GENERATION))),
            scheduleRevision = data.getLong(EXTRA_FOCUS_INVITATION_REVISION, -1L),
            date = LocalDate.parse(requireNotNull(data.getString(EXTRA_FOCUS_INVITATION_DATE))),
            dueAt = Instant.ofEpochMilli(data.getLong(EXTRA_FOCUS_INVITATION_DUE_AT, Long.MIN_VALUE)),
        ),
        action = action,
        token = token,
    ).also { require(it.slot.scheduleRevision >= 0 && it.slot.dueAt.toEpochMilli() != Long.MIN_VALUE) }
}.getOrNull()

internal fun focusInvitationRequestFrom(
    intent: Intent,
    expectedComponent: String? = null,
): FocusInvitationRequest? = runCatching {
    if (expectedComponent != null && intent.component?.className != expectedComponent) return null
    val request = focusInvitationRequestFrom(
        Data.Builder()
            .putString(EXTRA_FOCUS_INVITATION_ACTION, intent.getStringExtra(EXTRA_FOCUS_INVITATION_ACTION))
            .putString(EXTRA_FOCUS_INVITATION_GENERATION, intent.getStringExtra(EXTRA_FOCUS_INVITATION_GENERATION))
            .putLong(EXTRA_FOCUS_INVITATION_REVISION, intent.getLongExtra(EXTRA_FOCUS_INVITATION_REVISION, -1L))
            .putString(EXTRA_FOCUS_INVITATION_DATE, intent.getStringExtra(EXTRA_FOCUS_INVITATION_DATE))
            .putLong(EXTRA_FOCUS_INVITATION_DUE_AT, intent.getLongExtra(EXTRA_FOCUS_INVITATION_DUE_AT, Long.MIN_VALUE))
            .putString(EXTRA_FOCUS_INVITATION_TOKEN, intent.getStringExtra(EXTRA_FOCUS_INVITATION_TOKEN))
            .build(),
    ) ?: return null
    val expectedAction = if (request.action == "OPEN") ACTION_FOCUS_INVITATION_OPEN
        else "com.luma.lifeos.focus.invitation.${request.action}"
    if (intent.action != expectedAction || intent.data?.toString() != request.identityUriString()) return null
    request
}.getOrNull()

internal fun focusInvitationOpenRequest(intent: Intent): FocusInvitationOpenRequest? {
    if (intent.component?.className != MainActivity::class.java.name ||
        intent.action != ACTION_FOCUS_INVITATION_OPEN
    ) return null
    val request = focusInvitationRequestFrom(intent) ?: return null
    if (request.action != "OPEN" || request.token.isNullOrBlank()) return null
    return FocusInvitationOpenRequest(request.slot, request.token)
}

private fun FocusInvitationRequest.identityUriString(): String =
    "luma://focus-invitation/${slot.generation}/${slot.scheduleRevision}/${slot.date}/${slot.dueAt.toEpochMilli()}/$action/${token.orEmpty()}"
