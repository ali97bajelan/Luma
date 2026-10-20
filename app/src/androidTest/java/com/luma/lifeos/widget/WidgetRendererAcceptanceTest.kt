package com.luma.lifeos.widget

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
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
        assertEquals("Capture",
            root.findViewById<TextView>(R.id.widget_data_refresh).text.toString())
        assertEquals(context.getString(R.string.widget_tasks_refresh_description),
            root.findViewById<View>(R.id.widget_header_refresh).contentDescription.toString())
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
            root.findViewById<View>(R.id.widget_data_open).contentDescription.toString())
        org.junit.Assert.assertTrue(root.findViewById<TextView>(R.id.widget_data_title).text.toString()
            .startsWith(context.getString(R.string.widget_tasks_title)))
        val update = root.findViewById<TextView>(R.id.widget_data_updated).text.toString()
        org.junit.Assert.assertTrue(update.contains("21"))
    }

    @Test fun summaryModeKeepsNamesHiddenAndSaysSoInListNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = sampleSnapshot().copy(
            instance = WidgetInstance(57, WidgetKind.TASKS, showDetails = false),
            total = 5,
            rows = listOf(WidgetRow.Task(UUID.randomUUID(), "Private task name",
                LocalDate.parse("2026-10-20"), UUID.randomUUID())),
        )
        val root = WidgetRenderer.render(context, 57, snapshot, false, expandedBounds()).apply(context, null)
        assertEquals(0, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)
        assertEquals(context.getString(R.string.widget_view_all_hidden,
            context.getString(R.string.widget_tasks_view_all)),
            root.findViewById<View>(R.id.widget_data_open).contentDescription.toString())
    }

    @Test fun emptyLibrariesOfferCreationActionsButEmptyScopesDoNot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val habits = sampleSnapshot().copy(
            instance = WidgetInstance(61, WidgetKind.HABITS, showDetails = true),
            total = 0,
            completed = 0,
            hasAnyItems = false,
            rows = emptyList(),
        )
        val habitsRoot = WidgetRenderer.render(context, 61, habits, false, expandedBounds()).apply(context, null)
        val habitAction = habitsRoot.findViewById<TextView>(R.id.widget_data_empty_action)
        assertEquals(context.getString(R.string.widget_habits_add), habitAction.text.toString())
        assertEquals(context.getString(R.string.widget_habits_add_description), habitAction.contentDescription.toString())
        val compactHabits = WidgetRenderer.render(context, 61, habits, false, bounds(180, 160)).apply(context, null)
        assertEquals(context.getString(R.string.widget_habits_add),
            compactHabits.findViewById<TextView>(R.id.widget_compact_primary).text.toString())

        val tasks = habits.copy(instance = WidgetInstance(62, WidgetKind.TASKS, showDetails = true))
        val tasksRoot = WidgetRenderer.render(context, 62, tasks, false, expandedBounds()).apply(context, null)
        val taskAction = tasksRoot.findViewById<TextView>(R.id.widget_data_empty_action)
        assertEquals(context.getString(R.string.widget_tasks_add), taskAction.text.toString())
        assertEquals(context.getString(R.string.widget_tasks_add_description), taskAction.contentDescription.toString())

        val noEligibleTasks = tasks.copy(hasAnyItems = true)
        val scopeRoot = WidgetRenderer.render(context, 62, noEligibleTasks, false, expandedBounds()).apply(context, null)
        assertEquals(0, scopeRoot.findViewById<LinearLayout>(R.id.widget_data_action_slot).childCount)
        org.junit.Assert.assertTrue(scopeRoot.findViewById<TextView>(R.id.widget_data_summary).text.toString()
            .endsWith(context.getString(R.string.widget_tasks_empty_scope)))
    }

    @Test fun pickerPreviewsShowFictionalContentWithSharedCaptureHeader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectations = listOf(
            R.layout.widget_preview_habits to R.string.widget_preview_habits_title,
            R.layout.widget_preview_tasks to R.string.widget_preview_tasks_title,
        )
        expectations.forEach { (layoutId, titleId) ->
            val root = android.view.LayoutInflater.from(context).inflate(layoutId, null)
            val textViews = ArrayList<View>()
            root.findViewsWithText(textViews, context.getString(R.string.widget_preview_label), View.FIND_VIEWS_WITH_TEXT)
            assertEquals(1, textViews.size)
            val captureViews = ArrayList<View>()
            root.findViewsWithText(captureViews, context.getString(R.string.widget_capture), View.FIND_VIEWS_WITH_TEXT)
            assertEquals(1, captureViews.size)
            val titleViews = ArrayList<View>()
            root.findViewsWithText(titleViews, context.getString(titleId), View.FIND_VIEWS_WITH_TEXT)
            assertEquals(1, titleViews.size)
            val oldFooterViews = ArrayList<View>()
            root.findViewsWithText(oldFooterViews, context.getString(R.string.widget_habits_view_all), View.FIND_VIEWS_WITH_TEXT)
            assertEquals(0, oldFooterViews.size)
        }
    }

    @Test fun reapplyingNonEmptySnapshotClearsPreviouslyRenderedEmptyAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val empty = sampleSnapshot().copy(total = 0, hasAnyItems = false, rows = emptyList())
        val root = WidgetRenderer.render(context, 51, empty, false, expandedBounds()).apply(context, null)
        assertEquals(1, root.findViewById<LinearLayout>(R.id.widget_data_action_slot).childCount)

        val populated = empty.copy(
            total = 1,
            hasAnyItems = true,
            rows = listOf(WidgetRow.Task(UUID.randomUUID(), "First task", empty.date, UUID.randomUUID())),
        )
        WidgetRenderer.render(context, 51, populated, false, expandedBounds()).reapply(context, root)

        assertEquals(0, root.findViewById<LinearLayout>(R.id.widget_data_action_slot).childCount)
        assertEquals(1, root.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)
    }

    @Test fun dataWidgetHeaderHasSeparateListCaptureAndRefreshTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = WidgetRenderer.render(context, 51, sampleSnapshot().copy(total = 0), false, expandedBounds())
            .apply(context, null)
        val title = root.findViewById<View>(R.id.widget_data_open)
        val capture = root.findViewById<TextView>(R.id.widget_data_refresh)
        val refresh = root.findViewById<View>(R.id.widget_header_refresh)
        assertEquals(context.getString(R.string.widget_tasks_view_all), title.contentDescription.toString())
        assertEquals("Capture", capture.text.toString())
        assertEquals(context.getString(R.string.widget_open_quick_capture_description), capture.contentDescription.toString())
        assertEquals(context.getString(R.string.widget_tasks_refresh_description), refresh.contentDescription.toString())
        layoutAtBounds(root, context, 600, 600)
        org.junit.Assert.assertTrue(title.isClickable)
        org.junit.Assert.assertTrue(capture.isClickable)
        org.junit.Assert.assertTrue(refresh.isClickable)
        org.junit.Assert.assertTrue(title.height >= 48 * context.resources.displayMetrics.density - 1)
        org.junit.Assert.assertTrue(capture.height >= 48 * context.resources.displayMetrics.density - 1)
        org.junit.Assert.assertTrue(refresh.height >= 48 * context.resources.displayMetrics.density - 1)
        assertEquals(capture.parent, refresh.parent)
        assertEquals(capture.parent, title.parent)
    }

    @Test fun expandedWidgetRendersFourCompleteMixedHeightRowsFromEightCandidates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = (0 until 8).map { index ->
            WidgetRow.Task(
                id = UUID.randomUUID(),
                title = if (index == 0) "A mixed title that wraps across two lines in this widget" else "Task ${index + 1}",
                dueDate = LocalDate.parse("2026-10-20"),
                actionToken = UUID.randomUUID(),
            )
        }
        val snapshot = sampleSnapshot().copy(total = 10, rows = rows)
        val root = WidgetRenderer.render(context, 51, snapshot, false, bounds(373, 420)).apply(context, null)
        layoutAtBounds(root, context, 373, 420)
        val rendered = root.findViewById<LinearLayout>(R.id.widget_data_rows)
        org.junit.Assert.assertTrue("Only ${rendered.childCount} rows rendered", rendered.childCount >= 4)
        for (index in 0 until rendered.childCount) {
            val row = rendered.getChildAt(index)
            org.junit.Assert.assertTrue("Row $index has no complete measured height", row.height >= 48 * context.resources.displayMetrics.density - 1)
            val bounds = android.graphics.Rect()
            row.getDrawingRect(bounds)
            rendered.offsetDescendantRectToMyCoords(row, bounds)
            org.junit.Assert.assertTrue("Row $index extends beyond the row container", bounds.bottom <= rendered.height)
        }

        val expandedRoot = WidgetRenderer.render(context, 51, snapshot, false, expandedBounds()).apply(context, null)
        assertEquals(8, expandedRoot.findViewById<LinearLayout>(R.id.widget_data_rows).childCount)
        assertEquals(context.getString(R.string.widget_view_all_more,
            context.getString(R.string.widget_tasks_view_all), 2),
            expandedRoot.findViewById<View>(R.id.widget_data_open).contentDescription.toString())

        val habits = (0 until 8).map { index ->
            WidgetRow.Habit(UUID.randomUUID(),
                if (index == 0) "A mixed-script habit title عادتی با نام بلند that wraps" else "Habit ${index + 1}",
                0, 3, false, UUID.randomUUID())
        }
        val habitSnapshot = sampleSnapshot().copy(
            instance = WidgetInstance(56, WidgetKind.HABITS, true),
            total = 8,
            completed = 0,
            rows = habits,
        )
        val habitRoot = WidgetRenderer.render(context, 56, habitSnapshot, false, bounds(373, 420)).apply(context, null)
        layoutAtBounds(habitRoot, context, 373, 420)
        val visibleHabits = habitRoot.findViewById<LinearLayout>(R.id.widget_data_rows)
        org.junit.Assert.assertTrue("Only ${visibleHabits.childCount} habit rows rendered", visibleHabits.childCount >= 4)
    }

    @Test fun habitProgressUsesInlineLayoutWhenItFitsAndStacksLongNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val habit = WidgetRow.Habit(UUID.randomUUID(), "Read", 1, 4, false, UUID.randomUUID())
        val inlineRoot = WidgetRenderer.render(context, 52,
            sampleSnapshot().copy(instance = WidgetInstance(52, WidgetKind.HABITS, true),
                total = 1, completed = 0, rows = listOf(habit)), false, bounds(373, 320))
            .apply(context, null)
        layoutAtBounds(inlineRoot, context, 373, 320)
        val inlineRow = inlineRoot.findViewById<LinearLayout>(R.id.widget_data_rows).getChildAt(0)
        val inlineTitle = inlineRow.findViewById<TextView>(R.id.widget_habit_row_title)
        val inlineProgress = inlineRow.findViewById<TextView>(R.id.widget_habit_row_count)
        assertEquals(LinearLayout.HORIZONTAL,
            inlineRow.findViewById<LinearLayout>(R.id.widget_habit_row_text).orientation)
        org.junit.Assert.assertTrue(inlineTitle.width > 0 && inlineProgress.width > 0)

        val longHabit = habit.copy(title = "A very long mixed-script habit name عادتی با نام بلند")
        val stackedRoot = WidgetRenderer.render(context, 53,
            sampleSnapshot().copy(instance = WidgetInstance(53, WidgetKind.HABITS, true),
                total = 1, completed = 0, rows = listOf(longHabit)), false, bounds(250, 320))
            .apply(context, null)
        layoutAtBounds(stackedRoot, context, 250, 320)
        val stackedRow = stackedRoot.findViewById<LinearLayout>(R.id.widget_data_rows).getChildAt(0)
        assertEquals(LinearLayout.VERTICAL,
            stackedRow.findViewById<LinearLayout>(R.id.widget_habit_row_text).orientation)
        org.junit.Assert.assertTrue(stackedRow.findViewById<View>(R.id.widget_habit_row_text).contentDescription
            .toString().contains(longHabit.title))
    }

    @Test fun reachedAndSkippedHabitsKeepStateAndDoNotOfferLogging() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = listOf(
            WidgetRow.Habit(UUID.randomUUID(), "Reached habit", 3, 3, false, null),
            WidgetRow.Habit(UUID.randomUUID(), "Skipped habit", 1, 3, true, null),
        )
        val snapshot = sampleSnapshot().copy(
            instance = WidgetInstance(54, WidgetKind.HABITS, true),
            total = 2,
            completed = 1,
            rows = rows,
        )
        val root = WidgetRenderer.render(context, 54, snapshot, false, bounds(600, 600)).apply(context, null)
        layoutAtBounds(root, context, 600, 600)
        val rowViews = root.findViewById<LinearLayout>(R.id.widget_data_rows)
        assertEquals(2, rowViews.childCount)
        val reached = rowViews.getChildAt(0)
        val skipped = rowViews.getChildAt(1)
        org.junit.Assert.assertTrue(reached.findViewById<View>(R.id.widget_habit_row_text)
            .contentDescription.toString().contains(context.getString(R.string.widget_habit_target_reached, 3, 3)))
        org.junit.Assert.assertTrue(skipped.findViewById<View>(R.id.widget_habit_row_text)
            .contentDescription.toString().contains(context.getString(R.string.widget_habit_skipped, 1, 3)))
        assertEquals(View.GONE, reached.findViewById<View>(R.id.widget_habit_row_action).visibility)
        assertEquals(View.GONE, skipped.findViewById<View>(R.id.widget_habit_row_action).visibility)
    }

    @Test fun overdueTaskKeepsFullAccessibleNameAndSeparateCompleteAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val taskTitle = "A long task title with enough detail to be visually shortened in the row"
        val task = WidgetRow.Task(UUID.randomUUID(), taskTitle,
            LocalDate.parse("2026-10-19"), UUID.randomUUID())
        val snapshot = sampleSnapshot().copy(total = 1, rows = listOf(task))
        val root = WidgetRenderer.render(context, 51, snapshot, false, bounds(250, 320)).apply(context, null)
        layoutAtBounds(root, context, 250, 320)
        val row = root.findViewById<LinearLayout>(R.id.widget_data_rows).getChildAt(0)
        val navigation = row.findViewById<View>(R.id.widget_task_row_text)
        val action = row.findViewById<TextView>(R.id.widget_task_row_action)
        org.junit.Assert.assertTrue(navigation.contentDescription.toString().contains(taskTitle))
        org.junit.Assert.assertTrue(navigation.contentDescription.toString().contains("Overdue"))
        assertEquals(context.getString(R.string.widget_task_complete_description, taskTitle),
            action.contentDescription.toString())
        org.junit.Assert.assertTrue(navigation.isClickable)
        org.junit.Assert.assertTrue(action.isClickable)
        org.junit.Assert.assertNotSame(navigation, action)
        org.junit.Assert.assertTrue(action.height >= 48 * context.resources.displayMetrics.density - 1)
    }

    @Test fun headerTargetsKeepLocalizedLabelsWholeAcrossFontScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            val previousLocales = localeManager.applicationLocales
            try {
                for (tag in listOf("en", "fr", "fa")) {
                    localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(tag)
                    for (scale in listOf(1f, 1.3f, 2f)) {
                        val config = android.content.res.Configuration(context.resources.configuration).apply {
                            fontScale = scale
                        }
                        val scaledContext = context.createConfigurationContext(config)
                        for ((width, height) in listOf(373 to 321, 180 to 220)) {
                            val root = WidgetRenderer.render(scaledContext, 55, sampleSnapshot().copy(total = 0),
                                false, bounds(width, height)).apply(scaledContext, null)
                            layoutAtBounds(root, scaledContext, width, height)
                            val density = scaledContext.resources.displayMetrics.density
                            val title: View? = root.findViewById<View?>(R.id.widget_data_open)
                            if (title != null) {
                                org.junit.Assert.assertTrue("$tag $scale $width title target", title.height >= 48 * density - 1)
                                org.junit.Assert.assertTrue(title.isClickable)
                            }
                            val capture: TextView? = root.findViewById<TextView?>(R.id.widget_data_refresh)
                                ?: root.findViewById<TextView?>(R.id.widget_compact_capture)
                            if (capture != null && capture.visibility == View.VISIBLE) {
                                org.junit.Assert.assertTrue("$tag $scale $width Capture target", capture.height >= 48 * density - 1)
                                assertEquals("$tag $scale $width Capture must stay on one line", 1, capture.lineCount)
                            }
                            val refresh: View? = root.findViewById<View?>(R.id.widget_header_refresh)
                            if (refresh != null) {
                                org.junit.Assert.assertTrue("$tag $scale $width Refresh target", refresh.height >= 48 * density - 1)
                            }
                            for (id in listOf(R.id.widget_compact_primary, R.id.widget_compact_secondary,
                                R.id.widget_compact_capture)) {
                                val action: View? = root.findViewById<View?>(id)
                                if (action != null && action.visibility == View.VISIBLE) {
                                    org.junit.Assert.assertTrue("$tag $scale $width compact target", action.height >= 48 * density - 1)
                                }
                            }
                        }
                    }
                }
            } finally {
                localeManager.applicationLocales = previousLocales
            }
        }
    }

    private fun expandedBounds() = Bundle().apply {
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 600)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 600)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 600)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 600)
    }

    private fun bounds(width: Int, height: Int) = Bundle().apply {
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun layoutAtBounds(root: View, context: android.content.Context, widthDp: Int, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, widthPx, heightPx)
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
                                val footer = root.findViewById<View>(R.id.widget_data_open)
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

    @Test fun captureKeepsWholeLabelsAcrossLocaleTextScalesAndBounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (tag in listOf("en", "fr", "fa")) {
                val locale = java.util.Locale.forLanguageTag(tag)
                for (scale in listOf(1f, 1.3f, 2f)) {
                    val config = android.content.res.Configuration(instrumentation.targetContext.resources.configuration).apply {
                        fontScale = scale
                        setLocale(locale)
                        setLayoutDirection(locale)
                    }
                    val context = instrumentation.targetContext.createConfigurationContext(config)
                    for ((width, height) in listOf(276 to 210, 350 to 240, 578 to 109, 220 to 400)) {
                        val root = QuickCaptureWidgetProvider().viewsForSize(context, context, 51, width, height)
                            .apply(context, null)
                        val density = context.resources.displayMetrics.density
                        val rootWidth = (width * density).toInt()
                        val rootHeight = (height * density).toInt()
                        root.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(rootWidth, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(rootHeight, android.view.View.MeasureSpec.EXACTLY),
                        )
                        root.layout(0, 0, rootWidth, rootHeight)
                        val actionIds = listOf(
                            R.id.widget_capture_mood,
                            R.id.widget_capture_memory,
                            R.id.widget_capture_journal,
                        )
                        val directActions = actionIds.map { root.findViewById<TextView>(it) }
                        if (directActions.all { it != null }) {
                            directActions.filterNotNull().forEach { action ->
                                org.junit.Assert.assertTrue(
                                    "$tag $width x $height target too small: ${action.width} x ${action.height}",
                                    action.width >= 48 * density - 1 && action.height >= 48 * density - 1,
                                )
                                val layout = requireNotNull(action.layout)
                                org.junit.Assert.assertTrue(
                                    "$tag $scale $width x $height clipped ${action.text}",
                                    layout.height <= action.height - action.paddingTop - action.paddingBottom,
                                )
                                val text = action.text.toString()
                                for (line in 0 until layout.lineCount - 1) {
                                    val end = layout.getLineEnd(line).coerceAtMost(text.length)
                                    val nextStart = layout.getLineStart(line + 1).coerceAtMost(text.length)
                                    val previous = text.getOrNull(end - 1)
                                    val next = text.getOrNull(nextStart)
                                    org.junit.Assert.assertTrue(
                                        "$tag $scale $width x $height split a word in ${action.text}",
                                        previous?.isWhitespace() == true || next?.isWhitespace() == true,
                                    )
                                }
                            }
                        } else {
                            val fallback = root.findViewById<TextView>(R.id.widget_quick_capture_open)
                            org.junit.Assert.assertNotNull(
                                "$tag $scale $width x $height must keep a readable capture action",
                                fallback,
                            )
                            org.junit.Assert.assertEquals(
                                context.getString(R.string.widget_open_quick_capture),
                                fallback.text.toString(),
                            )
                            org.junit.Assert.assertEquals(
                                context.getString(R.string.widget_open_quick_capture_description),
                                fallback.contentDescription.toString(),
                            )
                            org.junit.Assert.assertTrue(
                                "$tag $scale $width x $height fallback target too small",
                                fallback.width >= 48 * density - 1 && fallback.height >= 48 * density - 1,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun densityEvidenceRecordsVisibleRowsForRequestedFixturesAndBounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val surfaces = listOf("compact" to (220 to 160), "recorded" to (373 to 321), "expanded" to (373 to 420))
        val feedbackStates = listOf(
            "none" to null,
            "undo" to WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
            "retry" to WidgetActionResult(WidgetOutcome.FAILED, retryToken = UUID.randomUUID()),
        )
        val evidence = mutableListOf("fixture,surface,feedback,eligible,visible")
        for (eligible in listOf(0, 1, 3, 4, 8, 10)) {
            val rows = (0 until eligible.coerceAtMost(8)).map { index ->
                WidgetRow.Task(
                    id = UUID.randomUUID(),
                    title = if (index == 0) "A long mixed-script title عادتی for density measurement" else "Task ${index + 1}",
                    dueDate = LocalDate.parse("2026-10-20"),
                    actionToken = UUID.randomUUID(),
                )
            }
            for ((surface, dimensions) in surfaces) {
                for ((feedbackName, feedback) in feedbackStates) {
                    val snapshot = sampleSnapshot().copy(total = eligible, rows = rows, feedback = feedback)
                    val (width, height) = dimensions
                    val root = WidgetRenderer.render(context, 71, snapshot, false, bounds(width, height)).apply(context, null)
                    layoutAtBounds(root, context, width, height)
                    val rowContainer = root.findViewById<LinearLayout>(R.id.widget_data_rows)
                    val visible = rowContainer?.childCount ?: 0
                    org.junit.Assert.assertTrue("Visible $visible rows exceed $eligible eligible items at $surface / $feedbackName",
                        visible <= eligible.coerceAtMost(8))
                    if (rowContainer != null) {
                        for (index in 0 until rowContainer.childCount) {
                            val row = rowContainer.getChildAt(index)
                            org.junit.Assert.assertTrue("Partial row $index at $surface / $feedbackName", row.height >= 48 * context.resources.displayMetrics.density - 1)
                            val rect = android.graphics.Rect()
                            row.getDrawingRect(rect)
                            rowContainer.offsetDescendantRectToMyCoords(row, rect)
                            org.junit.Assert.assertTrue("Clipped row $index at $surface / $feedbackName", rect.bottom <= rowContainer.height)
                        }
                    }
                    evidence += "$eligible,$surface,$feedbackName,$eligible,$visible"
                }
            }
        }
        android.util.Log.i("WD08Rows", evidence.joinToString("\n"))
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
