package com.luma.lifeos.widget

import android.os.Bundle
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.LocaleList
import android.text.format.DateFormat
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetActionResult
import com.luma.lifeos.domain.widget.WidgetInstance
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.widget.WidgetRow
import com.luma.lifeos.domain.widget.WidgetSnapshot
import com.luma.lifeos.language.AppLanguages
import com.luma.lifeos.language.localizedTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRendererAcceptanceTest {
    @Test fun reapplyingCompletedTaskHidesCollectionAndKeepsUndoAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 51
        val task = WidgetRow.Task(
            id = UUID.randomUUID(),
            title = "Widget task",
            dueDate = LocalDate.parse("2026-10-20"),
            actionToken = UUID.randomUUID(),
        )
        val before = WidgetSnapshot(
            instance = WidgetInstance(widgetId, WidgetKind.TASKS),
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
        assertEquals(View.VISIBLE, root.findViewById<ListView>(R.id.widget_data_rows).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_data_scroll_hint).visibility)

        val completed = before.copy(
            total = 0,
            rows = emptyList(),
            feedback = WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
        )
        val refreshedViews = WidgetRenderer.render(context, widgetId, completed, failure = false, options = expandedBounds())
        refreshedViews.reapply(context, root)

        assertEquals(View.GONE, root.findViewById<ListView>(R.id.widget_data_rows).visibility)
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
        assertEquals(View.GONE, root.findViewById<ListView>(R.id.widget_data_rows).visibility)
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

    @Test fun hiddenCountIncludesItemsOutsideSnapshotPrefixWithoutSummaryHeader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = sampleSnapshot().copy(total = 7, updatedAt = Instant.parse("2026-10-21T08:00:00Z"))
        val root = WidgetRenderer.render(context, 51, snapshot, false, expandedBounds()).apply(context, null)
        assertEquals(context.getString(R.string.widget_view_all_count,
            context.getString(R.string.widget_tasks_view_all), 7),
            root.findViewById<View>(R.id.widget_data_open).contentDescription.toString())
        org.junit.Assert.assertTrue(root.findViewById<TextView>(R.id.widget_data_title).text.toString()
            .startsWith(context.getString(R.string.widget_tasks_title)))
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_data_summary).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_data_updated).visibility)
        val compact = WidgetRenderer.render(context, 51, snapshot, false, bounds(250, 96)).apply(context, null)
        assertEquals(context.resources.getQuantityString(R.plurals.widget_tasks_total, 7, 7),
            compact.findViewById<TextView>(R.id.widget_compact_message).text.toString())
    }

    @Test fun populatedWidgetShowsCollectionWithoutPrivacyMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = sampleSnapshot().copy(
            instance = WidgetInstance(57, WidgetKind.TASKS),
            total = 5,
            rows = listOf(WidgetRow.Task(UUID.randomUUID(), "Visible task name",
                LocalDate.parse("2026-10-20"), UUID.randomUUID())),
        )
        val root = WidgetRenderer.render(context, 57, snapshot, false, expandedBounds()).apply(context, null)
        assertEquals(View.VISIBLE, root.findViewById<ListView>(R.id.widget_data_rows).visibility)
        assertEquals(context.getString(R.string.widget_view_all_count,
            context.getString(R.string.widget_tasks_view_all), 5),
            root.findViewById<View>(R.id.widget_data_open).contentDescription.toString())
    }

    @Test fun emptyLibrariesOfferCreationActionsButEmptyScopesDoNot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val habits = sampleSnapshot().copy(
            instance = WidgetInstance(61, WidgetKind.HABITS),
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

        val tasks = habits.copy(instance = WidgetInstance(62, WidgetKind.TASKS))
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
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val expectations = listOf(
            R.layout.widget_preview_habits to R.string.widget_preview_habits_title,
            R.layout.widget_preview_tasks to R.string.widget_preview_tasks_title,
        )
        listOf(
            android.content.res.Configuration.UI_MODE_NIGHT_NO,
            android.content.res.Configuration.UI_MODE_NIGHT_YES,
        ).forEach { nightMode ->
            val configuration = android.content.res.Configuration(baseContext.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            val context = baseContext.createConfigurationContext(configuration)
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
                val secondRowId = if (layoutId == R.layout.widget_preview_habits) {
                    R.string.widget_preview_habit_second
                } else R.string.widget_preview_task_second
                val secondRows = ArrayList<View>()
                root.findViewsWithText(secondRows, context.getString(secondRowId), View.FIND_VIEWS_WITH_TEXT)
                assertEquals(1, secondRows.size)
                val scrollHints = ArrayList<View>()
                root.findViewsWithText(
                    scrollHints,
                    context.getString(R.string.widget_scroll_for_more),
                    View.FIND_VIEWS_WITH_TEXT,
                )
                assertEquals(1, scrollHints.size)
                val oldFooterViews = ArrayList<View>()
                root.findViewsWithText(
                    oldFooterViews,
                    context.getString(R.string.widget_habits_view_all),
                    View.FIND_VIEWS_WITH_TEXT,
                )
                assertEquals(0, oldFooterViews.size)
                assertNotNull(root.findViewById<View>(R.id.widget_preview_count).background)

                if (layoutId == R.layout.widget_preview_tasks) {
                    assertNotNull(root.findViewById<LinearLayout>(R.id.widget_preview_task_card).background)
                    assertEquals(context.getString(R.string.widget_task_due_today),
                        root.findViewById<TextView>(R.id.widget_preview_task_due).text.toString())
                    assertEquals(context.getString(R.string.widget_preview_task_action),
                        root.findViewById<TextView>(R.id.widget_preview_task_action).text.toString())
                    assertEquals(context.getString(R.string.widget_preview_task_overdue),
                        root.findViewById<TextView>(R.id.widget_preview_task_overdue).text.toString())
                } else {
                    assertNotNull(root.findViewById<LinearLayout>(R.id.widget_preview_habit_card).background)
                    assertEquals(0, root.findViewById<android.widget.ProgressBar>(R.id.widget_preview_habit_progress).progress)
                    assertEquals(1000, root.findViewById<android.widget.ProgressBar>(R.id.widget_preview_habit_second_progress).progress)
                    assertEquals(1000, root.findViewById<android.widget.ProgressBar>(R.id.widget_preview_habit_second_progress).max)
                    assertEquals(context.getString(R.string.widget_habit_log_short),
                        root.findViewById<TextView>(R.id.widget_preview_habit_action).text.toString())
                    assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.widget_preview_habit_action).visibility)
                    assertEquals(context.getString(R.string.widget_preview_habit_second_action),
                        root.findViewById<TextView>(R.id.widget_preview_habit_second_count).text.toString())
                }
            }
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
        assertEquals(View.VISIBLE, root.findViewById<ListView>(R.id.widget_data_rows).visibility)
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

    @Test fun expandedWidgetBindsScrollableCollectionAndShowsOverflowHint() {
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
        val collection = root.findViewById<ListView>(R.id.widget_data_rows)
        assertEquals(View.VISIBLE, collection.visibility)
        org.junit.Assert.assertTrue(collection.contentDescription.toString().contains("Tasks"))
        org.junit.Assert.assertTrue(collection.contentDescription.toString().contains("10"))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_data_scroll_hint).visibility)

        val expandedRoot = WidgetRenderer.render(context, 51, snapshot, false, expandedBounds()).apply(context, null)
        assertEquals(View.VISIBLE, expandedRoot.findViewById<ListView>(R.id.widget_data_rows).visibility)
        assertEquals(View.VISIBLE, expandedRoot.findViewById<View>(R.id.widget_data_scroll_hint).visibility)
        assertEquals(context.getString(R.string.widget_view_all_count,
            context.getString(R.string.widget_tasks_view_all), 10),
            expandedRoot.findViewById<View>(R.id.widget_data_open).contentDescription.toString())

        val habits = (0 until 8).map { index ->
            WidgetRow.Habit(UUID.randomUUID(),
                if (index == 0) "A mixed-script habit title عادتی با نام بلند that wraps" else "Habit ${index + 1}",
                0, 3, false, UUID.randomUUID())
        }
        val habitSnapshot = sampleSnapshot().copy(
            instance = WidgetInstance(56, WidgetKind.HABITS),
            total = 8,
            completed = 0,
            rows = habits,
        )
        val habitRoot = WidgetRenderer.render(context, 56, habitSnapshot, false, bounds(373, 420)).apply(context, null)
        layoutAtBounds(habitRoot, context, 373, 420)
        val habitCollection = habitRoot.findViewById<ListView>(R.id.widget_data_rows)
        assertEquals(View.VISIBLE, habitCollection.visibility)
        org.junit.Assert.assertTrue(habitCollection.contentDescription.toString().contains("Habits"))
        org.junit.Assert.assertTrue(habitCollection.contentDescription.toString().contains("8"))
    }

    @Test fun habitProgressUsesInlineLayoutWhenItFitsAndStacksLongNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val habit = WidgetRow.Habit(UUID.randomUUID(), "Read", 1, 4, false, UUID.randomUUID())
        val inlineSnapshot = sampleSnapshot().copy(instance = WidgetInstance(52, WidgetKind.HABITS),
            total = 1, completed = 0, rows = listOf(habit))
        val inlineRow = WidgetRenderer.collectionRow(context, inlineSnapshot, habit, 373).apply(context, null)
        layoutAtBounds(inlineRow, context, 373, 100)
        val inlineTitle = inlineRow.findViewById<TextView>(R.id.widget_habit_row_title)
        val inlineProgress = inlineRow.findViewById<TextView>(R.id.widget_habit_row_count)
        assertEquals(LinearLayout.HORIZONTAL,
            inlineRow.findViewById<LinearLayout>(R.id.widget_habit_row_text).orientation)
        org.junit.Assert.assertTrue(inlineTitle.width > 0 && inlineProgress.width > 0)

        val longHabit = habit.copy(title = "A very long mixed-script habit name عادتی با نام بلند")
        val stackedSnapshot = sampleSnapshot().copy(instance = WidgetInstance(53, WidgetKind.HABITS),
            total = 1, completed = 0, rows = listOf(longHabit))
        val stackedRow = WidgetRenderer.collectionRow(context, stackedSnapshot, longHabit, 250).apply(context, null)
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
            instance = WidgetInstance(54, WidgetKind.HABITS),
            total = 2,
            completed = 1,
            rows = rows,
        )
        val reached = WidgetRenderer.collectionRow(context, snapshot, rows[0], 600).apply(context, null)
        val skipped = WidgetRenderer.collectionRow(context, snapshot, rows[1], 600).apply(context, null)
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
            LocalDate.parse("2026-10-19"), UUID.randomUUID(), LocalTime.of(9, 5))
        val snapshot = sampleSnapshot().copy(total = 1, rows = listOf(task))
        val row = WidgetRenderer.collectionRow(context, snapshot, task, 250).apply(context, null)
        val density = context.resources.displayMetrics.density
        val rowWidth = (250 * density).toInt()
        row.measure(View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((200 * density).toInt(), View.MeasureSpec.AT_MOST))
        row.layout(0, 0, rowWidth, row.measuredHeight)
        val navigation = row.findViewById<View>(R.id.widget_task_row_text)
        val action = row.findViewById<TextView>(R.id.widget_task_row_action)
        org.junit.Assert.assertTrue(navigation.contentDescription.toString().contains(taskTitle))
        org.junit.Assert.assertTrue(navigation.contentDescription.toString().contains("Overdue"))
        org.junit.Assert.assertTrue(action.contentDescription.toString().startsWith(
            context.getString(R.string.widget_task_complete_description, taskTitle)))
        org.junit.Assert.assertTrue(action.contentDescription.toString().contains("Overdue"))
        org.junit.Assert.assertTrue(action.contentDescription.toString().contains("2026"))
        val expectedTime = localizedTime(DateFormat.is24HourFormat(context), AppLanguages.current(context).locale)
            .format(LocalTime.of(9, 5))
        org.junit.Assert.assertTrue(navigation.contentDescription.toString().contains(expectedTime))
        org.junit.Assert.assertTrue(action.contentDescription.toString().contains(expectedTime))
        org.junit.Assert.assertNotSame(navigation, action)
        org.junit.Assert.assertTrue(action.height >= 48 * density - 1)
    }

    @Test fun habitMutationDescriptionIncludesCurrentProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val habit = WidgetRow.Habit(UUID.randomUUID(), "Read", 2, 5, false, UUID.randomUUID())
        val snapshot = sampleSnapshot().copy(
            instance = WidgetInstance(56, WidgetKind.HABITS),
            total = 1,
            completed = 0,
            rows = listOf(habit),
        )
        val row = WidgetRenderer.collectionRow(context, snapshot, habit, 600).apply(context, null)
        val action = row.findViewById<View>(R.id.widget_habit_row_action)
        org.junit.Assert.assertTrue(action.contentDescription.toString().contains("Read"))
        org.junit.Assert.assertTrue(action.contentDescription.toString().contains("2 / 5"))
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

    @Test fun wd10KeepsHeaderTargetsSeparateAndShowsTotalCountBelowTitle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = (0 until 8).map { index ->
            WidgetRow.Task(UUID.randomUUID(), "Task ${index + 1}", LocalDate.parse("2026-10-20"), UUID.randomUUID())
        }
        val root = WidgetRenderer.render(
            context,
            56,
            sampleSnapshot().copy(total = 8, rows = rows),
            failure = false,
            options = bounds(373, 420),
        ).apply(context, null)
        layoutAtBounds(root, context, 373, 420)

        val titleTarget = root.findViewById<View>(R.id.widget_data_open)
        val title = root.findViewById<TextView>(R.id.widget_data_title)
        val totalCue = root.findViewById<TextView>(R.id.widget_data_more)
        val capture = root.findViewById<TextView>(R.id.widget_data_refresh)
        val refresh = root.findViewById<View>(R.id.widget_header_refresh)
        val density = context.resources.displayMetrics.density
        assertTargetsSeparated(root, titleTarget, capture, (8 * density).toInt())
        assertTargetsSeparated(root, capture, refresh, (8 * density).toInt())
        assertEquals("Capture", capture.text.toString())
        assertEquals(context.getColor(R.color.widget_action_utility_light), capture.currentTextColor)
        assertEquals("8 tasks", totalCue.text.toString())
        assertTrue("The total cue sits beneath the title", boundsIn(root, totalCue).top >= boundsIn(root, title).bottom)
        val background = capture.background as StateListDrawable
        assertEquals(Color.TRANSPARENT, (background.current as ColorDrawable).color)
        val captureBitmap = Bitmap.createBitmap(capture.width, capture.height, Bitmap.Config.ARGB_8888)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val host = FrameLayout(activity)
                    activity.setContentView(host)
                    host.addView(root, FrameLayout.LayoutParams(root.width, root.height))
                    root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, root.width, root.height)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    capture.draw(Canvas(captureBitmap))
                }
            }
            assertTrue("The Capture label is painted in the resting state: " +
                "size=${capture.width}x${capture.height}, shown=${capture.isShown}, visible=${capture.visibility}, " +
                "layout=${capture.layout?.lineCount}, alpha=${capture.alpha}, text=${capture.text}, " +
                "layoutSize=${capture.layout?.width}x${capture.layout?.height}, lineWidth=${capture.layout?.getLineWidth(0)}, " +
                "lineBounds=${capture.layout?.getLineLeft(0)}..${capture.layout?.getLineRight(0)}, " +
                "paintAlpha=${capture.paint.alpha}, paintColor=${capture.paint.color}",
                (0 until captureBitmap.width).any { x ->
                    (0 until captureBitmap.height).any { y -> Color.alpha(captureBitmap.getPixel(x, y)) > 0 }
                })
        } finally {
            captureBitmap.recycle()
        }
    }

    @Test fun populatedWidgetsOmitSummaryAndFreshnessBeforeFeedbackAndRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val date = LocalDate.parse("2026-10-19")
        val rows = (0 until 8).map { index ->
            WidgetRow.Task(UUID.randomUUID(), "Task ${index + 1}", date, UUID.randomUUID())
        }
        val snapshot = sampleSnapshot().copy(
            date = date,
            zone = java.time.ZoneId.of("America/Los_Angeles"),
            total = 8,
            rows = rows,
            feedback = WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
            updatedAt = Instant.parse("2026-10-20T08:00:00Z"),
        )
        val root = WidgetRenderer.render(context, 57, snapshot, false, bounds(373, 420)).apply(context, null)
        layoutAtBounds(root, context, 373, 420)

        val scope = root.findViewById<TextView>(R.id.widget_data_summary)
        val freshness = root.findViewById<TextView>(R.id.widget_data_updated)
        val feedback = root.findViewById<View>(R.id.widget_feedback_slot)
        val rowsView = root.findViewById<View>(R.id.widget_data_rows)
        assertEquals(View.GONE, scope.visibility)
        assertEquals(View.GONE, freshness.visibility)
        val header = root.findViewById<View>(R.id.widget_data_open)
        assertTrue("Feedback follows the title directly", boundsIn(root, header).bottom <= boundsIn(root, feedback).top)
        assertTrue("Rows follow feedback", boundsIn(root, feedback).bottom <= boundsIn(root, rowsView).top)
    }

    @Test fun wd10StacksNarrowHeaderAndKeepsEightDpBetweenTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = (0 until 8).map { index ->
            WidgetRow.Task(UUID.randomUUID(), "Task ${index + 1}", LocalDate.parse("2026-10-20"), UUID.randomUUID())
        }
        val root = WidgetRenderer.render(
            context, 58, sampleSnapshot().copy(total = 8, rows = rows), false, bounds(210, 420),
        ).apply(context, null)
        layoutAtBounds(root, context, 210, 420)

        val titleTarget = root.findViewById<View>(R.id.widget_data_open)
        val capture = root.findViewById<TextView>(R.id.widget_data_refresh)
        val refresh = root.findViewById<View>(R.id.widget_header_refresh)
        val density = context.resources.displayMetrics.density
        assertTargetsSeparated(root, titleTarget, capture, (8 * density).toInt())
        assertTargetsSeparated(root, capture, refresh, (8 * density).toInt())
        assertTrue(titleTarget.height >= 48 * density - 1)
        assertTrue(capture.height >= 48 * density - 1)
        assertTrue(refresh.height >= 48 * density - 1)
        assertEquals("Capture", capture.text.toString())
    }

    @Test fun wd10HeaderFitsSupportedLocalesAt100130And200PercentText() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        val previousLocales = localeManager.applicationLocales
        val previousDefaultLocale = Locale.getDefault()
        try {
            for (languageTag in listOf("en", "fr", "fa")) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(languageTag)
                val localized = AppLanguages.wrap(context)
                for (scale in listOf(1f, 1.3f, 2f)) {
                    val configuration = android.content.res.Configuration(localized.resources.configuration).apply {
                        fontScale = scale
                    }
                    val scaled = localized.createConfigurationContext(configuration)
                    val rows = (0 until 8).map { index ->
                        WidgetRow.Task(UUID.randomUUID(), "Task ${index + 1}", LocalDate.parse("2026-10-20"), UUID.randomUUID())
                    }
                    val snapshot = sampleSnapshot().copy(total = 8, rows = rows)
                    val root = WidgetRenderer.render(scaled, 59, snapshot, false, bounds(373, 420))
                        .apply(scaled, null)
                    layoutAtBounds(root, scaled, 373, 420)

                    val titleTarget = root.findViewById<View>(R.id.widget_data_open)
                    val title = root.findViewById<TextView>(R.id.widget_data_title)
                    val totalCue = root.findViewById<TextView>(R.id.widget_data_more)
                    val capture = root.findViewById<TextView>(R.id.widget_data_refresh)
                    val refresh = root.findViewById<View>(R.id.widget_header_refresh)
                    val density = scaled.resources.displayMetrics.density
                    val state = "$languageTag at ${scale * 100}%"
                    assertEquals("$state Capture label", localized.getString(R.string.widget_capture), capture.text.toString())
                    assertTargetsSeparated(root, titleTarget, capture, (8 * density).toInt())
                    assertTargetsSeparated(root, capture, refresh, (8 * density).toInt())
                    assertTrue("$state title target", titleTarget.height >= 48 * density - 1)
                    assertTrue("$state Capture target", capture.height >= 48 * density - 1)
                    assertTrue("$state Refresh target", refresh.height >= 48 * density - 1)
                    if (totalCue.visibility == View.VISIBLE) {
                        assertEquals("$state total cue",
                            localized.resources.getQuantityString(R.plurals.widget_tasks_total, 8, 8),
                            totalCue.text.toString())
                        assertTrue("$state total cue stays below title",
                            boundsIn(root, totalCue).top >= boundsIn(root, title).bottom)
                    }
                }
            }
        } finally {
            localeManager.applicationLocales = previousLocales
            Locale.setDefault(previousDefaultLocale)
        }
    }

    private fun assertTargetsSeparated(root: View, first: View, second: View, minimumGapPx: Int) {
        val firstRect = boundsIn(root, first)
        val secondRect = boundsIn(root, second)
        val horizontal = firstRect.right + minimumGapPx <= secondRect.left ||
            secondRect.right + minimumGapPx <= firstRect.left
        val vertical = firstRect.bottom + minimumGapPx <= secondRect.top ||
            secondRect.bottom + minimumGapPx <= firstRect.top
        assertTrue("Header targets are separate by at least ${minimumGapPx}px: $firstRect vs $secondRect",
            horizontal || vertical)
    }

    private fun boundsIn(root: View, child: View): Rect = Rect(0, 0, child.width, child.height).also {
        (root as ViewGroup).offsetDescendantRectToMyCoords(child, it)
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
        instance = WidgetInstance(51, WidgetKind.TASKS),
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
                        val base = sampleSnapshot().copy(instance = WidgetInstance(51, kind),
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

    @Test fun densityEvidenceRecordsCollectionAndOverflowAffordanceForRequestedFixturesAndBounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val surfaces = listOf("compact" to (220 to 160), "recorded" to (373 to 321), "expanded" to (373 to 420))
        val feedbackStates = listOf(
            "none" to null,
            "undo" to WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
            "retry" to WidgetActionResult(WidgetOutcome.FAILED, retryToken = UUID.randomUUID()),
        )
        val evidence = mutableListOf("fixture,surface,feedback,eligible,collection,hint")
        for (eligible in listOf(0, 1, 3, 4, 8, 10)) {
            val rows = (0 until eligible).map { index ->
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
                    val collection = root.findViewById<ListView?>(R.id.widget_data_rows)
                    val visible = collection?.visibility == View.VISIBLE
                    val hint = root.findViewById<View?>(R.id.widget_data_scroll_hint)?.visibility == View.VISIBLE
                    if (eligible == 0 && collection != null) assertEquals(View.GONE, collection.visibility)
                    if (eligible == 0) assertFalse("Empty collection has a scroll hint at $surface / $feedbackName", hint)
                    evidence += "$eligible,$surface,$feedbackName,$eligible,$visible,$hint"
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

    @Test fun fictionalRendererEvidenceKeepsCollectionTaskRow() {
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
            val task = requireNotNull(snapshot.rows.singleOrNull()) as WidgetRow.Task
            val row = WidgetRenderer.collectionRow(context, snapshot, task, width).apply(context, null)
            val density = context.resources.displayMetrics.density
            val w = (width * density).toInt()
            val h = (64 * density).toInt()
            row.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.AT_MOST))
            row.layout(0, 0, w, h)
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            row.draw(android.graphics.Canvas(bitmap))
            java.io.File(context.cacheDir, "widget-collection-row-$locale-${width}x${h}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
