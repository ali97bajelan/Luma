package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.ListView
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
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic RemoteViews baseline for WD09. It never reads the app database. */
@RunWith(AndroidJUnit4::class)
class WidgetVisualBaselineTest {
    @Test fun captureCurrentRendererFixtureMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val counts = listOf(0, 1, 2, 3, 4, 8, 10, 50, 500)
        val sizes = listOf(220 to 160, 373 to 321, 373 to 420)
        val rootOutput = File(context.getExternalFilesDir(null), "wd10-after").apply {
            deleteRecursively()
            mkdirs()
        }
        val output = File(rootOutput, "matrix").apply { mkdirs() }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val manifest = StringBuilder()
        manifest.appendLine("kind,count,width_dp,height_dp,feedback,show_details,collection_visible,scroll_hint,title_cue,capture_label,title_bounds,capture_bounds,refresh_bounds,image")
        var captureCount = 0

        val configuration = context.resources.configuration
        val localeTags = configuration.locales.toLanguageTags()
        val theme = if (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val homeResolver = context.packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName ?: "unresolved"
        File(rootOutput, "environment.txt").writeText(
            "versionName=${packageInfo.versionName}\n" +
                "versionCode=${packageInfo.longVersionCode}\n" +
                "api=${Build.VERSION.SDK_INT}\n" +
                "device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n" +
                "home_activity_resolver=$homeResolver\n" +
                "capture_host=offscreen RemoteViews.apply; collection adapter rows are not launcher-bound\n" +
                "density_dpi=${context.resources.displayMetrics.densityDpi}\n" +
                "locale=$localeTags\n" +
                "font_scale=${configuration.fontScale}\n" +
                "theme=$theme\n" +
                "fixture_source=synthetic WidgetSnapshot; no app records read or changed\n",
        )

        instrumentation.runOnMainSync {
            for (kind in WidgetKind.entries) {
                for (count in counts) {
                    for ((width, height) in sizes) {
                        val snapshot = fixture(kind, count)
                        val views = WidgetRenderer.render(
                            context,
                            snapshot.instance.id,
                            snapshot,
                            failure = false,
                            options = bounds(width, height),
                        )
                        val root = views.apply(context, null)
                        val density = context.resources.displayMetrics.density
                        val widthPx = (width * density).toInt()
                        val heightPx = (height * density).toInt()
                        root.measure(
                            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                        )
                        root.layout(0, 0, widthPx, heightPx)

                        val name = "${kind.name.lowercase()}-$count-${width}x$height-no-feedback-details.png"
                        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        try {
                            root.draw(Canvas(bitmap))
                            File(output, name).outputStream().use { stream ->
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                            }
                        } finally {
                            bitmap.recycle()
                        }

                        val collectionVisible = root.findViewById<ListView?>(R.id.widget_data_rows)
                            ?.visibility == View.VISIBLE
                        val scrollHint = root.findViewById<View?>(R.id.widget_data_scroll_hint)
                            ?.visibility == View.VISIBLE
                        val title = boundsLabel(root.findViewById(R.id.widget_data_open))
                        val titleCue = root.findViewById<TextView>(R.id.widget_data_more)?.text?.toString()?.replace(",", " ") ?: "absent"
                        val captureView = root.findViewById<TextView>(R.id.widget_data_refresh)
                        val captureLabel = captureView?.text?.toString()?.replace(",", " ") ?: "absent"
                        val capture = boundsLabel(captureView)
                        val refresh = boundsLabel(root.findViewById(R.id.widget_header_refresh))
                        manifest.appendLine(
                            "${kind.name},$count,$width,$height,none,true,$collectionVisible,$scrollHint,$titleCue,$captureLabel,$title,$capture,$refresh,$name",
                        )
                        captureCount++
                    }
                }
            }
        }

        File(output, "fixtures.csv").writeText(manifest.toString())
        assertEquals("Both providers × nine counts × three bounds", 54, captureCount)
        assertEquals(54, output.listFiles { file -> file.extension == "png" }?.size)
        val feedbackOutput = File(rootOutput, "feedback").apply { mkdirs() }
        captureFeedbackStates(instrumentation, context, feedbackOutput)
        assertEquals(4, feedbackOutput.listFiles { file -> file.extension == "png" }?.size)
        val artifact = exportEvidenceZip(context, rootOutput)
        Log.i("WD10", "after_artifact=$artifact; fixture_captures=$captureCount; feedback_captures=4")
    }

    private fun captureFeedbackStates(
        instrumentation: android.app.Instrumentation,
        context: android.content.Context,
        output: File,
    ) {
        val rows = StringBuilder()
        val size = 373 to 321
        instrumentation.runOnMainSync {
            for (kind in WidgetKind.entries) {
                for ((state, feedback) in listOf(
                    "undo" to WidgetActionResult(WidgetOutcome.SAVED, undoToken = UUID.randomUUID()),
                    "retry" to WidgetActionResult(WidgetOutcome.FAILED, retryToken = UUID.randomUUID()),
                )) {
                    val snapshot = fixture(kind, 4).copy(feedback = feedback)
                    val root = WidgetRenderer.render(
                        context,
                        snapshot.instance.id,
                        snapshot,
                        failure = false,
                        options = bounds(size.first, size.second),
                    ).apply(context, null)
                    val density = context.resources.displayMetrics.density
                    val widthPx = (size.first * density).toInt()
                    val heightPx = (size.second * density).toInt()
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, widthPx, heightPx)
                    val name = "${kind.name.lowercase()}-4-${size.first}x${size.second}-$state-details.png"
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    try {
                        root.draw(Canvas(bitmap))
                        File(output, name).outputStream().use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    val collectionVisible = root.findViewById<ListView?>(R.id.widget_data_rows)
                        ?.visibility == View.VISIBLE
                    val scrollHint = root.findViewById<View?>(R.id.widget_data_scroll_hint)
                        ?.visibility == View.VISIBLE
                    rows.appendLine("${kind.name},4,${size.first},${size.second},$state,true,$collectionVisible,$scrollHint,$name")
                }
            }
        }
        File(output, "feedback.csv").writeText("kind,count,width_dp,height_dp,feedback,show_details,collection_visible,scroll_hint,image\n$rows")
    }

    private fun exportEvidenceZip(context: android.content.Context, root: File): String {
        val displayName = "wd10-after-${System.currentTimeMillis()}.zip"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Luma/")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri))
            ZipOutputStream(output).use { zip ->
                root.walkTopDown().filter(File::isFile).forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
        return displayName
    }

    private fun fixture(kind: WidgetKind, count: Int): WidgetSnapshot {
        val date = LocalDate.parse("2026-10-20")
        val rows = when (kind) {
            WidgetKind.HABITS -> (0 until count).map { index ->
                when (index) {
                    0 -> WidgetRow.Habit(
                        UUID.randomUUID(),
                        "📌 A long mixed-script habit عادت بلند با عدد ۱۲ و (علامت)",
                        count = 123456,
                        target = 999999,
                        skipped = false,
                        actionToken = UUID.randomUUID(),
                    )
                    1 -> WidgetRow.Habit(UUID.randomUUID(), "Reached target", 999999, 999999, false, null)
                    2 -> WidgetRow.Habit(UUID.randomUUID(), "Skipped habit", 5, 9, true, null)
                    else -> WidgetRow.Habit(UUID.randomUUID(), "Habit ${index + 1}", index % 4, 8, false, UUID.randomUUID())
                }
            }
            WidgetKind.TASKS -> (0 until count).map { index ->
                WidgetRow.Task(
                    UUID.randomUUID(),
                    if (index == 0) "📌 A long mixed-script task کار بلند با عدد ۱۲ و (علامت)" else "Task ${index + 1}",
                    if (index == 0) date.minusDays(1) else date,
                    UUID.randomUUID(),
                )
            }
        }
        return WidgetSnapshot(
            instance = WidgetInstance(if (kind == WidgetKind.HABITS) 61 else 62, kind),
            date = date,
            zone = ZoneOffset.UTC,
            total = count,
            completed = if (kind == WidgetKind.HABITS) 1 else null,
            hasAnyItems = true,
            rows = rows,
            feedback = null,
            updatedAt = Instant.parse("2026-10-20T08:00:00Z"),
        )
    }

    private fun bounds(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun boundsLabel(view: View?): String = if (view == null) "absent" else
        "${view.left}:${view.top}:${view.right}:${view.bottom}"
}
