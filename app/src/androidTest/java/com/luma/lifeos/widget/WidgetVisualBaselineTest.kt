package com.luma.lifeos.widget

import android.app.Instrumentation
import android.app.LocaleManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
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
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** T34 synthetic RemoteViews evidence. It never reads the app database or binds a launcher collection. */
@RunWith(AndroidJUnit4::class)
class WidgetVisualBaselineTest {
    @Test fun captureT34RendererFixtureMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val outputRoot = File(context.getExternalFilesDir(null), "t34/run-${runId()}").apply { mkdirs() }
        val matrixOutput = File(outputRoot, "matrix").apply { mkdirs() }
        val stressOutput = File(outputRoot, "stress").apply { mkdirs() }
        val rowOutput = File(outputRoot, "collection-rows").apply { mkdirs() }
        val feedbackOutput = File(outputRoot, "feedback-reapply").apply { mkdirs() }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val themePreferences = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
        val hadThemePreference = themePreferences.contains("selected_theme")
        val previousTheme = themePreferences.getString("selected_theme", null)
        val previousDefaultLocale = Locale.getDefault()
        val previousApplicationLocales = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else null
        val languagePreferences = if (Build.VERSION.SDK_INT < 33) {
            context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        } else null
        val hadLanguagePreference = languagePreferences?.contains("selected_tag") == true
        val previousLanguage = languagePreferences?.getString("selected_tag", null)
        val manifest = StringBuilder()
        val stressManifest = StringBuilder()
        val rowManifest = StringBuilder()
        val feedbackManifest = StringBuilder()
        var baselineCount = 0
        var stressCount = 0
        var rowCount = 0

        try {
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            val homeResolver = context.packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName ?: "unresolved"
            File(outputRoot, "environment.txt").writeText(
                "versionName=${packageInfo.versionName}\n" +
                    "versionCode=${packageInfo.longVersionCode}\n" +
                    "buildSha=recorded externally in the acceptance ledger\n" +
                    "api=${Build.VERSION.SDK_INT}\n" +
                    "device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n" +
                    "home_activity_resolver=$homeResolver\n" +
                    "capture_host=offscreen RemoteViews.apply; collection adapter rows are not launcher-bound\n" +
                    "fixture_source=synthetic WidgetSnapshot; no app records read or changed\n" +
                    "baseline=both widget kinds, app light/dark override against opposite system appearance, en, fontScale 1.0\n" +
                    "stress=fr/fa, fontScale 1.3/2.0, app light/dark override against opposite system appearance\n",
            )

            val counts = listOf(0, 1, 2, 3, 4, 8, 10, 50, 500)
            val baselineSizes = listOf(220 to 160, 373 to 321, 373 to 420)
            for (appTheme in listOf("light", "dark")) {
                val systemTheme = if (appTheme == "light") "dark" else "light"
                themePreferences.edit().putString("selected_theme", appTheme).commit()
                for (kind in WidgetKind.entries) {
                    for (count in counts) {
                        for ((width, height) in baselineSizes) {
                            val configured = configuredContext(context, "en", 1f, systemTheme)
                            val snapshot = fixture(kind, count, "en")
                            manifest.appendLine(captureShell(
                                instrumentation, configured, snapshot, width, height, appTheme, systemTheme,
                                File(matrixOutput, "${appTheme}-on-${systemTheme}-system"),
                            ))
                            baselineCount++
                        }
                    }
                }
            }

            val stressSizes = listOf(180 to 160, 180 to 220, 578 to 109)
            for (appTheme in listOf("light", "dark")) {
                val systemTheme = if (appTheme == "light") "dark" else "light"
                themePreferences.edit().putString("selected_theme", appTheme).commit()
                for (localeTag in listOf("fr", "fa")) {
                    selectAppLocale(context, localeTag)
                    for (fontScale in listOf(1.3f, 2f)) {
                        for (kind in WidgetKind.entries) {
                            for (count in listOf(1, 500)) {
                                for ((width, height) in stressSizes) {
                                    val configured = configuredContext(context, localeTag, fontScale, systemTheme)
                                    val snapshot = fixture(kind, count, localeTag)
                                    stressManifest.appendLine(captureShell(
                                        instrumentation, configured, snapshot, width, height, appTheme, systemTheme,
                                        File(stressOutput,
                                            "${appTheme}-on-${systemTheme}-system/${localeTag}-${fontScale}"),
                                    ).trimEnd() + ",\"$localeTag\",\"$fontScale\"")
                                    stressCount++
                                }
                            }
                        }
                    }
                }
            }

            selectAppLocale(context, "en")
            for (appTheme in listOf("light", "dark")) {
                val systemTheme = if (appTheme == "light") "dark" else "light"
                themePreferences.edit().putString("selected_theme", appTheme).commit()
                val configured = configuredContext(context, "en", 1f, systemTheme)
                val rows = edgeRows()
                rows.forEachIndexed { index, row ->
                    val kind = if (row is WidgetRow.Task) WidgetKind.TASKS else WidgetKind.HABITS
                    val snapshot = WidgetSnapshot(
                        instance = WidgetInstance(if (kind == WidgetKind.TASKS) 62 else 61, kind),
                        date = LocalDate.parse("2026-10-20"),
                        zone = ZoneOffset.UTC,
                        total = rows.size,
                        completed = if (kind == WidgetKind.HABITS) 0 else null,
                        hasAnyItems = true,
                        rows = listOf(row),
                        feedback = null,
                        updatedAt = Instant.parse("2026-10-20T08:00:00Z"),
                    )
                    val name = "${appTheme}-on-${systemTheme}-${kind.name.lowercase()}-edge-$index.png"
                    instrumentation.runOnMainSync {
                        val rendered = WidgetRenderer.collectionRow(configured, snapshot, row, 373)
                        val view = rendered.apply(configured, null)
                        val widthPx = dpToPx(configured, 373)
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        )
                        val heightPx = view.measuredHeight.coerceAtLeast(1)
                        view.layout(0, 0, widthPx, heightPx)
                        savePng(view, widthPx, heightPx, File(rowOutput, name))
                        val progress = view.findViewById<ProgressBar?>(R.id.widget_habit_row_progress)
                        val title = view.findViewById<View?>(
                            if (kind == WidgetKind.TASKS) R.id.widget_task_row_title else R.id.widget_habit_row_title,
                        )
                        val action = view.findViewById<View?>(
                            if (kind == WidgetKind.TASKS) R.id.widget_task_row_action else R.id.widget_habit_row_action,
                        )
                        val desc = view.contentDescription?.toString()
                            ?: view.findViewById<TextView?>(R.id.widget_habit_row_title)?.text?.toString()
                            ?: view.findViewById<TextView?>(R.id.widget_task_row_title)?.text?.toString().orEmpty()
                        rowManifest.appendLine(csv(listOf(
                            appTheme, systemTheme, kind.name, index.toString(), row.toString(),
                            "${view.width}x${view.height}", desc, boundsLabel(title), boundsLabel(action),
                            progress?.progress?.toString() ?: "n/a", progress?.max?.toString() ?: "n/a", name,
                        )))
                    }
                    rowCount++
                }
            }

            for (appTheme in listOf("light", "dark")) {
                val systemTheme = if (appTheme == "light") "dark" else "light"
                themePreferences.edit().putString("selected_theme", appTheme).commit()
                val configured = configuredContext(context, "en", 1f, systemTheme)
                for (kind in WidgetKind.entries) {
                    for ((state, feedback) in listOf(
                        "undo" to WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
                        "retry" to WidgetActionResult(WidgetOutcome.FAILED, retryToken = UUID.randomUUID()),
                    )) {
                        val snapshot = fixture(kind, 4, "en")
                        val bounds = bounds(373, 321)
                        instrumentation.runOnMainSync {
                            val root = WidgetRenderer.render(configured, snapshot.instance.id, snapshot, false, bounds)
                                .apply(configured, null)
                            WidgetRenderer.render(configured, snapshot.instance.id, snapshot.copy(feedback = feedback),
                                false, bounds).reapply(configured, root)
                            val widthPx = dpToPx(configured, 373)
                            val heightPx = dpToPx(configured, 321)
                            root.measure(
                                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                            )
                            root.layout(0, 0, widthPx, heightPx)
                            val name = "${appTheme}-on-${systemTheme}-${kind.name.lowercase()}-$state-reapply.png"
                            savePng(root, widthPx, heightPx, File(feedbackOutput, name))
                            val listVisible = root.findViewById<ListView?>(R.id.widget_data_rows)?.visibility == View.VISIBLE
                            val hintVisible = root.findViewById<View?>(R.id.widget_data_scroll_hint)?.visibility == View.VISIBLE
                            feedbackManifest.appendLine(csv(listOf(
                                "feedback", appTheme, systemTheme, kind.name, state, "373", "321",
                                "listVisible=$listVisible", "scrollHint=$hintVisible", name,
                            )))
                        }
                    }
                }
            }

            File(matrixOutput, "fixtures.csv").writeText(
                "captureType,kind,count,widthDp,heightDp,appTheme,systemTheme,collectionVisible,scrollHint,titleBounds,titleDescription,captureBounds,captureDescription,refreshBounds,refreshDescription,image\n" + manifest.toString(),
            )
            File(stressOutput, "fixtures.csv").writeText(
                "captureType,kind,count,widthDp,heightDp,appTheme,systemTheme,collectionVisible,scrollHint,titleBounds,titleDescription,captureBounds,captureDescription,refreshBounds,refreshDescription,image,locale,fontScale\n" + stressManifest.toString(),
            )
            File(rowOutput, "fixtures.csv").writeText(
                "appTheme,systemTheme,kind,index,row,widthPxHeightPx,accessibleDescription,titleBounds,actionBounds,progress,max,image\n" + rowManifest.toString(),
            )
            File(feedbackOutput, "feedback.csv").writeText(
                "captureType,appTheme,systemTheme,kind,state,widthDp,heightDp,listState,overflowState,image\n" + feedbackManifest.toString(),
            )
            writeContrastMatrix(context, outputRoot)
            assertEquals("Two kinds × nine counts × three bounds × two app overrides", 108, baselineCount)
            assertEquals("Two kinds × two app overrides × two locales × two scales × three bounds × two counts", 96, stressCount)
            assertEquals("Nine saved-count/action cases × two app overrides", 18, rowCount)
            assertEquals(8, feedbackOutput.listFiles { file -> file.extension == "png" }?.size)
            Log.i("T34", "offscreen_artifact=${outputRoot.absolutePath}; baseline=$baselineCount; stress=$stressCount; rows=$rowCount; feedback_reapply_cases=4")
        } finally {
            if (hadThemePreference) themePreferences.edit().putString("selected_theme", previousTheme).commit()
            else themePreferences.edit().remove("selected_theme").commit()
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = previousApplicationLocales!!
            } else {
                val editor = languagePreferences!!.edit()
                if (hadLanguagePreference) editor.putString("selected_tag", previousLanguage) else editor.remove("selected_tag")
                editor.commit()
            }
            Locale.setDefault(previousDefaultLocale)
        }
    }

    private fun captureShell(
        instrumentation: Instrumentation,
        context: Context,
        snapshot: WidgetSnapshot,
        width: Int,
        height: Int,
        appTheme: String,
        systemTheme: String,
        output: File,
    ): String {
        var row = ""
        instrumentation.runOnMainSync {
            output.mkdirs()
            val views = WidgetRenderer.render(context, snapshot.instance.id, snapshot, false, bounds(width, height))
            val root = views.apply(context, null)
            val widthPx = dpToPx(context, width)
            val heightPx = dpToPx(context, height)
            root.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, widthPx, heightPx)
            val name = "${snapshot.instance.kind.name.lowercase()}-${snapshot.total}-${width}x$height.png"
            savePng(root, widthPx, heightPx, File(output, name))
            val title = root.findViewById<View?>(R.id.widget_data_open)
            val capture = root.findViewById<View?>(R.id.widget_data_refresh)
            val refresh = root.findViewById<View?>(R.id.widget_header_refresh)
            val list = root.findViewById<ListView?>(R.id.widget_data_rows)
            val hint = root.findViewById<View?>(R.id.widget_data_scroll_hint)
            row = csv(listOf(
                "shell", snapshot.instance.kind.name, snapshot.total.toString(), width.toString(), height.toString(),
                appTheme, systemTheme, (list?.visibility == View.VISIBLE).toString(),
                (hint?.visibility == View.VISIBLE).toString(), boundsLabel(title), accessibleText(title),
                boundsLabel(capture), accessibleText(capture), boundsLabel(refresh), accessibleText(refresh), name,
            ))
        }
        return row
    }

    private fun fixture(kind: WidgetKind, count: Int, localeTag: String): WidgetSnapshot {
        val date = LocalDate.parse("2026-10-20")
        val longHabit = when (localeTag) {
            "fr" -> "Habitude très longue avec accents éàç et caractère persan عادت"
            "fa" -> "عادت بسیار طولانی با حروف فارسی و واژه فرانسوی éàç"
            else -> "A long mixed-script habit عادت بلند با عدد ۱۲ و (علامت)"
        }
        val longTask = when (localeTag) {
            "fr" -> "Tâche très longue avec accents éàç et texte persan کار بلند"
            "fa" -> "کار بسیار طولانی با نویسه‌های فارسی و عبارت française éàç"
            else -> "A long mixed-script task کار بلند با عدد ۱۲ و (علامت)"
        }
        val rows = when (kind) {
            WidgetKind.HABITS -> (0 until count).map { index ->
                when (index) {
                    0 -> WidgetRow.Habit(UUID.randomUUID(), longHabit, 0, 5, false, UUID.randomUUID())
                    1 -> WidgetRow.Habit(UUID.randomUUID(), "Reached target", 5, 5, false, null)
                    2 -> WidgetRow.Habit(UUID.randomUUID(), "Skipped habit", 5, 9, true, null)
                    else -> WidgetRow.Habit(UUID.randomUUID(), "Habit ${index + 1}", index % 4, 8, false, UUID.randomUUID())
                }
            }
            WidgetKind.TASKS -> (0 until count).map { index ->
                WidgetRow.Task(
                    UUID.randomUUID(), if (index == 0) longTask else "Task ${index + 1}",
                    if (index == 0) date.minusDays(1) else date, UUID.randomUUID(),
                )
            }
        }
        return WidgetSnapshot(
            instance = WidgetInstance(if (kind == WidgetKind.HABITS) 61 else 62, kind),
            date = date,
            zone = ZoneOffset.UTC,
            total = count,
            completed = if (kind == WidgetKind.HABITS) 1 else null,
            hasAnyItems = count > 0,
            rows = rows,
            feedback = null,
            updatedAt = Instant.parse("2026-10-20T08:00:00Z"),
        )
    }

    private fun edgeRows(): List<WidgetRow> = listOf(
        WidgetRow.Habit(UUID.randomUUID(), "Zero of five", 0, 5, false, UUID.randomUUID()),
        WidgetRow.Habit(UUID.randomUUID(), "One of five", 1, 5, false, UUID.randomUUID()),
        WidgetRow.Habit(UUID.randomUUID(), "Two of eight", 2, 8, false, UUID.randomUUID()),
        WidgetRow.Habit(UUID.randomUUID(), "Target reached", 5, 5, false, null),
        WidgetRow.Habit(UUID.randomUUID(), "Over target", 6, 5, false, null),
        WidgetRow.Habit(UUID.randomUUID(), "Skipped", 5, 9, true, null),
        WidgetRow.Habit(UUID.randomUUID(), "Invalid target", 1, 0, false, UUID.randomUUID()),
        WidgetRow.Task(UUID.randomUUID(), "Overdue task with a long title", LocalDate.parse("2026-10-19"), UUID.randomUUID()),
        WidgetRow.Task(UUID.randomUUID(), "Task due today", LocalDate.parse("2026-10-20"), UUID.randomUUID()),
    )

    private fun writeContrastMatrix(context: Context, output: File) {
        val rows = listOf(
            listOf("light", R.color.widget_text_primary_light, R.color.widget_surface_light, 4.5),
            listOf("light", R.color.widget_text_secondary_light, R.color.widget_surface_light, 4.5),
            listOf("light", R.color.widget_text_primary_light, R.color.widget_row_surface_light, 4.5),
            listOf("light", R.color.widget_text_secondary_light, R.color.widget_row_surface_light, 4.5),
            listOf("light", R.color.widget_overdue_light, R.color.widget_row_surface_light, 4.5),
            listOf("light", R.color.widget_mutation_text_light, R.color.widget_mutation_fill_light, 4.5),
            listOf("light", R.color.widget_action_utility_light, R.color.widget_count_fill_light, 4.5),
            listOf("light", R.color.widget_progress_fill_light, R.color.widget_progress_track_light, 3.0),
            listOf("dark", R.color.widget_text_primary_dark, R.color.widget_surface_dark, 4.5),
            listOf("dark", R.color.widget_text_secondary_dark, R.color.widget_surface_dark, 4.5),
            listOf("dark", R.color.widget_text_primary_dark, R.color.widget_row_surface_dark, 4.5),
            listOf("dark", R.color.widget_text_secondary_dark, R.color.widget_row_surface_dark, 4.5),
            listOf("dark", R.color.widget_overdue_dark, R.color.widget_row_surface_dark, 4.5),
            listOf("dark", R.color.widget_mutation_text_dark, R.color.widget_mutation_fill_dark, 4.5),
            listOf("dark", R.color.widget_action_utility_dark, R.color.widget_count_fill_dark, 4.5),
            listOf("dark", R.color.widget_progress_fill_dark, R.color.widget_progress_track_dark, 3.0),
        )
        val manifest = StringBuilder("theme,foreground,background,ratio,minimum,result\n")
        rows.forEach { row ->
            val theme = row[0] as String
            val foreground = context.getColor(row[1] as Int)
            val background = context.getColor(row[2] as Int)
            val minimum = row[3] as Double
            val ratio = contrastRatio(foreground, background)
            val name = "${theme},${row[1]},${row[2]},${"%.2f".format(Locale.US, ratio)},$minimum,${if (ratio >= minimum) "PASS" else "FAIL"}"
            manifest.appendLine(name)
            assertTrue("Contrast failed for $theme widget role pair: $ratio < $minimum", ratio >= minimum)
        }
        File(output, "contrast.csv").writeText(manifest.toString())
    }

    private fun configuredContext(base: Context, localeTag: String, fontScale: Float, systemTheme: String): Context {
        val mode = if (systemTheme == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            this.fontScale = fontScale
            setLocales(LocaleList.forLanguageTags(localeTag))
            setLayoutDirection(Locale.forLanguageTag(localeTag))
        }
        return base.createConfigurationContext(configuration)
    }

    private fun selectAppLocale(context: Context, localeTag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(localeTag)
        } else {
            context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit()
                .putString("selected_tag", localeTag).commit()
        }
    }

    private fun bounds(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    private fun boundsLabel(view: View?): String = if (view == null) "absent" else
        "${view.left}:${view.top}:${view.right}:${view.bottom}"

    private fun accessibleText(view: View?): String = view?.contentDescription?.toString()
        ?: (view as? TextView)?.text?.toString().orEmpty()

    private fun savePng(view: View, widthPx: Int, heightPx: Int, target: File) {
        target.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            target.outputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun csv(values: List<String>): String = values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }

    private fun runId(): String = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(Date()) + "-${System.nanoTime()}"

    private fun contrastRatio(first: Int, second: Int): Double {
        fun luminance(color: Int): Double {
            fun linear(channel: Int): Double {
                val value = channel / 255.0
                return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linear(android.graphics.Color.red(color)) +
                0.7152 * linear(android.graphics.Color.green(color)) +
                0.0722 * linear(android.graphics.Color.blue(color))
        }
        val values = listOf(luminance(first), luminance(second)).sortedDescending()
        return (values[0] + 0.05) / (values[1] + 0.05)
    }
}
