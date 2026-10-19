package com.luma.lifeos.widget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.ArrayList
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WidgetLaunchViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val queue = savedStateHandle.getStateFlow<ArrayList<WidgetLaunchRequest>>(QUEUE_KEY, arrayListOf())

    val pending: StateFlow<WidgetLaunchRequest?> = queue
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, queue.value.firstOrNull())

    fun enqueue(destination: WidgetDestination) {
        val updated = ArrayList(queue.value)
        updated += WidgetLaunchRequest(UUID.randomUUID(), destination)
        savedStateHandle[QUEUE_KEY] = updated
    }

    fun acknowledge(id: UUID) {
        val current = queue.value
        if (current.firstOrNull()?.id != id) return
        savedStateHandle[QUEUE_KEY] = ArrayList(current.drop(1))
    }

    private companion object {
        const val QUEUE_KEY = "pending_widget_launches"
    }
}
