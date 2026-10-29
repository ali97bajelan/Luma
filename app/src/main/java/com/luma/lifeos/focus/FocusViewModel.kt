package com.luma.lifeos.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusWriteResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusScreenState(
    val runtime: FocusRuntimeState,
    val busy: Boolean = false,
    val operationFailed: Boolean = false,
    val backPromptVisible: Boolean = false,
    val emergencyPromptVisible: Boolean = false,
    val shortcutUnavailable: Boolean = false,
)

class FocusViewModel(private val runtime: FocusRuntime) : ViewModel() {
    private sealed interface PendingOperation {
        data class Start(val commandId: UUID, val sessionId: UUID) : PendingOperation
        data class Action(val commandId: UUID, val session: FocusSession, val action: FocusAction) : PendingOperation
        data class Recovery(val session: FocusSession, val choice: FocusRecoveryChoice) : PendingOperation
    }

    private data class LocalState(
        val busy: Boolean = false,
        val operationFailed: Boolean = false,
        val backPromptVisible: Boolean = false,
        val emergencyPromptVisible: Boolean = false,
        val emergencyMarkerSaved: Boolean? = null,
        val shortcutUnavailable: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())
    private var pendingOperation: PendingOperation? = null

    val state: StateFlow<FocusScreenState> = combine(runtime.state, local) { runtimeState, localState ->
        FocusScreenState(
            runtime = runtimeState.copy(
                emergencyMarkerSaved = localState.emergencyMarkerSaved ?: runtimeState.emergencyMarkerSaved,
            ),
            busy = localState.busy,
            operationFailed = localState.operationFailed,
            backPromptVisible = localState.backPromptVisible,
            emergencyPromptVisible = localState.emergencyPromptVisible,
            shortcutUnavailable = localState.shortcutUnavailable || runtimeState.shortcutFailed,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FocusScreenState(runtime.state.value),
    )

    fun start() = submit(PendingOperation.Start(UUID.randomUUID(), UUID.randomUUID()))

    fun act(action: FocusAction, onSuccess: (() -> Unit)? = null) {
        val session = runtime.state.value.session ?: return
        submit(PendingOperation.Action(UUID.randomUUID(), session, action), onSuccess)
    }

    fun resolveRecovery(choice: FocusRecoveryChoice) {
        val session = runtime.state.value.session ?: return
        submit(PendingOperation.Recovery(session, choice))
    }

    fun retry() {
        val pending = pendingOperation
        if (pending != null) {
            submit(pending)
            return
        }
        viewModelScope.launch {
            local.update { it.copy(busy = true, operationFailed = false) }
            try {
                runtime.checkpointNow()
                local.update { it.copy(busy = false, operationFailed = runtime.state.value.checkpointFailed) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update { it.copy(busy = false, operationFailed = true) }
            }
        }
    }

    fun retryInitialization() = runtime.retryInitialization()

    private fun submit(operation: PendingOperation, onSuccess: (() -> Unit)? = null) {
        pendingOperation = operation
        local.update { it.copy(busy = true, operationFailed = false) }
        viewModelScope.launch {
            try {
                val result = when (operation) {
                    is PendingOperation.Start -> runtime.start(operation.commandId, operation.sessionId)
                    is PendingOperation.Action -> runtime.act(operation.commandId, operation.session, operation.action)
                    is PendingOperation.Recovery -> runtime.resolveRecovery(operation.session, operation.choice)
                }
                when (result) {
                    is FocusWriteResult.Applied,
                    is FocusWriteResult.AlreadyApplied,
                    is FocusWriteResult.Conflict,
                    is FocusWriteResult.RecoveryRequired -> {
                        pendingOperation = null
                        local.update { it.copy(busy = false, operationFailed = false) }
                        if (result is FocusWriteResult.Applied || result is FocusWriteResult.AlreadyApplied) {
                            onSuccess?.invoke()
                        }
                    }
                    is FocusWriteResult.Invalid,
                    is FocusWriteResult.Unavailable -> local.update {
                        it.copy(busy = false, operationFailed = true)
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update { it.copy(busy = false, operationFailed = true) }
            }
        }
    }

    fun showBackPrompt() = local.update { it.copy(backPromptVisible = true) }
    fun dismissBackPrompt() = local.update { it.copy(backPromptVisible = false) }

    fun requestEmergencyExit() {
        viewModelScope.launch {
            val session = runtime.state.value.session
            val saved = session?.let { runtime.markEmergencyExit(it.id) } ?: false
            local.update {
                it.copy(emergencyPromptVisible = true, emergencyMarkerSaved = saved)
            }
        }
    }

    fun dismissEmergencyExit() {
        viewModelScope.launch {
            val runtimeState = runtime.state.value
            val sessionId = runtimeState.session?.id
            if (runtimeState.emergencyMarkerSaved == true && sessionId != null) {
                val cleared = runtime.cancelEmergencyExit(sessionId)
                if (!cleared) return@launch
            }
            local.update { it.copy(emergencyPromptVisible = false, emergencyMarkerSaved = null) }
        }
    }

    fun confirmEmergencyExit() {
        val sessionId = runtime.state.value.session?.id
        if (sessionId == null) runtime.releaseEmergencyExitWithoutSession()
        else runtime.releaseEmergencyExit(sessionId)
        local.update { it.copy(emergencyPromptVisible = false) }
    }

    fun launchSelectedApp(packageName: String) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, shortcutUnavailable = false) }
            try {
                when (runtime.launchSelectedApp(packageName)) {
                    FocusShortcutResult.LAUNCHED -> Unit
                    FocusShortcutResult.NOT_SELECTED,
                    FocusShortcutResult.UNAVAILABLE,
                    FocusShortcutResult.CHECKPOINT_FAILED -> local.update { it.copy(shortcutUnavailable = true) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update { it.copy(shortcutUnavailable = true) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }
}
