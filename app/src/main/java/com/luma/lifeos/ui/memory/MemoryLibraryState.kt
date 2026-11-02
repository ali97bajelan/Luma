package com.luma.lifeos.ui.memory

import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.MemoryRepository
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface MemoryLibraryUiState {
    data object InitialLoading : MemoryLibraryUiState
    data object Empty : MemoryLibraryUiState
    data object InitialError : MemoryLibraryUiState
    data class Content(
        val records: List<LifeEventRecord>,
        val hasOlder: Boolean,
        val olderLoad: MemoryOlderLoadState,
        val observationError: Boolean = false,
    ) : MemoryLibraryUiState
}

enum class MemoryOlderLoadState { Idle, Loading, Error }

sealed interface MemoryLibraryItem {
    val key: String

    data class MonthHeading(val month: YearMonth) : MemoryLibraryItem {
        override val key: String = "month:${month.year}-${month.monthValue.toString().padStart(2, '0')}"
    }

    data class Entry(val record: LifeEventRecord) : MemoryLibraryItem {
        override val key: String = record.event.id.toString()
    }
}

/** Groups an already newest-first prefix without changing its order or stored records. */
fun memoryLibraryItems(records: List<LifeEventRecord>, zone: ZoneId): List<MemoryLibraryItem> = buildList {
    var currentMonth: YearMonth? = null
    records.forEach { record ->
        val month = YearMonth.from(record.event.occurredAt.atZone(zone))
        if (month != currentMonth) {
            add(MemoryLibraryItem.MonthHeading(month))
            currentMonth = month
        }
        add(MemoryLibraryItem.Entry(record))
    }
}

/** Keeps only a bounded prefix and sentinel; record content is never saved in route state. */
class MemoryLibraryStateHolder(
    private val repository: MemoryRepository,
    initialVisibleLimit: Int = INITIAL_LIMIT,
    private val scope: CoroutineScope,
    private val onCommittedLimitChanged: (Int) -> Unit,
) {
    private val mutableState = MutableStateFlow<MemoryLibraryUiState>(MemoryLibraryUiState.InitialLoading)
    val state: StateFlow<MemoryLibraryUiState> = mutableState.asStateFlow()

    private var committedLimit = initialVisibleLimit.coerceIn(1, MAX_VISIBLE_LIMIT)
    private var pendingLimit: Int? = null
    private var committedRecords = emptyList<LifeEventRecord>()
    private var committedHasOlder = false
    private var initialResolved = false
    private var closed = false
    private var committedGeneration = 0L
    private var expansionGeneration = 0L
    private var committedJob: Job? = null
    private var expansionJob: Job? = null

    init {
        observeCommitted(committedLimit, initial = true)
    }

    fun loadOlder() {
        if (closed || pendingLimit != null || mutableState.value !is MemoryLibraryUiState.Content) return
        val content = mutableState.value as MemoryLibraryUiState.Content
        if (!content.hasOlder || content.olderLoad != MemoryOlderLoadState.Idle) return
        if (committedLimit > MAX_VISIBLE_LIMIT - LOAD_INCREMENT) return
        observeExpansion(committedLimit + LOAD_INCREMENT)
    }

    fun retry() {
        if (closed) return
        when (val current = mutableState.value) {
            MemoryLibraryUiState.InitialError -> {
                initialResolved = false
                mutableState.value = MemoryLibraryUiState.InitialLoading
                observeCommitted(committedLimit, initial = true)
            }
            is MemoryLibraryUiState.Content -> when {
                current.olderLoad == MemoryOlderLoadState.Error -> pendingLimit?.let(::observeExpansion)
                current.observationError -> {
                    publishContent(current.olderLoad, observationError = false)
                    observeCommitted(committedLimit, initial = false)
                }
                else -> Unit
            }
            MemoryLibraryUiState.Empty,
            MemoryLibraryUiState.InitialLoading -> Unit
        }
    }

    fun close() {
        if (closed) return
        closed = true
        committedGeneration++
        expansionGeneration++
        committedJob?.cancel()
        expansionJob?.cancel()
        committedJob = null
        expansionJob = null
        pendingLimit = null
        committedRecords = emptyList()
        committedHasOlder = false
        mutableState.value = MemoryLibraryUiState.InitialLoading
    }

    private fun observeCommitted(limit: Int, initial: Boolean) {
        if (closed) return
        committedJob?.cancel()
        val generation = ++committedGeneration
        committedJob = scope.launch {
            try {
                repository.observeLibrary(limit + 1, SUPPORTED_TYPES).collect { rows ->
                    if (closed || generation != committedGeneration) return@collect
                    committedRecords = rows.take(limit)
                    committedHasOlder = rows.size > limit
                    initialResolved = true
                    publishCommitted()
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (closed || generation != committedGeneration) return@launch
                if (initial && !initialResolved) {
                    mutableState.value = MemoryLibraryUiState.InitialError
                } else if (mutableState.value is MemoryLibraryUiState.Content) {
                    mutableState.value = (mutableState.value as MemoryLibraryUiState.Content)
                        .copy(observationError = true)
                } else if (mutableState.value == MemoryLibraryUiState.Empty ||
                    mutableState.value == MemoryLibraryUiState.InitialLoading
                ) {
                    mutableState.value = MemoryLibraryUiState.InitialError
                }
            }
        }
    }

    private fun observeExpansion(limit: Int) {
        if (closed || pendingLimit != null && pendingLimit != limit) return
        pendingLimit = limit
        val generation = ++expansionGeneration
        expansionJob?.cancel()
        publishContent(MemoryOlderLoadState.Loading)
        expansionJob = scope.launch {
            try {
                val rows = repository.observeLibrary(limit + 1, SUPPORTED_TYPES).first()
                if (closed || generation != expansionGeneration || pendingLimit != limit) return@launch
                committedLimit = limit
                committedRecords = rows.take(limit)
                committedHasOlder = rows.size > limit
                pendingLimit = null
                initialResolved = true
                onCommittedLimitChanged(limit)
                publishContent(MemoryOlderLoadState.Idle)
                observeCommitted(limit, initial = false)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (closed || generation != expansionGeneration || pendingLimit != limit) return@launch
                publishContent(MemoryOlderLoadState.Error)
            }
        }
    }

    private fun publishCommitted() {
        if (pendingLimit != null) {
            val olderLoad = (mutableState.value as? MemoryLibraryUiState.Content)?.olderLoad
                ?: MemoryOlderLoadState.Loading
            publishContent(olderLoad, observationError = false)
        } else if (committedRecords.isEmpty()) {
            mutableState.value = MemoryLibraryUiState.Empty
        } else {
            mutableState.value = MemoryLibraryUiState.Content(committedRecords, committedHasOlder, MemoryOlderLoadState.Idle)
        }
    }

    private fun publishContent(olderLoad: MemoryOlderLoadState, observationError: Boolean = false) {
        mutableState.value = MemoryLibraryUiState.Content(
            committedRecords,
            committedHasOlder,
            olderLoad,
            observationError,
        )
    }

    private companion object {
        const val INITIAL_LIMIT = 30
        const val LOAD_INCREMENT = 30
        const val MAX_VISIBLE_LIMIT = Int.MAX_VALUE - 1
        val SUPPORTED_TYPES = setOf(EventType.MEMORY, EventType.MOOD)
    }
}
