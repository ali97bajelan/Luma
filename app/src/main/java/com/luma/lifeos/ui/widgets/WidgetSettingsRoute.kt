package com.luma.lifeos.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetInstance
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetStore
import com.luma.lifeos.ui.settings.SettingsSectionPage
import com.luma.lifeos.widget.HabitsWidgetProvider
import com.luma.lifeos.widget.QuickCaptureWidgetProvider
import com.luma.lifeos.widget.TasksWidgetProvider
import com.luma.lifeos.widget.WidgetConfigurationActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WidgetSettingsRoute(widgetStore: WidgetStore?, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var instances by remember { mutableStateOf<List<WidgetInstance>>(emptyList()) }
    var pinStatus by remember { mutableIntStateOf(0) }
    val configurationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshKey += 1 }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshKey, widgetStore) {
        loading = true
        instances = if (widgetStore == null) {
            emptyList()
        } else {
            val manager = AppWidgetManager.getInstance(context)
            val live = WidgetKind.entries.flatMap { kind ->
                val provider = providerComponent(context, kind)
                manager.getAppWidgetIds(provider).map { kind to it }
            }
            withContext(Dispatchers.IO) {
                live.map { (kind, id) ->
                    widgetStore.configurationOrNull(id)?.takeIf { it.kind == kind }
                        ?: WidgetInstance(id = id, kind = kind, showDetails = false)
                }.sortedWith(compareBy<WidgetInstance>({ it.kind.ordinal }, { it.id }))
            }
        }
        loading = false
    }

    fun configure(instance: WidgetInstance) {
        val intent = Intent(context, WidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, instance.id)
        configurationLauncher.launch(intent)
    }

    fun requestPin(provider: ComponentName, callbackKind: WidgetKind? = null) {
        val manager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !manager.isRequestPinAppWidgetSupported) {
            pinStatus = R.string.widget_settings_pin_unsupported
            return
        }
        val callback = callbackKind?.let { kind ->
            val callbackIntent = Intent(context, WidgetConfigurationActivity::class.java).apply {
                action = "${context.packageName}.widget.CONFIGURE_PINNED_${kind.name}"
                data = Uri.parse("luma-widget-config://pin/${kind.name.lowercase()}")
            }
            PendingIntent.getActivity(
                context,
                kind.ordinal + PIN_REQUEST_BASE,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }
        pinStatus = try {
            if (manager.requestPinAppWidget(provider, null, callback)) {
                R.string.widget_settings_pin_requested
            } else {
                R.string.widget_settings_pin_unsupported
            }
        } catch (_: Exception) {
            R.string.widget_settings_pin_error
        }
    }

    SettingsSectionPage(
        title = R.string.settings_widgets_title,
        onBack = onBack,
        screenTag = "screen_settings_widgets",
    ) {
        Text(stringResource(R.string.widget_settings_intro))
        ProviderCard(
            title = R.string.widget_habits_title,
            description = R.string.widget_habits_description,
            onAdd = { requestPin(providerComponent(context, WidgetKind.HABITS), WidgetKind.HABITS) },
        )
        ProviderCard(
            title = R.string.widget_tasks_title,
            description = R.string.widget_tasks_description,
            onAdd = { requestPin(providerComponent(context, WidgetKind.TASKS), WidgetKind.TASKS) },
        )
        ProviderCard(
            title = R.string.widget_quick_capture_name,
            description = R.string.widget_quick_capture_description,
            onAdd = {
                requestPin(ComponentName(context, QuickCaptureWidgetProvider::class.java))
            },
        )
        Text(stringResource(R.string.widget_settings_existing_heading), style = MaterialTheme.typography.titleMedium)
        when {
            loading -> CircularProgressIndicator()
            instances.isEmpty() -> Text(stringResource(R.string.widget_settings_no_instances))
            else -> instances.forEach { instance ->
                InstanceCard(instance = instance, onConfigure = { configure(instance) })
            }
        }
        if (pinStatus != 0) {
            Text(stringResource(pinStatus), style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.widget_settings_picker_steps), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.widget_settings_privacy_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProviderCard(title: Int, description: Int, onAdd: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onAdd) { Text(stringResource(R.string.widget_settings_add)) }
        }
    }
}

@Composable
private fun InstanceCard(instance: WidgetInstance, onConfigure: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.widget_settings_instance,
                    stringResource(if (instance.kind == WidgetKind.HABITS) R.string.widget_habits_title else R.string.widget_tasks_title),
                    instance.id,
                ),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onConfigure) { Text(stringResource(R.string.widget_settings_configure)) }
        }
    }
}

private fun providerComponent(context: Context, kind: WidgetKind) = when (kind) {
    WidgetKind.HABITS -> ComponentName(context, HabitsWidgetProvider::class.java)
    WidgetKind.TASKS -> ComponentName(context, TasksWidgetProvider::class.java)
}

private suspend fun WidgetStore.configurationOrNull(widgetId: Int): WidgetInstance? = try {
    configuration(widgetId)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

private const val PIN_REQUEST_BASE = 7400
