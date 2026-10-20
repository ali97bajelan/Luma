package com.luma.lifeos.widget

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.R
import com.luma.lifeos.MainActivity
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetIntentsTest {
    @Test fun emptyHabitActionHasAnAllowlistedNewHabitDestination() {
        assertEquals(WidgetDestination.NewHabit, decodeNavigation(widgetId = 61, destination = "new-habit"))
    }

    @Test fun eitherDataWidgetUsesTheSameDateFreeCaptureDestination() {
        assertEquals(WidgetDestination.QuickCapture, decodeNavigation(widgetId = 61, destination = "quick-capture"))
        assertEquals(WidgetDestination.QuickCapture, decodeNavigation(widgetId = 62, destination = "quick-capture"))
    }

    @Test fun legacyProviderAndDirectEditorRoutesRemainAvailable() {
        assertEquals(WidgetDestination.Capture(CaptureKind.MOOD),
            decodeNavigation(widgetId = 63, destination = "capture/MOOD"))
        assertEquals(WidgetDestination.Capture(CaptureKind.MEMORY),
            decodeNavigation(widgetId = 63, destination = "capture/MEMORY"))
        assertEquals(WidgetDestination.Capture(CaptureKind.JOURNAL),
            decodeNavigation(widgetId = 63, destination = "capture/JOURNAL"))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = context.packageManager.getReceiverInfo(
            ComponentName(context, QuickCaptureWidgetProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertEquals(context.getString(R.string.widget_quick_capture_name), provider.loadLabel(context.packageManager))
        assertTrue(provider.metaData?.containsKey("android.appwidget.provider") == true)
        assertTrue(context.getString(R.string.widget_quick_capture_description).isNotBlank())
    }

    @Test fun collectionFillInsKeepNavigationAndMutationBoundToRecordIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetId = 64
        val itemId = UUID.randomUUID()
        val token = UUID.randomUUID()
        val date = LocalDate.parse("2026-10-20")
        WidgetIntents.collectionTemplate(context, widgetId)

        val navigation = collectionIntent(context, widgetId).apply {
            putExtras(WidgetIntents.collectionNavigation(itemId, date))
        }
        assertEquals(
            WidgetIntents.Action.Collection(widgetId, WidgetIntents.CollectionRoute.Navigate(itemId, date)),
            WidgetIntents.decodeAction(navigation),
        )

        val mutation = collectionIntent(context, widgetId).apply {
            putExtras(WidgetIntents.collectionMutation(token, itemId, date))
        }
        assertEquals(
            WidgetIntents.Action.Collection(widgetId, WidgetIntents.CollectionRoute.Mutate(token, itemId, date)),
            WidgetIntents.decodeAction(mutation),
        )
        mutation.putExtra(WidgetIntents.EXTRA_COLLECTION_ITEM_ID, "not-a-uuid")
        assertNull(WidgetIntents.decodeAction(mutation))
    }

    private fun decodeNavigation(widgetId: Int, destination: String): WidgetDestination? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            action = WidgetIntents.ACTION_NAVIGATION
            data = Uri.parse("luma-widget://navigate/navigation/$widgetId/$destination")
        }
        return WidgetIntents.decode(intent)
    }

    private fun collectionIntent(context: android.content.Context, widgetId: Int) =
        Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetIntents.ACTION_COLLECTION_ITEM
            data = Uri.parse("luma-widget://action/collection/$widgetId")
        }
}
