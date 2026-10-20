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
        val initialViews = WidgetRenderer.render(context, widgetId, before, failure = false, options = expandedBounds())
        val root = initialViews.apply(context, null)
        assertEquals(1, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)

        val completed = before.copy(
            total = 0,
            rows = emptyList(),
            feedback = WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
        )
        val refreshedViews = WidgetRenderer.render(context, widgetId, completed, failure = false, options = expandedBounds())
        refreshedViews.reapply(context, root)

        assertEquals(0, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)
        assertEquals(context.getString(R.string.widget_feedback_undo), root.findViewById<TextView>(R.id.widget_feedback_primary).text.toString())
        assertEquals(
            context.getString(R.string.widget_task_undo_description),
            root.findViewById<TextView>(R.id.widget_feedback_primary).contentDescription.toString(),
        )
    }

    @Test fun taskReadFailureOffersRefreshAndClearsUnsafeRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = sampleSnapshot()
        val views = WidgetRenderer.render(context, 51, snapshot, failure = true, options = expandedBounds())
        val root = views.apply(context, null)
        assertEquals(context.getString(R.string.widget_data_refresh),
            root.findViewById<TextView>(R.id.widget_data_refresh).text.toString())
        assertEquals(0, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)
    }

    @Test fun retryNamesTaskAndUndoneClearsActionBandOnReapply() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = sampleSnapshot().copy(feedback = WidgetActionResult(
            WidgetOutcome.FAILED, retryToken = UUID.randomUUID()))
        val expanded = Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 500)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 500)
        }
        val root = WidgetRenderer.render(context, 51, snapshot, false, expanded).apply(context, null)
        assertEquals(context.getString(R.string.widget_task_retry_description),
            root.findViewById<TextView>(R.id.widget_feedback_primary).contentDescription.toString())
        WidgetRenderer.render(context, 51, snapshot.copy(feedback = WidgetActionResult(WidgetOutcome.UNDONE)), false, expanded)
            .reapply(context, root)
        assertEquals(android.view.View.GONE,
            root.findViewById<android.view.View>(R.id.widget_feedback_actions).visibility)
    }

    @Test fun hiddenCountIncludesItemsOutsideSnapshotPrefixAndUpdateUsesActualDate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = sampleSnapshot().copy(total = 7, updatedAt = Instant.parse("2026-10-21T08:00:00Z"))
        val root = WidgetRenderer.render(context, 51, snapshot, false, expandedBounds()).apply(context, null)
        assertEquals(context.getString(R.string.widget_view_all_more,
            context.getString(R.string.widget_tasks_view_all), 7),
            root.findViewById<TextView>(R.id.widget_data_open).text.toString())
        val update = root.findViewById<TextView>(R.id.widget_data_updated).text.toString()
        org.junit.Assert.assertTrue(update.contains("21"))
    }

    private fun expandedBounds() = Bundle().apply {
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 600)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 600)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 600)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 600)
    }

    private fun sampleSnapshot() = WidgetSnapshot(
        instance = WidgetInstance(51, WidgetKind.TASKS, showDetails = true),
        date = LocalDate.parse("2026-10-20"), zone = ZoneOffset.UTC,
        total = 1, completed = null, hasAnyItems = true, rows = emptyList(),
        feedback = null, updatedAt = Instant.parse("2026-10-20T08:00:00Z"),
    )

    @Test fun constrainedStateShellsKeepPrimaryRecoveryInsideBounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (scale in listOf(1f, 1.3f, 2f)) {
                val configuration = android.content.res.Configuration(instrumentation.targetContext.resources.configuration)
                    .apply { fontScale = scale }
                val context = instrumentation.targetContext.createConfigurationContext(configuration)
                for ((width, height) in listOf(180 to 160, 578 to 109, 180 to 220)) {
                    for (kind in WidgetKind.entries) {
                        val base = sampleSnapshot().copy(instance = WidgetInstance(51, kind, true),
                            zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                        val states = listOf(null) + WidgetOutcome.entries.map { outcome ->
                            WidgetActionResult(outcome, retryToken = if (outcome == WidgetOutcome.FAILED) UUID.randomUUID() else null,
                                undoToken = if (outcome == WidgetOutcome.SAVED) UUID.randomUUID() else null)
                        }
                        for ((candidate, failed) in states.map { base.copy(feedback = it) to false } +
                            listOf(null to false, null to true, base to true)) {
                            val feedback = candidate?.feedback
                            val options = Bundle().apply {
                                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                            }
                            val root = WidgetRenderer.render(context, 51, candidate, failed, options).apply(context, null)
                            val density = context.resources.displayMetrics.density
                            val w = (width * density).toInt()
                            val h = (height * density).toInt()
                            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
                            root.layout(0, 0, w, h)
                            val primary = root.findViewById<TextView>(R.id.widget_compact_primary)
                            if (primary == null) {
                                // A regular shell is valid at the taller bound if its controls fit.
                                val footer = root.findViewById<TextView>(R.id.widget_data_open)
                                val rect = android.graphics.Rect()
                                footer.getDrawingRect(rect)
                                (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(footer, rect)
                                org.junit.Assert.assertTrue("Regular footer clipped $kind $scale $width x $height $feedback: $rect / $h",
                                    rect.bottom <= h && footer.height >= 48 * density - 1)
                                continue
                            }
                            val message = root.findViewById<TextView>(R.id.widget_compact_message)
                            org.junit.Assert.assertTrue("Message clipped $kind $scale $width x $height $feedback: ${message.text} ${message.layout.height} / ${message.height}; width=${message.width / density}, measured=${WidgetRenderer.measureTextHeight(context, message.text.toString(), message.width, 14f)}",
                                message.layout.height <= message.height - message.paddingTop - message.paddingBottom)
                            val bounds = android.graphics.Rect()
                            primary.getDrawingRect(bounds)
                            (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(primary, bounds)
                            org.junit.Assert.assertTrue("Primary clipped $kind $scale $width x $height $feedback: $bounds / $w x $h",
                                bounds.top >= 0 && bounds.left >= 0 && bounds.bottom <= h && bounds.right <= w)
                            org.junit.Assert.assertTrue("Small primary $kind $scale $width x $height $feedback: ${primary.height} / ${48 * density}", primary.height >= 48 * density - 1)
                        }
                    }
                }
            }
        }
    }

    @Test fun captureKeepsThreeActionsAtLargeTextWhenAnyArrangementFits() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (tag in listOf("en", "fr", "fa")) {
                val locale = java.util.Locale.forLanguageTag(tag)
                val config = android.content.res.Configuration(instrumentation.targetContext.resources.configuration).apply {
                    fontScale = 2f
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                for ((width, height) in listOf(578 to 109, 350 to 240, 220 to 400)) {
                    val root = QuickCaptureWidgetProvider().viewsForSize(context, context, 51, width, height).apply(context, null)
                    for (id in listOf(R.id.widget_capture_mood, R.id.widget_capture_memory, R.id.widget_capture_journal)) {
                        org.junit.Assert.assertNotNull("$tag $width x $height", root.findViewById<TextView>(id))
                    }
                }
            }
        }
    }

    @Test fun loadingKeepsCountsUnknownAndOpensSection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = WidgetRenderer.render(context, 51, null, false, expandedBounds(),
            loadingKind = WidgetKind.TASKS).apply(context, null)
        assertEquals(context.getString(R.string.widget_loading_tasks),
            root.findViewById<TextView>(R.id.widget_compact_message).text.toString())
        assertEquals(context.getString(R.string.widget_data_open),
            root.findViewById<TextView>(R.id.widget_compact_primary).text.toString())
        assertEquals(android.view.View.GONE, root.findViewById<android.view.View>(R.id.widget_compact_secondary).visibility)
    }

    @Test fun fictionalRendererEvidenceKeepsBaselineTaskRow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val config = android.content.res.Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = 1f }
            val context = instrumentation.targetContext.createConfigurationContext(config)
            val locale = com.luma.lifeos.language.AppLanguages.current(context).tag
            val width = if (locale == "en") 250 else 400
            val height = if (locale == "en") 250 else 400
            val snapshot = sampleSnapshot().copy(rows = listOf(WidgetRow.Task(UUID.randomUUID(),
                "Widget task", LocalDate.parse("2026-10-20"), UUID.randomUUID())))
            val options = Bundle().apply {
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
            }
            val root = WidgetRenderer.render(context, 51, snapshot, false, options).apply(context, null)
            assertEquals(1, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)
            val density = context.resources.displayMetrics.density
            val w = (width * density).toInt()
            val h = (height * density).toInt()
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            root.layout(0, 0, w, h)
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File(context.cacheDir, "widget-renderer-$locale-${width}x$height.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
