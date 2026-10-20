package com.luma.lifeos.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.luma.lifeos.MainActivity
import java.time.LocalDate
import java.util.UUID

object WidgetIntents {
    const val ACTION_NAVIGATION = "com.luma.lifeos.widget.action.NAVIGATE"
    const val ACTION_REFRESH = "com.luma.lifeos.widget.action.REFRESH"
    const val ACTION_MUTATION = "com.luma.lifeos.widget.action.MUTATION"
    const val ACTION_DISMISS_FEEDBACK = "com.luma.lifeos.widget.action.DISMISS_FEEDBACK"
    private const val URI_SCHEME = "luma-widget"
    private const val URI_AUTHORITY = "navigate"
    private const val ACTION_AUTHORITY = "action"

    fun navigation(context: Context, widgetId: Int, destination: WidgetDestination): PendingIntent {
        require(widgetId > 0) { "A live widget ID is required" }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_NAVIGATION
            data = destinationUri(widgetId, destination)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The configuration Activity validates the ID against the live provider before reading or saving. */
    fun configuration(context: Context, widgetId: Int): PendingIntent {
        require(widgetId > 0)
        val intent = Intent(context, WidgetConfigurationActivity::class.java).apply {
            data = Uri.parse("luma-widget-config://instance/$widgetId")
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun refresh(context: Context, widgetId: Int): PendingIntent {
        require(widgetId > 0) { "A live widget ID is required" }
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = ACTION_REFRESH
            data = actionUri("refresh", widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The PendingIntent carries only the live widget ID and opaque persisted command token. */
    fun mutation(context: Context, widgetId: Int, token: UUID): PendingIntent {
        require(widgetId > 0) { "A live widget ID is required" }
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = ACTION_MUTATION
            data = actionUri("mutation", widgetId, token.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun dismissFeedback(context: Context, widgetId: Int): PendingIntent {
        require(widgetId > 0) { "A live widget ID is required" }
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = ACTION_DISMISS_FEEDBACK
            data = actionUri("dismiss-feedback", widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal sealed interface Action {
        val widgetId: Int

        data class Refresh(override val widgetId: Int) : Action
        data class Mutation(override val widgetId: Int, val token: UUID) : Action
        data class DismissFeedback(override val widgetId: Int) : Action
    }

    /** Decode only explicit receiver actions with canonical identifier paths. */
    internal fun decodeAction(intent: Intent): Action? {
        if (intent.component?.className != WidgetActionReceiver::class.java.name) return null
        val uri = intent.data ?: return null
        if (uri.scheme != URI_SCHEME || uri.authority != ACTION_AUTHORITY ||
            uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null
        ) return null
        val parts = uri.pathSegments
        if (parts.size < 2 || parts[1].toIntOrNull()?.takeIf { it > 0 }?.toString() != parts[1]) return null
        val widgetId = parts[1].toInt()
        val action = when (intent.action) {
            ACTION_REFRESH -> if (parts.size == 2 && parts[0] == "refresh") Action.Refresh(widgetId) else null
            ACTION_MUTATION -> if (parts.size == 3 && parts[0] == "mutation") {
                val token = parts[2].canonicalUuid() ?: return null
                Action.Mutation(widgetId, token)
            } else null
            ACTION_DISMISS_FEEDBACK -> if (parts.size == 2 && parts[0] == "dismiss-feedback") {
                Action.DismissFeedback(widgetId)
            } else null
            else -> null
        } ?: return null
        return action.takeIf {
            when (it) {
                is Action.Refresh -> actionUri("refresh", widgetId).toString() == uri.toString()
                is Action.Mutation -> actionUri("mutation", widgetId, it.token.toString()).toString() == uri.toString()
                is Action.DismissFeedback -> actionUri("dismiss-feedback", widgetId).toString() == uri.toString()
            }
        }
    }

    /** Decode only the explicit MainActivity navigation allowlist; extras are never navigation input. */
    fun decode(intent: Intent): WidgetDestination? {
        if (intent.action != ACTION_NAVIGATION ||
            intent.component?.className != MainActivity::class.java.name
        ) return null

        val uri = intent.data ?: return null
        if (uri.scheme != URI_SCHEME || uri.authority != URI_AUTHORITY ||
            uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null
        ) return null

        val parts = uri.pathSegments
        if (parts.size < 3 || parts[0] != "navigation") return null
        val widgetId = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        if (widgetId.toString() != parts[1]) return null

        val destination = when (parts[2]) {
            "today" -> if (parts.size == 3) WidgetDestination.Today else null
            "quick-capture" -> if (parts.size == 3) WidgetDestination.QuickCapture else null
            "capture" -> if (parts.size != 4) null else when (parts[3]) {
                "MOOD" -> WidgetDestination.Capture(CaptureKind.MOOD)
                "MEMORY" -> WidgetDestination.Capture(CaptureKind.MEMORY)
                "JOURNAL" -> WidgetDestination.Capture(CaptureKind.JOURNAL)
                else -> null
            }
            "habits" -> if (parts.size == 3) WidgetDestination.Habits else null
            "habit" -> if (parts.size != 5) null else {
                val id = parts[3].canonicalUuid() ?: return null
                val date = parts[4].canonicalDate() ?: return null
                WidgetDestination.HabitDetail(id, date)
            }
            "tasks" -> if (parts.size == 3) WidgetDestination.Tasks else null
            "task" -> if (parts.size != 4) null else {
                val id = parts[3].canonicalUuid() ?: return null
                WidgetDestination.Task(id)
            }
            "new-habit" -> if (parts.size == 3) WidgetDestination.NewHabit else null
            "new-task" -> if (parts.size == 3) WidgetDestination.NewTask else null
            else -> null
        }
        if (destination == null) return null

        // This also rejects noncanonical UUID/date spellings and escaped alternate paths.
        return destination.takeIf { destinationUri(widgetId, it).toString() == uri.toString() }
    }

    private fun destinationUri(widgetId: Int, destination: WidgetDestination): Uri =
        Uri.Builder()
            .scheme(URI_SCHEME)
            .authority(URI_AUTHORITY)
            .appendPath("navigation")
            .appendPath(widgetId.toString())
            .apply {
                when (destination) {
                    WidgetDestination.Today -> appendPath("today")
                    WidgetDestination.QuickCapture -> appendPath("quick-capture")
                    is WidgetDestination.Capture -> {
                        appendPath("capture")
                        appendPath(destination.kind.name)
                    }
                    WidgetDestination.Habits -> appendPath("habits")
                    is WidgetDestination.HabitDetail -> {
                        appendPath("habit")
                        appendPath(destination.id.toString())
                        appendPath(destination.date.toString())
                    }
                    WidgetDestination.Tasks -> appendPath("tasks")
                    is WidgetDestination.Task -> {
                        appendPath("task")
                        appendPath(destination.id.toString())
                    }
                    WidgetDestination.NewHabit -> appendPath("new-habit")
                    WidgetDestination.NewTask -> appendPath("new-task")
                }
            }
            .build()

    private fun actionUri(action: String, widgetId: Int, token: String? = null): Uri =
        Uri.Builder()
            .scheme(URI_SCHEME)
            .authority(ACTION_AUTHORITY)
            .appendPath(action)
            .appendPath(widgetId.toString())
            .apply { token?.let { appendPath(it) } }
            .build()

    private fun String.canonicalUuid(): UUID? = try {
        UUID.fromString(this).takeIf { it.toString() == this }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun String.canonicalDate(): LocalDate? = try {
        LocalDate.parse(this).takeIf { it.toString() == this }
    } catch (_: Exception) {
        null
    }
}
