package com.luma.lifeos.widget

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetActionResult
import com.luma.lifeos.domain.widget.WidgetInstance
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.widget.WidgetRow
import com.luma.lifeos.domain.widget.WidgetSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRendererAcceptanceTest {
    @Test fun reapplyingCompletedTaskSnapshotRemovesOldRowAndKeepsUndoAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 51
        val task = WidgetRow.Task(
            id = UUID.randomUUID(),
            title = "Widget task",
            dueDate = LocalDate.parse("2026-10-20"),
            actionToken = UUID.randomUUID(),
        )
        val before = WidgetSnapshot(
            instance = WidgetInstance(widgetId, WidgetKind.TASKS, showDetails = true),
            date = task.dueDate,
            zone = ZoneOffset.UTC,
            total = 1,
            completed = null,
            hasAnyItems = true,
            rows = listOf(task),
            feedback = null,
            updatedAt = Instant.parse("2026-10-20T08:00:00Z"),
        )
        val initialViews = WidgetRenderer.render(context, widgetId, before, failure = false, options = Bundle())
        val root = initialViews.apply(context, null)
        assertEquals(1, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)

        val completed = before.copy(
            total = 0,
            rows = emptyList(),
            feedback = WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
        )
        val refreshedViews = WidgetRenderer.render(context, widgetId, completed, failure = false, options = Bundle())
        refreshedViews.reapply(context, root)

        assertEquals(0, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)
        assertEquals("Undo", root.findViewById<TextView>(R.id.widget_feedback_primary).text.toString())
        assertEquals(
            context.getString(R.string.widget_task_undo_description),
            root.findViewById<TextView>(R.id.widget_feedback_primary).contentDescription.toString(),
        )
    }
}
