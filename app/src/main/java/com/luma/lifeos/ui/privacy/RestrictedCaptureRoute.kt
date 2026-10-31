package com.luma.lifeos.ui.privacy

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.MemoryInput
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.lock.CaptureDraft
import com.luma.lifeos.lock.CaptureInput
import com.luma.lifeos.lock.CaptureKind
import com.luma.lifeos.lock.CaptureResult
import com.luma.lifeos.lock.CaptureResumeResult
import com.luma.lifeos.lock.CreateSession
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.RestrictedCaptureService
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaChoiceRow
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation
import java.io.File
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory

private data class RestrictedCaptureState(
    val loading: Boolean = true,
    val draftIds: List<UUID> = emptyList(),
    val session: CreateSession? = null,
    val draft: CaptureDraft? = null,
    val busy: Boolean = false,
    val pickerActive: Boolean = false,
    val error: CaptureUiError? = null,
    val confirmDiscard: Boolean = false,
    val saved: Boolean = false,
)

private enum class CaptureUiError { VALIDATION, RETRY, CONFLICT, STORAGE }

private class RestrictedCaptureViewModel(private val service: RestrictedCaptureService) : ViewModel() {
    private val mutableState = MutableStateFlow(RestrictedCaptureState())
    val state: StateFlow<RestrictedCaptureState> = mutableState.asStateFlow()
    private val persistenceMutex = Mutex()
    private var pendingWrite: Job? = null

    init { refreshDrafts() }

    fun refreshDrafts() = viewModelScope.launch {
        mutableState.value = state.value.copy(loading = true)
        try {
            val ids = service.recoverableDraftIds()
            mutableState.value = state.value.copy(loading = false, draftIds = ids)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = state.value.copy(loading = false, error = CaptureUiError.STORAGE)
        }
    }

    fun begin(kind: CaptureKind) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            val session = service.begin(kind)
            val draft = service.activeDraft(session)
            mutableState.value = if (draft == null) state.value.copy(busy = false, error = CaptureUiError.STORAGE)
            else state.value.copy(busy = false, session = session, draft = draft, draftIds = emptyList())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = state.value.copy(busy = false, error = CaptureUiError.STORAGE)
        }
    }

    fun resume(id: UUID, proof: OperationProof? = null, onUnlock: (UUID) -> Unit = {}) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        when (val result = service.resume(id, proof)) {
            is CaptureResumeResult.Ready -> mutableState.value = state.value.copy(
                busy = false, session = result.session, draft = result.draft, draftIds = emptyList(), error = null,
            )
            is CaptureResumeResult.RequiresUnlock -> {
                mutableState.value = state.value.copy(busy = false)
                onUnlock(id)
            }
            CaptureResumeResult.Missing -> mutableState.value = state.value.copy(busy = false, error = CaptureUiError.STORAGE)
            CaptureResumeResult.Unavailable -> mutableState.value = state.value.copy(busy = false, error = CaptureUiError.STORAGE)
        }
    }

    fun setField(key: String, value: String) {
        val current = state.value
        val session = current.session ?: return
        val draft = current.draft ?: return
        mutableState.value = current.copy(draft = draft.copy(
            revision = (draft.revision + 1).coerceAtMost(Long.MAX_VALUE),
            fields = draft.fields + (key to value),
        ), error = null)
        pendingWrite?.cancel()
        pendingWrite = viewModelScope.launch {
            delay(180)
            persistLatest(session)
        }
    }

    fun addPhoto(source: String, afterImport: () -> Unit = {}) = viewModelScope.launch {
        val session = state.value.session ?: run {
            afterImport()
            return@launch
        }
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            persistenceMutex.withLock {
                val latest = state.value.draft ?: return@withLock
                service.updateDraft(session, latest.fields)
                val updated = service.addPhoto(session, source)
                if (updated == null) mutableState.value = state.value.copy(error = CaptureUiError.RETRY)
                else mutableState.value = state.value.copy(draft = updated)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = state.value.copy(error = CaptureUiError.RETRY)
        } finally {
            mutableState.value = state.value.copy(busy = false)
            afterImport()
        }
    }

    fun removePhoto(id: UUID) = viewModelScope.launch {
        val session = state.value.session ?: return@launch
        mutableState.value = state.value.copy(busy = true)
        try {
            service.removePhoto(session, id)?.let { mutableState.value = state.value.copy(draft = it, error = null) }
                ?: run { mutableState.value = state.value.copy(error = CaptureUiError.RETRY) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = state.value.copy(error = CaptureUiError.RETRY)
        } finally { mutableState.value = state.value.copy(busy = false) }
    }

    fun save(zone: ZoneId, onSaved: () -> Unit) = viewModelScope.launch {
        if (state.value.busy) return@launch
        val session = state.value.session ?: return@launch
        mutableState.value = state.value.copy(busy = true, error = null)
        val result = try {
            persistenceMutex.withLock {
                val latest = state.value.draft ?: return@withLock CaptureResult.RetryableFailure
                service.updateDraft(session, latest.fields)
                val input = captureInput(latest.copy(fields = state.value.draft?.fields ?: latest.fields), zone)
                service.save(session, input)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.copy(busy = false)
            throw cancelled
        } catch (_: DateTimeException) {
            CaptureResult.ValidationError
        } catch (_: IllegalArgumentException) {
            CaptureResult.ValidationError
        } catch (_: Exception) {
            CaptureResult.RetryableFailure
        }
        when (result) {
            CaptureResult.Saved -> {
                mutableState.value = state.value.copy(
                    busy = false, session = null, draft = null, draftIds = emptyList(), saved = true, error = null,
                )
                onSaved()
            }
            CaptureResult.ValidationError -> mutableState.value = state.value.copy(busy = false, error = CaptureUiError.VALIDATION)
            CaptureResult.RetryableFailure -> mutableState.value = state.value.copy(busy = false, error = CaptureUiError.RETRY)
            CaptureResult.Conflict -> mutableState.value = state.value.copy(busy = false, error = CaptureUiError.CONFLICT)
        }
    }

    fun requestDiscard() { mutableState.value = state.value.copy(confirmDiscard = true) }
    fun dismissDiscard() { mutableState.value = state.value.copy(confirmDiscard = false) }

    fun discard(onDiscarded: () -> Unit) = viewModelScope.launch {
        val session = state.value.session
        mutableState.value = state.value.copy(busy = true, confirmDiscard = false)
        try {
            if (session != null) service.cancel(session)
            mutableState.value = RestrictedCaptureState(loading = false)
            onDiscarded()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = state.value.copy(busy = false, error = CaptureUiError.RETRY)
        }
    }

    fun setPickerActive(active: Boolean) { mutableState.value = state.value.copy(pickerActive = active) }

    fun suspendForDeparture() = viewModelScope.launch {
        val session = state.value.session ?: return@launch
        try {
            persistLatest(session)
            service.suspendSession(session)
            mutableState.value = RestrictedCaptureState(loading = false, draftIds = service.recoverableDraftIds())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = RestrictedCaptureState(loading = false, error = CaptureUiError.STORAGE)
        }
    }

    suspend fun preview(photoId: UUID): ImageBitmap? {
        val session = state.value.session ?: return null
        return try {
            val bytes = service.previewPhoto(session, photoId) ?: return null
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
    }

    private suspend fun persistLatest(session: CreateSession) = persistenceMutex.withLock {
        val current = state.value
        if (current.session !== session) return@withLock
        current.draft?.let { service.updateDraft(session, it.fields) }
    }

    private fun captureInput(draft: CaptureDraft, zone: ZoneId): CaptureInput {
        val fields = draft.fields
        fun required(name: String) = fields[name].orEmpty()
        fun optional(name: String) = fields[name].orEmpty().trim().takeIf(String::isNotEmpty)
        val time = Instant.parse(required("occurredAt"))
        return when (draft.kind) {
            CaptureKind.MEMORY -> CaptureInput.Memory(MemoryInput(
                note = required("note"), occurredAt = time, title = optional("title"),
                importance = optional("importance")?.toInt(), photos = draft.photos,
            ))
            CaptureKind.JOURNAL -> CaptureInput.Journal(JournalInput(
                body = required("body"), occurredAt = time, title = optional("title"),
                tagNames = optional("tags")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.toSet().orEmpty(),
                photos = draft.photos,
            ))
            CaptureKind.MOOD -> CaptureInput.Mood(MoodInput(
                score = required("score").toInt(), occurredAt = time, energy = optional("energy")?.toInt(),
                stress = optional("stress")?.toInt(), note = optional("note"),
            ))
            CaptureKind.TASK -> CaptureInput.Task(TodoInput(
                title = required("title"), note = optional("note"),
                dueDate = optional("dueDate")?.let(LocalDate::parse), dueTime = optional("dueTime")?.let(LocalTime::parse),
            ))
            CaptureKind.HABIT_DEFINITION -> CaptureInput.HabitDefinition(HabitSaveInput(
                name = required("name"), unitLabel = optional("unit"), start = LocalDate.parse(required("start")),
                target = required("target").toInt(),
                weekdays = required("weekdays").split(',').map { java.time.DayOfWeek.of(it.toInt()) }.toSet(),
                zone = ZoneId.of(required("zone").ifBlank { zone.id }),
            ))
        }
    }
}

@Composable
fun RestrictedCaptureRoute(
    service: RestrictedCaptureService,
    onBack: () -> Unit,
    onRequestDraftUnlock: (UUID, (OperationProof) -> Unit) -> Unit,
) {
    val factory = remember(service) { viewModelFactory { initializer { RestrictedCaptureViewModel(service) } } }
    val vm: RestrictedCaptureViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    val lockSnapshot by service.lockSnapshot.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUriValue by rememberSaveable { mutableStateOf<String?>(null) }
    val screenshotProtectionRequired = when (lockSnapshot.loadStatus) {
        com.luma.lifeos.domain.lock.LockLoadStatus.UNKNOWN,
        com.luma.lifeos.domain.lock.LockLoadStatus.ERROR -> true
        com.luma.lifeos.domain.lock.LockLoadStatus.READY ->
            lockSnapshot.policy?.let { it.enabled && it.screenshotsProtected } == true
    }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        vm.setPickerActive(false)
        uri?.let { vm.addPhoto(it.toString()) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        vm.setPickerActive(false)
        val uri = cameraUriValue?.let(Uri::parse)
        val filePath = cameraFilePath
        if (saved && uri != null) vm.addPhoto(uri.toString()) { filePath?.let(::File)?.delete() }
        else filePath?.let(::File)?.delete()
        cameraFilePath = null
        cameraUriValue = null
    }
    DisposableEffect(lifecycleOwner, activity, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true &&
                !vm.state.value.pickerActive
            ) vm.suspendForDeparture()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(activity, screenshotProtectionRequired) {
        val window = activity?.window
        if (screenshotProtectionRequired) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (screenshotProtectionRequired) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (activity?.isChangingConfigurations != true) cameraFilePath?.let(::File)?.delete()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            delay(700)
            onBack()
        }
    }

    AppPageScaffold(
        title = stringResource(R.string.restricted_capture_title),
        navigation = PageNavigation.BACK,
        onNavigate = vm::requestDiscard,
        navigationEnabled = !state.busy,
        titleTag = "restricted_capture_heading",
        navigationTag = "restricted_capture_back",
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                    .testTag("restricted_capture_screen"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.loading || state.busy && state.session == null) CircularProgressIndicator()
                else if (state.saved) {
                    Text(stringResource(R.string.restricted_capture_saved), Modifier.testTag("restricted_capture_saved"))
                } else if (state.session == null) {
                    if (state.draftIds.isNotEmpty()) {
                        Text(stringResource(R.string.restricted_capture_resume_heading),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.restricted_capture_resume_body))
                        state.draftIds.forEachIndexed { index, id ->
                            LumaChoiceRow(
                                title = stringResource(R.string.restricted_capture_private_draft, index + 1),
                                icon = R.drawable.ic_nav_timeline,
                                onClick = { vm.resume(id) { draftId ->
                                    onRequestDraftUnlock(draftId) { proof -> vm.resume(draftId, proof) }
                                } },
                                modifier = Modifier.testTag("restricted_capture_resume_$index"),
                                showChevron = true,
                            )
                        }
                    }
                    Text(stringResource(R.string.restricted_capture_choose_type),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    CaptureKind.entries.forEach { kind ->
                        LumaChoiceRow(
                            title = stringResource(kind.labelResource()), icon = R.drawable.ic_nav_timeline,
                            onClick = { vm.begin(kind) }, modifier = Modifier.testTag("restricted_capture_kind_${kind.name.lowercase()}"),
                            showChevron = true,
                        )
                    }
                    TextButton(onClick = onBack, modifier = Modifier.testTag("restricted_capture_exit")) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    val draft = requireNotNull(state.draft)
                    Text(stringResource(R.string.restricted_capture_private_notice), style = MaterialTheme.typography.bodyMedium)
                    CaptureFields(draft, state.busy, vm::setField)
                    if (draft.kind in setOf(CaptureKind.MEMORY, CaptureKind.JOURNAL)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !state.busy && draft.photos.size < 20, onClick = {
                                vm.setPickerActive(true)
                                picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                            }, modifier = Modifier.testTag("restricted_capture_add_photo")) {
                                Text(stringResource(R.string.restricted_capture_choose_photo))
                            }
                            TextButton(enabled = !state.busy && draft.photos.size < 20, onClick = {
                                val directory = File(context.cacheDir, "restricted-capture").apply { mkdirs() }
                                val file = File(directory, "${UUID.randomUUID()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.restricted_capture.fileprovider", file)
                                cameraFilePath = file.absolutePath
                                cameraUriValue = uri.toString()
                                vm.setPickerActive(true)
                                camera.launch(uri)
                            }, modifier = Modifier.testTag("restricted_capture_take_photo")) {
                                Text(stringResource(R.string.restricted_capture_take_photo))
                            }
                        }
                        if (draft.photos.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                draft.photos.forEachIndexed { index, photo ->
                                    RestrictedCapturePhoto(vm, photo, index, !state.busy) { vm.removePhoto(photo.id) }
                                }
                            }
                        }
                    }
                    state.error?.let { error ->
                        val resource = when (error) {
                            CaptureUiError.VALIDATION -> R.string.restricted_capture_validation_error
                            CaptureUiError.RETRY -> R.string.restricted_capture_retry_error
                            CaptureUiError.CONFLICT -> R.string.restricted_capture_generic_error
                            CaptureUiError.STORAGE -> R.string.restricted_capture_storage_error
                        }
                        AccessibleFeedback(stringResource(resource), Modifier.testTag("restricted_capture_error"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = !state.busy, onClick = { vm.save(ZoneId.systemDefault()) { } },
                            modifier = Modifier.testTag("restricted_capture_save")) {
                            Text(stringResource(R.string.save))
                        }
                        TextButton(enabled = !state.busy, onClick = vm::requestDiscard,
                            modifier = Modifier.testTag("restricted_capture_cancel")) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
                state.error?.takeIf { state.session == null }?.let {
                    AccessibleFeedback(stringResource(R.string.restricted_capture_storage_error), Modifier.testTag("restricted_capture_load_error"))
                }
            }
        }
    }
    if (state.confirmDiscard) AlertDialog(
        onDismissRequest = vm::dismissDiscard,
        title = { Text(stringResource(R.string.restricted_capture_discard_title)) },
        text = { Text(stringResource(R.string.restricted_capture_discard_body)) },
        confirmButton = { TextButton(onClick = { vm.discard(onBack) }, modifier = Modifier.testTag("restricted_capture_discard_confirm")) {
            Text(stringResource(R.string.restricted_capture_discard))
        } },
        dismissButton = { TextButton(onClick = vm::dismissDiscard) { Text(stringResource(R.string.keep_editing)) } },
    )
}

@Composable
private fun CaptureFields(draft: CaptureDraft, enabled: Boolean, change: (String, String) -> Unit) {
    val values = draft.fields
    when (draft.kind) {
        CaptureKind.MEMORY -> {
            CaptureField(R.string.restricted_capture_title_field, "title", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_note, "note", values, enabled, change, minLines = 4)
            CaptureField(R.string.restricted_capture_importance, "importance", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_datetime, "occurredAt", values, enabled, change, singleLine = true)
        }
        CaptureKind.JOURNAL -> {
            CaptureField(R.string.restricted_capture_title_field, "title", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_body, "body", values, enabled, change, minLines = 6)
            CaptureField(R.string.restricted_capture_tags, "tags", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_datetime, "occurredAt", values, enabled, change, singleLine = true)
        }
        CaptureKind.MOOD -> {
            CaptureField(R.string.restricted_capture_score, "score", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_energy, "energy", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_stress, "stress", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_note, "note", values, enabled, change, minLines = 3)
            CaptureField(R.string.restricted_capture_datetime, "occurredAt", values, enabled, change, singleLine = true)
        }
        CaptureKind.TASK -> {
            CaptureField(R.string.restricted_capture_task_title, "title", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_note, "note", values, enabled, change, minLines = 3)
            CaptureField(R.string.restricted_capture_due_date, "dueDate", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_due_time, "dueTime", values, enabled, change, singleLine = true)
        }
        CaptureKind.HABIT_DEFINITION -> {
            CaptureField(R.string.restricted_capture_habit_name, "name", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_unit, "unit", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_habit_start, "start", values, enabled, change, singleLine = true)
            CaptureField(R.string.restricted_capture_habit_target, "target", values, enabled, change, singleLine = true)
            Text(stringResource(R.string.restricted_capture_weekdays), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                java.time.DayOfWeek.entries.forEach { day ->
                    val code = day.value.toString()
                    val selected = code in values["weekdays"].orEmpty().split(',')
                    FilterChip(selected = selected, enabled = enabled, onClick = {
                        val next = if (selected) values["weekdays"].orEmpty().split(',').filterNot { it == code }
                        else (values["weekdays"].orEmpty().split(',') + code).filter(String::isNotBlank)
                        change("weekdays", next.distinct().sorted().joinToString(","))
                    }, label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) })
                }
            }
        }
    }
}

@Composable
private fun CaptureField(
    label: Int,
    key: String,
    values: Map<String, String>,
    enabled: Boolean,
    change: (String, String) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = values[key].orEmpty(), onValueChange = { change(key, it) }, enabled = enabled,
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth().testTag("restricted_capture_field_$key"),
        singleLine = singleLine, minLines = minLines,
    )
}

@Composable
private fun RestrictedCapturePhoto(vm: RestrictedCaptureViewModel, photo: PhotoAttachment, index: Int,
    enabled: Boolean, onRemove: () -> Unit) {
    var bitmap by remember(vm, photo.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(vm, photo.id) { bitmap = vm.preview(photo.id) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bitmap?.let { Image(it, contentDescription = stringResource(R.string.photo_number, index + 1),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)) }
                ?: Text(stringResource(R.string.restricted_capture_photo_added))
            TextButton(enabled = enabled, onClick = onRemove, modifier = Modifier.testTag("restricted_capture_remove_photo_${photo.id}")) {
                Text(stringResource(R.string.photo_remove))
            }
        }
    }
}

private fun CaptureKind.labelResource(): Int = when (this) {
    CaptureKind.MEMORY -> R.string.restricted_capture_memory
    CaptureKind.JOURNAL -> R.string.restricted_capture_journal
    CaptureKind.MOOD -> R.string.restricted_capture_mood
    CaptureKind.TASK -> R.string.restricted_capture_task
    CaptureKind.HABIT_DEFINITION -> R.string.restricted_capture_habit
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
