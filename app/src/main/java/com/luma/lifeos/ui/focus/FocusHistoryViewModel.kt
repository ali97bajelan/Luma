package com.luma.lifeos.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.focus.FOCUS_HISTORY_PAGE_SIZE
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.time.TimeRange
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FocusHistoryUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val page: com.luma.lifeos.domain.focus.FocusPage? = null,
    val deletingSessionId: UUID? = null,
    val deletionFailed: Boolean = false,
)

/** Loads bounded history pages and deletes only terminal rows through the repository contract. */
class FocusHistoryViewModel(
    private val repository: FocusRepository,
    private val range: TimeRange?,
    private val savedStateHandle: SavedStateHandle,
    private val processInstanceId: UUID = UUID.randomUUID(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(FocusHistoryUiState())
    val state: StateFlow<FocusHistoryUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var loadRequest = 0L

    init {
        load(savedStateHandle[PAGE_OFFSET_KEY] ?: 0)
    }

    fun retry() = load(mutableState.value.page?.offset ?: savedStateHandle[PAGE_OFFSET_KEY] ?: 0)

    fun page(offset: Int) {
        require(offset >= 0 && offset % FOCUS_HISTORY_PAGE_SIZE == 0)
        load(offset)
    }

    fun delete(row: FocusHistoryRow) {
        if (!row.session.isTerminal || mutableState.value.deletingSessionId != null) return
        mutableState.value = mutableState.value.copy(
            deletingSessionId = row.session.id,
            deletionFailed = false,
        )
        viewModelScope.launch {
            try {
                val context = FocusWriteContext(
                    databaseGeneration = repository.currentGeneration(),
                    processInstanceId = processInstanceId,
                )
                when (repository.delete(row.session.id, row.session.revision, context)) {
                    is FocusWriteResult.Applied,
                    is FocusWriteResult.AlreadyApplied -> {
                        val offset = mutableState.value.page?.offset ?: 0
                        mutableState.value = mutableState.value.copy(
                            deletingSessionId = null,
                            deletionFailed = false,
                        )
                        load(offset)
                    }
                    else -> mutableState.value = mutableState.value.copy(
                        deletingSessionId = null,
                        deletionFailed = true,
                    )
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = mutableState.value.copy(
                    deletingSessionId = null,
                    deletionFailed = true,
                )
            }
        }
    }

    private fun load(requestedOffset: Int) {
        loadJob?.cancel()
        val request = ++loadRequest
        mutableState.value = mutableState.value.copy(
            loading = true,
            loadFailed = false,
            page = null,
        )
        loadJob = viewModelScope.launch {
            try {
                var page = repository.history(range, requestedOffset, FOCUS_HISTORY_PAGE_SIZE)
                val lastValidOffset = if (page.total == 0) 0
                else ((page.total - 1) / FOCUS_HISTORY_PAGE_SIZE) * FOCUS_HISTORY_PAGE_SIZE
                val actualOffset = requestedOffset.coerceAtMost(lastValidOffset)
                if (actualOffset != requestedOffset) {
                    page = repository.history(range, actualOffset, FOCUS_HISTORY_PAGE_SIZE)
                }
                if (request == loadRequest) {
                    savedStateHandle[PAGE_OFFSET_KEY] = page.offset
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        loadFailed = false,
                        page = page,
                    )
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (request == loadRequest) {
                    mutableState.value = mutableState.value.copy(loading = false, loadFailed = true)
                }
            }
        }
    }

    private companion object {
        const val PAGE_OFFSET_KEY = "focus_history_page_offset"
    }
}
