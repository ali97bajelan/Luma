package com.luma.lifeos.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TaskReminderRepository
import com.luma.lifeos.reminders.ReminderPermissionSwitch
import com.luma.lifeos.reminders.ReminderStatusStore
import com.luma.lifeos.reminders.ReminderDeliveryStatus
import com.luma.lifeos.reminders.reminderStatusText
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import com.luma.lifeos.reminders.notificationPermissionGranted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun TodoEditorRoute(
    repository: TodoRepository,
    taskId: UUID?,
    initialDueDate: LocalDate?,
    onClose: (TodoEditorResult?) -> Unit,
    reminderRepository: TaskReminderRepository? = null,
    restoreGeneration: Int = 0,
    requestDelete: Boolean = false,
) {
    val factory = remember(repository, taskId, initialDueDate) {
        viewModelFactory {
            initializer {
                TodoEditorViewModel(repository, taskId, initialDueDate, createSavedStateHandle())
            }
        }
    }
    val editor: TodoEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    val latestOnClose by rememberUpdatedState(onClose)
    val handledRestore = rememberSaveable { mutableStateOf(restoreGeneration) }
    var reminderEnabled by rememberSaveable(taskId?.toString()) { mutableStateOf(false) }
    var reminderStatus by rememberSaveable(taskId?.toString()) { mutableStateOf(com.luma.lifeos.R.string.reminder_status_configured) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(taskId, reminderRepository, restoreGeneration) {
        reminderEnabled = taskId?.let { reminderRepository?.configuration(it)?.enabled } == true
        if (taskId != null && reminderEnabled) {
            reminderStatus = reminderStatusText(ReminderStatusStore(context).get("task", taskId.toString()))
        }
    }

    LaunchedEffect(restoreGeneration) {
        if (handledRestore.value != restoreGeneration) {
            handledRestore.value = restoreGeneration
            editor.invalidateAfterRestore()
        }
    }

    LaunchedEffect(state.ready, requestDelete) {
        if (requestDelete && state.ready) editor.requestDelete()
    }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            if (taskId != null) enqueueReminderReconciliation(context)
            latestOnClose(state.result)
        }
    }

    TodoEditorScreen(
        state = state,
        onClose = { latestOnClose(null) },
        onSave = editor::save,
        onRetry = editor::retry,
        onReload = editor::reload,
        onTitle = editor::setTitle,
        onNote = editor::setNote,
        onDueDate = editor::setDueDate,
        onDueTime = editor::setDueTime,
        onSetCompleted = editor::setCompleted,
        onRequestDelete = editor::requestDelete,
        onConfirmDelete = editor::confirmDelete,
        onCancelDelete = editor::cancelDelete,
        reminderEnabled = reminderEnabled,
        reminderStatus = reminderStatus,
        reminderAvailable = reminderRepository != null && state.editing && !state.taskCompleted && taskId != null,
        onReminderEnabled = { enabled ->
            if (taskId != null && reminderRepository != null) {
                reminderEnabled = enabled
                scope.launch {
                    runCatching { reminderRepository.save(taskId, enabled) }
                        .onSuccess {
                            val deliveryStatus = when {
                                !enabled -> ReminderDeliveryStatus.OFF
                                !notificationPermissionGranted(context) -> ReminderDeliveryStatus.BLOCKED
                                else -> ReminderDeliveryStatus.PENDING
                            }
                            reminderStatus = reminderStatusText(deliveryStatus)
                            ReminderStatusStore(context).set("task", taskId.toString(),
                                deliveryStatus)
                            enqueueReminderReconciliation(context)
                        }
                        .onFailure {
                            reminderEnabled = !enabled
                            reminderStatus = com.luma.lifeos.R.string.reminder_save_failed
                        }
                }
            }
        },
    )
}
