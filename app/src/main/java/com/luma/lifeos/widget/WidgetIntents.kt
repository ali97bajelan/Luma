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
    private const val URI_SCHEME = "luma-widget"
    private const val URI_AUTHORITY = "navigate"

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
                    WidgetDestination.NewTask -> appendPath("new-task")
                }
            }
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
