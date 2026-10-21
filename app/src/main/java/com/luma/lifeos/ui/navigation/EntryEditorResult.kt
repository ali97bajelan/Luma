package com.luma.lifeos.ui.navigation

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.EventId
import kotlinx.coroutines.flow.StateFlow

sealed interface EntryEditorResult {
    val id: EventId

    data class Saved(override val id: EventId) : EntryEditorResult
    data class Deleted(override val id: EventId) : EntryEditorResult
    data class Unmarked(override val id: EventId) : EntryEditorResult
}

private const val RESULT_TYPE = "entry_editor_result_type"
private const val RESULT_ID = "entry_editor_result_id"

/** Store primitive fields on the editor entry so a completed outcome survives recreation. */
fun SavedStateHandle.recordEntryEditorResult(result: EntryEditorResult) {
    this[RESULT_TYPE] = when (result) {
        is EntryEditorResult.Saved -> "SAVED"
        is EntryEditorResult.Deleted -> "DELETED"
        is EntryEditorResult.Unmarked -> "UNMARKED"
    }
    this[RESULT_ID] = result.id.toString()
}

fun SavedStateHandle.entryEditorResultIdFlow(): StateFlow<String?> = getStateFlow(RESULT_ID, null)

/** Read and clear the result before navigation consumes it. */
fun SavedStateHandle.consumeEntryEditorResult(): EntryEditorResult? {
    val type = get<String>(RESULT_TYPE)
    val id = get<String>(RESULT_ID)?.let { runCatching { EventId.parse(it) }.getOrNull() }
    remove<String>(RESULT_TYPE)
    remove<String>(RESULT_ID)
    if (id == null) return null
    return when (type) {
        "SAVED" -> EntryEditorResult.Saved(id)
        "DELETED" -> EntryEditorResult.Deleted(id)
        "UNMARKED" -> EntryEditorResult.Unmarked(id)
        else -> null
    }
}
