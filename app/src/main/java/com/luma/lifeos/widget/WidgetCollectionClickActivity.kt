package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.domain.widget.WidgetKind
import java.time.Instant
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives collection row taps directly from the launcher. Navigation must not pass through a
 * BroadcastReceiver, because Android blocks that activity trampoline on modern versions.
 */
class WidgetCollectionClickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = WidgetIntents.decodeCollectionClick(intent)
        if (action == null || !isLiveWidget(action.widgetId)) {
            finish()
            return
        }

        when (val route = action.route) {
            is WidgetIntents.CollectionRoute.Navigate ->
                navigateToCollectionItem(action.widgetId, route.itemId, route.date)
            is WidgetIntents.CollectionRoute.Mutate -> {
                sendBroadcast(WidgetIntents.collectionReceiverIntent(this, action))
                finish()
            }
        }
    }

    private fun isLiveWidget(widgetId: Int): Boolean {
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
            ?: return false
        return provider.packageName == packageName &&
            WidgetRefreshCoordinator.kindForWidgetId(this, widgetId) != null
    }

    private fun navigateToCollectionItem(widgetId: Int, itemId: UUID, date: LocalDate) {
        lifecycleScope.launch {
            try {
                val destination = withContext(Dispatchers.IO) {
                    val app = application as? LumaApplication ?: return@withContext null
                    val kind = WidgetRefreshCoordinator.kindForWidgetId(this@WidgetCollectionClickActivity, widgetId)
                        ?: return@withContext null
                    val now = Instant.now()
                    val deviceZone = ZoneId.systemDefault()
                    val store = app.storage.widgets
                    val snapshot = store.snapshot(widgetId, now, deviceZone, rowLimit = 0)
                        ?: return@withContext null
                    if (snapshot.instance.kind != kind || snapshot.date != date ||
                        !store.isEligibleCollectionItem(widgetId, itemId, now, deviceZone)
                    ) {
                        return@withContext null
                    }
                    when (kind) {
                        WidgetKind.HABITS -> WidgetDestination.HabitDetail(itemId, snapshot.date)
                        WidgetKind.TASKS -> WidgetDestination.Task(itemId)
                    }
                }

                if (destination == null) {
                    WidgetRefreshCoordinator.request(this@WidgetCollectionClickActivity)
                } else {
                    startActivity(
                        WidgetIntents.navigationIntent(this@WidgetCollectionClickActivity, widgetId, destination)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                WidgetRefreshCoordinator.request(this@WidgetCollectionClickActivity)
            } finally {
                finish()
            }
        }
    }
}
