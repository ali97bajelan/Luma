package com.luma.lifeos.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.repository.TimelineRepository
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

sealed interface ShareStatus {
    data object Loading : ShareStatus
    data object Unavailable : ShareStatus
    data object Error : ShareStatus
    data class Ready(
        val snapshot: EntryShareSnapshot,
        val options: EntryShareOptions,
        val text: String,
        val edited: Boolean = false,
        val pendingOptions: EntryShareOptions? = null,
        val stale: Boolean = false,
        val busy: Boolean = false,
        val launchError: Boolean = false,
        val textError: EntryShareTextError? = null,
        val contextKey: String,
    ) : ShareStatus
}

class EntryShareViewModel(private val repository: TimelineRepository, private val request: EntryShareRequest) : ViewModel() {
    private val mutableState = MutableStateFlow<ShareStatus>(ShareStatus.Loading)
    val state = mutableState.asStateFlow()
    private var generation = 0
    private var started = false
    private var launchJob: Job? = null
    fun start(zone: ZoneId, context: EntryShareTextContext, contextKey: String) {
        if (!started) { started = true; load(zone, context, contextKey) }
    }
    fun cancelPendingLaunch() {
        launchJob?.cancel()
        launchJob = null
        (state.value as? ShareStatus.Ready)?.let { if (it.busy) mutableState.value = it.copy(busy = false) }
    }

    fun load(zone: ZoneId, context: EntryShareTextContext, contextKey: String) {
        cancelPendingLaunch()
        val token = ++generation
        mutableState.value = ShareStatus.Loading
        viewModelScope.launch {
            try {
                val snapshot = loadEntryShareSnapshot(repository, request, zone)
                if (token != generation) return@launch
                mutableState.value = if (snapshot == null) ShareStatus.Unavailable else {
                    val options = EntryShareOptions()
                    ShareStatus.Ready(snapshot, options, formatEntryShare(snapshot, options, context), contextKey = contextKey)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (token == generation) mutableState.value = ShareStatus.Error }
        }
    }
    fun edit(text: String) {
        val s = state.value as? ShareStatus.Ready ?: return
        if (!s.busy && !s.stale) mutableState.value = s.copy(text = text, edited = true, textError = null, launchError = false)
    }
    fun option(options: EntryShareOptions, context: EntryShareTextContext) {
        val s = state.value as? ShareStatus.Ready ?: return
        if (s.busy || s.stale) return
        mutableState.value = if (s.edited) s.copy(pendingOptions = options) else s.copy(options = options, text = formatEntryShare(s.snapshot, options, context), textError = null)
    }
    fun cancelRegenerate() { (state.value as? ShareStatus.Ready)?.let { mutableState.value = it.copy(pendingOptions = null) } }
    fun regenerate(context: EntryShareTextContext) {
        val s = state.value as? ShareStatus.Ready ?: return
        val options = s.pendingOptions ?: return
        mutableState.value = s.copy(options = options, pendingOptions = null, text = formatEntryShare(s.snapshot, options, context), edited = false, textError = null)
    }
    fun invalidateContext(zone: ZoneId, contextKey: String) {
        val s = state.value as? ShareStatus.Ready ?: return
        if (s.snapshot.zone != zone || s.contextKey != contextKey) mutableState.value = s.copy(stale = true)
    }
    fun share(zone: ZoneId, contextKey: String, launch: (String) -> TextShareLaunchResult) {
        val s = state.value as? ShareStatus.Ready ?: return
        if (s.busy || s.stale || s.pendingOptions != null) return
        val error = validateEntryShareText(s.text)
        if (error != null) { mutableState.value = s.copy(textError = error); return }
        if (s.snapshot.zone != zone || s.contextKey != contextKey) { mutableState.value = s.copy(stale = true); return }
        mutableState.value = s.copy(busy = true, launchError = false)
        val token = generation
        launchJob = viewModelScope.launch {
            try {
                val current = loadEntryShareSnapshot(repository, request, zone)
                if (token != generation) return@launch
                val active = state.value as? ShareStatus.Ready ?: return@launch
                mutableState.value = when {
                    current == null -> ShareStatus.Unavailable
                    current != s.snapshot || active.stale -> active.copy(busy = false, stale = true)
                    validateEntryShareText(s.text) != null -> active.copy(busy = false, textError = validateEntryShareText(s.text))
                    else -> active.copy(busy = false, launchError = launch(s.text) == TextShareLaunchResult.UNAVAILABLE)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                val active = state.value as? ShareStatus.Ready
                if (token == generation && active != null) mutableState.value = active.copy(busy = false, launchError = true)
            }
        }
    }
}
