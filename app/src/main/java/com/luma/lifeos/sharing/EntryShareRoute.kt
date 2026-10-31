package com.luma.lifeos.sharing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.lock.AuthPurpose
import com.luma.lifeos.lock.LockSensitiveOperationGuard
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.PendingSensitiveOperation
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.DetailTopBar
import com.luma.lifeos.ui.privacy.LockSensitiveActionDialog
import com.luma.lifeos.ui.mood.moodScoreLabel
import java.time.ZoneId
import java.time.format.FormatStyle

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.activity()
    else -> null
}

@Composable
fun EntryShareRoute(
    repository: TimelineRepository,
    request: EntryShareRequest,
    zone: ZoneId,
    onBack: () -> Unit,
    lockGuard: LockSensitiveOperationGuard? = null,
) {
    val androidContext = LocalContext.current
    val policy = rememberDisplayTimePolicy()
    val labels = (1..5).associateWith { stringResource(moodScoreLabel(it)) }
    val context = EntryShareTextContext(policy.locale, stringResource(R.string.share_memory),
        { androidContext.getString(R.string.share_feeling, it) }, stringResource(R.string.share_emotions), stringResource(R.string.share_note),
        stringResource(R.string.share_source), stringResource(R.string.share_people), stringResource(R.string.share_place),
        stringResource(R.string.share_energy), stringResource(R.string.share_stress), labels,
        { instant, displayZone -> instant.atZone(displayZone).format(policy.date(FormatStyle.LONG)) },
        { emotion -> com.luma.lifeos.language.builtinEmotionResource(emotion.id, emotion.name)?.let { androidContext.getString(it) } ?: emotion.name },
        { instant, displayZone -> instant.atZone(displayZone).format(policy.dateTime(FormatStyle.LONG)) })
    val key = "${policy.locale.toLanguageTag()}|${policy.use24Hour}"
    val factory = remember(repository, request) { viewModelFactory { initializer { EntryShareViewModel(repository, request) } } }
    val model: EntryShareViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val chooserTitle = stringResource(R.string.share_action)
    var showSensitivePrompt by remember { mutableStateOf(false) }
    var sensitivePending by remember { mutableStateOf<PendingSensitiveOperation?>(null) }
    var sensitiveError by remember { mutableStateOf(false) }

    fun launchShare(proof: OperationProof? = null) {
        val pending = sensitivePending
        model.share(zone, key, { text ->
            androidContext.activity()?.let { AndroidTextShareLauncher(it, chooserTitle).launch(text) }
                ?: TextShareLaunchResult.UNAVAILABLE
        }) { operation ->
            if (lockGuard != null) {
                lockGuard.executeSensitive(AuthPurpose.SHARE, pending, proof, operation)
            } else operation()
        }
        sensitivePending = null
    }

    fun requestShare() {
        if (lockGuard?.requiresAuthentication() == true) {
            sensitivePending = lockGuard.begin(AuthPurpose.SHARE)
            if (sensitivePending == null) sensitiveError = true else showSensitivePrompt = true
        } else launchShare()
    }
    LaunchedEffect(model) { model.start(zone, context, key) }
    LaunchedEffect(zone, key, state) { model.invalidateContext(zone, key) }
    DisposableEffect(model) { onDispose { model.cancelPendingLaunch() } }
    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.share_preview), backTag = "share_back", onBack = onBack) },
        bottomBar = {
            val ready = state as? ShareStatus.Ready
            if (ready != null) Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(16.dp)) {
                if (ready.stale) Button(onClick = { model.load(zone, context, key) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_refresh)) }
                else Button(onClick = ::requestShare, enabled = !ready.busy && ready.pendingOptions == null,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_action)) }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                ShareStatus.Loading -> CircularProgressIndicator()
                ShareStatus.Unavailable -> AccessibleFeedback(stringResource(R.string.share_unavailable))
                ShareStatus.Error -> {
                    AccessibleFeedback(stringResource(R.string.share_load_error))
                    TextButton(onClick = { model.load(zone, context, key) }) { Text(stringResource(R.string.retry)) }
                }
                is ShareStatus.Ready -> {
                    if (s.stale) AccessibleFeedback(stringResource(R.string.share_stale))
                    if (s.launchError) AccessibleFeedback(stringResource(R.string.share_launch_error))
                    when (s.textError) {
                        EntryShareTextError.EMPTY -> AccessibleFeedback(stringResource(R.string.share_empty))
                        EntryShareTextError.TOO_LARGE -> AccessibleFeedback(stringResource(R.string.share_too_large))
                        null -> Unit
                    }
                    val record = s.snapshot.record
                    val marker = request.kind == EntryShareKind.MEMORY && record.event.type != EventType.MEMORY
                    val enabled = !s.stale && !s.busy
                    @Composable fun Option(label: String, selected: Boolean, change: (Boolean) -> EntryShareOptions) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, Modifier.weight(1f).padding(vertical = 12.dp))
                            Switch(selected, onCheckedChange = { model.option(change(it), context) }, enabled = enabled)
                        }
                    }
                    Option(stringResource(if (request.kind == EntryShareKind.MOOD) R.string.share_date_time else R.string.share_date), s.options.includeDate) { s.options.copy(includeDate = it) }
                    Option(stringResource(R.string.share_note), s.options.includeNote) { s.options.copy(includeNote = it) }
                    if (record.people.isNotEmpty()) Option(stringResource(R.string.share_people), s.options.includePeople) { s.options.copy(includePeople = it) }
                    if (record.place != null) Option(stringResource(R.string.share_place), s.options.includePlace) { s.options.copy(includePlace = it) }
                    if (marker) {
                        Option(stringResource(R.string.share_include_source), s.options.includeSourceContent) { s.options.copy(includeSourceContent = it) }
                        Text(stringResource(R.string.share_source_explanation), style = MaterialTheme.typography.bodySmall)
                    }
                    if (request.kind == EntryShareKind.MOOD || (marker && s.options.includeSourceContent)) record.mood?.let { mood ->
                        if (mood.energy != null) Option(stringResource(R.string.share_energy), s.options.includeEnergy) { s.options.copy(includeEnergy = it) }
                        if (mood.stress != null) Option(stringResource(R.string.share_stress), s.options.includeStress) { s.options.copy(includeStress = it) }
                    }
                    Text(stringResource(R.string.share_privacy_explanation), style = MaterialTheme.typography.bodySmall)
                    if (record.photos.isNotEmpty()) Text(stringResource(R.string.share_no_photos), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = s.text, onValueChange = model::edit, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), label = { Text(stringResource(R.string.share_text)) })
                    if (s.pendingOptions != null) AlertDialog(onDismissRequest = model::cancelRegenerate,
                        title = { Text(stringResource(R.string.share_regenerate)) }, text = { Text(stringResource(R.string.share_regenerate_explanation)) },
                        confirmButton = { TextButton(onClick = { model.regenerate(context) }) { Text(stringResource(R.string.share_regenerate)) } },
                        dismissButton = { TextButton(onClick = model::cancelRegenerate) { Text(stringResource(R.string.cancel)) } })
                }
            }
            if (sensitiveError) AccessibleFeedback(stringResource(R.string.app_lock_error_unavailable))
        }
    }
    val pending = sensitivePending
    if (showSensitivePrompt && lockGuard != null && pending != null) {
        LockSensitiveActionDialog(
            guard = lockGuard,
            pending = pending,
            onAuthorized = { proof -> showSensitivePrompt = false; launchShare(proof) },
            onCancel = { showSensitivePrompt = false; sensitivePending = null },
        )
    }
}
