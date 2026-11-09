package com.luma.lifeos.ui.focus

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.luma.lifeos.domain.focus.FocusOutcome
import java.util.UUID

internal data class FocusEditorDraftKey(
    val sessionId: UUID,
    val revision: Long,
    val generation: UUID,
)

internal class FocusReflectionDraftState {
    val outcome: MutableState<FocusOutcome?> = mutableStateOf(null)
    val nextStep: MutableState<String> = mutableStateOf("")
    val editing: MutableState<Boolean> = mutableStateOf(false)
    val initialized: MutableState<Boolean> = mutableStateOf(false)
}

internal class FocusMetadataDraftState(
    initialName: String,
    initialTag: String,
) {
    val editing: MutableState<Boolean> = mutableStateOf(false)
    val name: MutableState<String> = mutableStateOf(initialName)
    val tag: MutableState<String> = mutableStateOf(initialTag)
}

/** Keeps protected editor drafts through configuration changes without serializing them. */
internal class FocusEditorDraftViewModel : ViewModel() {
    private val reflectionDrafts = mutableMapOf<FocusEditorDraftKey, FocusReflectionDraftState>()
    private val metadataDrafts = mutableMapOf<FocusEditorDraftKey, FocusMetadataDraftState>()

    fun reflectionDraft(key: FocusEditorDraftKey): FocusReflectionDraftState {
        retainOnly(key)
        return reflectionDrafts.getOrPut(key, ::FocusReflectionDraftState)
    }

    fun metadataDraft(
        key: FocusEditorDraftKey,
        initialName: String,
        initialTag: String,
    ): FocusMetadataDraftState {
        retainOnly(key)
        return metadataDrafts.getOrPut(key) { FocusMetadataDraftState(initialName, initialTag) }
    }

    private fun retainOnly(key: FocusEditorDraftKey) {
        reflectionDrafts.keys.filter { it != key }.forEach { reflectionDrafts.remove(it) }
        metadataDrafts.keys.filter { it != key }.forEach { metadataDrafts.remove(it) }
    }

    fun clearSession(sessionId: UUID) {
        reflectionDrafts.keys
            .filter { it.sessionId == sessionId }
            .forEach { reflectionDrafts.remove(it) }
        metadataDrafts.keys
            .filter { it.sessionId == sessionId }
            .forEach { metadataDrafts.remove(it) }
    }
}
