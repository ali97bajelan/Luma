package com.luma.lifeos.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetInstance
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.ui.theme.LifeOsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Required first-placement flow and the safe in-app reconfiguration entry point. */
class WidgetConfigurationActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var kind: WidgetKind? = null
    private var loaded by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    private var loadFailed by mutableStateOf(false)
    private var saveFailed by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.luma.lifeos.language.AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        kind = liveKind(widgetId)
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (kind == null) {
            finish()
            return
        }
        savedInstanceState?.let {
            loaded = it.getBoolean(STATE_LOADED, false)
        }
        setContent {
            LifeOsTheme {
                ConfigurationContent(
                    kind = requireNotNull(kind),
                    loaded = loaded,
                    saving = saving,
                    loadFailed = loadFailed,
                    saveFailed = saveFailed,
                    onSave = ::saveConfiguration,
                    onCancel = ::finish,
                    onRetryLoad = ::loadConfiguration,
                )
            }
        }
        if (savedInstanceState == null) loadConfiguration() else if (!loaded) loadConfiguration()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_LOADED, loaded)
        super.onSaveInstanceState(outState)
    }

    private fun loadConfiguration() {
        loadFailed = false
        loaded = false
        lifecycleScope.launch {
            try {
                val stored = withContext(Dispatchers.IO) {
                    (application as LumaApplication).storage.widgets.configuration(widgetId)
                }
                if (stored != null && stored.kind != kind) {
                    loadFailed = true
                }
                loaded = !loadFailed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadFailed = true
            }
        }
    }

    private fun saveConfiguration() {
        if (saving || !loaded) return
        val selectedKind = kind ?: return
        val selectedWidgetId = widgetId
        saving = true
        saveFailed = false
        lifecycleScope.launch {
            try {
                if (liveKind(widgetId) != selectedKind) {
                    throw IllegalStateException("Widget provider is no longer live")
                }
                val storage = (application as LumaApplication).storage
                withContext(Dispatchers.IO) {
                    if (liveKind(selectedWidgetId) != selectedKind) {
                        throw IllegalStateException("Widget provider is no longer live")
                    }
                    storage.widgets.configure(WidgetInstance(selectedWidgetId, selectedKind))
                }
                val snapshot = try {
                    withContext(Dispatchers.IO) {
                        WidgetRenderer.snapshotForRendering(
                            store = storage.widgets,
                            widgetId = selectedWidgetId,
                            now = Instant.now(),
                            deviceZone = ZoneId.systemDefault(),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                val manager = AppWidgetManager.getInstance(this@WidgetConfigurationActivity)
                manager.updateAppWidget(
                    selectedWidgetId,
                    WidgetRenderer.render(
                        this@WidgetConfigurationActivity,
                        selectedWidgetId,
                        snapshot,
                        failure = snapshot == null,
                        options = AppWidgetManager.getInstance(this@WidgetConfigurationActivity)
                            .getAppWidgetOptions(selectedWidgetId),
                    ),
                )
                manager.notifyAppWidgetViewDataChanged(selectedWidgetId, R.id.widget_data_rows)
                try {
                    WidgetRefreshCoordinator.request(this@WidgetConfigurationActivity)
                } catch (_: Exception) {
                    // The direct RemoteViews update above is already complete.
                }
                setResult(Activity.RESULT_OK, resultIntent())
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveFailed = true
                saving = false
            }
        }
    }

    private fun liveKind(id: Int): WidgetKind? {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || id <= 0) return null
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(id)?.provider ?: return null
        return when (provider) {
            ComponentName(this, HabitsWidgetProvider::class.java) -> {
                id.takeIf { it in manager.getAppWidgetIds(provider) }?.let { WidgetKind.HABITS }
            }
            ComponentName(this, TasksWidgetProvider::class.java) -> {
                id.takeIf { it in manager.getAppWidgetIds(provider) }?.let { WidgetKind.TASKS }
            }
            else -> null
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    companion object {
        private const val STATE_LOADED = "widget_config_loaded"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
internal fun ConfigurationContent(
    kind: WidgetKind,
    loaded: Boolean,
    saving: Boolean,
    loadFailed: Boolean,
    saveFailed: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    BackHandler(enabled = saving) { }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            Text(
                stringResource(if (kind == WidgetKind.HABITS) R.string.widget_config_habits_title else R.string.widget_config_tasks_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            when {
                !loaded && !loadFailed -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.widget_config_loading))
                }
                loadFailed -> {
                    Text(stringResource(R.string.widget_config_load_error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetryLoad) { Text(stringResource(R.string.retry)) }
                }
                else -> {
                    Text(stringResource(R.string.widget_config_content_description))
                    Text(stringResource(R.string.widget_config_scroll_resize), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.widget_config_privacy_note), style = MaterialTheme.typography.bodySmall)
                    com.luma.lifeos.ui.widgets.WidgetExample(kind)
                    if (saveFailed) {
                        Text(stringResource(R.string.widget_config_save_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = onCancel, enabled = !saving) {
                    Text(stringResource(R.string.widget_config_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = loaded && !loadFailed && !saving,
                ) {
                    Text(stringResource(if (saving) R.string.widget_config_saving else R.string.widget_config_save))
                }
            }
        }
    }
}
