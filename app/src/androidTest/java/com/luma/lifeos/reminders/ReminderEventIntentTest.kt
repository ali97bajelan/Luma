package com.luma.lifeos.reminders

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ReminderEventIntentTest {
    @Test fun eventActionsRoundTripSeparatelyFromQualifiedBroadcastActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cases = listOf(
            KIND_HABIT to "DELIVER",
            KIND_HABIT to "LOG_ONE",
            KIND_HABIT to "SNOOZE",
            KIND_TASK to "DELIVER",
            KIND_TASK to "COMPLETE",
            KIND_TASK to "SNOOZE",
        )

        cases.forEach { (kind, action) ->
            val event = event(kind, action)
            val intent = event.toIntent(context)

            assertEquals("com.luma.lifeos.reminders.$action", intent.action)
            assertEquals(action, intent.getStringExtra(EXTRA_ACTION))
            assertEquals(event, reminderEventFrom(intent))
            assertEquals(event, reminderEventFrom(event.toData()))
        }
    }

    @Test fun qualifiedIntentActionInEventExtraIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = event(KIND_TASK, "DELIVER").toIntent(context)
            .putExtra(EXTRA_ACTION, "com.luma.lifeos.reminders.DELIVER")

        assertNull(reminderEventFrom(intent))
    }

    private fun event(kind: String, action: String) = ReminderEvent(
        kind = kind,
        action = action,
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        revision = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        sourceRevision = if (kind == KIND_TASK) UUID.fromString("00000000-0000-0000-0000-000000000003") else null,
        date = LocalDate.of(2026, 10, 28),
        key = "$kind:09:00",
        scheduledAt = Instant.parse("2026-10-28T06:00:00Z"),
        expiresAt = Instant.parse("2026-10-28T06:10:00Z"),
        minute = 540,
        token = UUID.fromString("00000000-0000-0000-0000-000000000004"),
    )
}
