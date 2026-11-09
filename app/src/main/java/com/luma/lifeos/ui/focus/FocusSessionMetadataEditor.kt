package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.domain.focus.normalizeFocusTagName
import com.luma.lifeos.ui.common.LumaButton
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel

private data class PendingFocusMetadataEdit(
    val commandId: UUID,
    val sessionId: UUID,
    val expectedRevision: Long,
    val name: String?,
    val tagName: String?,
    val expectedCatalogRevision: Long,
    val generation: UUID,
)

@Composable
internal fun FocusSessionMetadataEditor(
    session: FocusSession,
    productRepository: FocusProductRepository?,
    generation: UUID?,
    isFocusReadable: Boolean,
    onSaved: () -> Unit,
) {
    val editorDraftViewModel: FocusEditorDraftViewModel = viewModel()
    SideEffect {
        if (!isFocusReadable || generation == null) {
            editorDraftViewModel.clearSession(session.id)
        }
    }
    val product = productRepository ?: return
    val currentGeneration = generation ?: return
    if (!session.isTerminal || !isFocusReadable) return

    val draftKey = FocusEditorDraftKey(session.id, session.revision, currentGeneration)
    val draftState = editorDraftViewModel.metadataDraft(
        draftKey,
        initialName = session.metadata.name.orEmpty(),
        initialTag = session.metadata.tag?.name.orEmpty(),
    )
    var editing by draftState.editing
    var name by draftState.name
    var tag by draftState.tag
    var saving by remember(session.id, session.revision, currentGeneration) { mutableStateOf(false) }
    var rejected by remember(session.id, session.revision, currentGeneration) { mutableStateOf(false) }
    var conflict by remember(session.id, session.revision, currentGeneration) { mutableStateOf(false) }
    var saveError by remember(session.id, session.revision, currentGeneration) { mutableStateOf(false) }
    var pending by remember(session.id, session.revision, currentGeneration) {
        mutableStateOf<PendingFocusMetadataEdit?>(null)
    }
    val scope = rememberCoroutineScope()
    val catalogFlow = remember(product) { product.observeTagCatalog() }
    val catalog by catalogFlow.collectAsStateWithLifecycle(
        initialValue = com.luma.lifeos.domain.focus.FocusTagCatalog(0, emptyList()),
    )

    fun submit(retry: PendingFocusMetadataEdit? = null) {
        if (saving) return
        val tagName = tag.takeIf { it.isNotBlank() }
        val submittedName = if (session.metadata.name == null && name.isBlank()) null else name
        val normalized = runCatching {
            val normalizedTag = tagName?.let(::normalizeFocusTagName)
            val normalizedName = if (submittedName == null) null else {
                normalizeFocusStartInput(FocusStartInput(submittedName, normalizedTag)).name
            }
            normalizedName to normalizedTag
        }.getOrElse {
            rejected = true
            return
        }
        rejected = false
        val request = retry?.takeIf {
            it.sessionId == session.id && it.expectedRevision == session.revision &&
                it.name == normalized.first && it.tagName == normalized.second &&
                it.generation == currentGeneration
        } ?: PendingFocusMetadataEdit(
            commandId = UUID.randomUUID(),
            sessionId = session.id,
            expectedRevision = session.revision,
            name = normalized.first,
            tagName = normalized.second,
            expectedCatalogRevision = catalog.revision,
            generation = currentGeneration,
        )
        pending = request
        saving = true
        conflict = false
        saveError = false
        scope.launch {
            try {
                val before = withContext(Dispatchers.IO) { product.currentGeneration() }
                if (before != request.generation) {
                    conflict = true
                    pending = null
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    product.updateSessionMetadata(
                        request.sessionId,
                        request.expectedRevision,
                        request.name,
                        request.tagName,
                        request.expectedCatalogRevision,
                        FocusProductContext(request.commandId, request.generation),
                    )
                }
                when (result) {
                    FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> {
                        if (withContext(Dispatchers.IO) { product.currentGeneration() } != request.generation) {
                            conflict = true
                        } else {
                            pending = null
                            editing = false
                            onSaved()
                        }
                    }
                    is FocusProductResult.SessionConflict,
                    is FocusProductResult.TagCatalogConflict -> {
                        pending = null
                        conflict = true
                        onSaved()
                    }
                    is FocusProductResult.Invalid -> {
                        pending = null
                        rejected = true
                    }
                    is FocusProductResult.Conflict,
                    is FocusProductResult.InvitationConflict,
                    is FocusProductResult.MergeRequired,
                    is FocusProductResult.Unavailable -> saveError = true
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                saveError = true
            } finally {
                saving = false
            }
        }
    }

    if (!editing) {
        TextButton(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_session_metadata_edit"),
        ) { Text(stringResource(R.string.focus_session_metadata_edit)) }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("focus_session_metadata_editor"),
    ) {
        Text(stringResource(R.string.focus_session_metadata_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { value ->
                if (value.codePointCount(0, value.length) <= 120) {
                    name = value
                    rejected = false
                    pending = null
                } else rejected = true
            },
            label = { Text(stringResource(R.string.focus_session_name_label)) },
            enabled = !saving,
            singleLine = true,
            isError = rejected,
            modifier = Modifier.fillMaxWidth().testTag("focus_session_name_edit"),
        )
        OutlinedTextField(
            value = tag,
            onValueChange = { value ->
                if (value.length <= 50) {
                    tag = value
                    rejected = false
                    pending = null
                } else rejected = true
            },
            label = { Text(stringResource(R.string.focus_session_tag_label)) },
            enabled = !saving,
            singleLine = true,
            isError = rejected,
            modifier = Modifier.fillMaxWidth().testTag("focus_session_tag_edit"),
        )
        if (rejected) Text(stringResource(R.string.focus_session_metadata_invalid), color = MaterialTheme.colorScheme.error)
        if (conflict) Text(stringResource(R.string.focus_session_metadata_conflict), color = MaterialTheme.colorScheme.error)
        if (saveError) Text(
            stringResource(R.string.focus_session_metadata_save_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LumaButton(
                onClick = { submit() },
                enabled = !saving && !rejected && (
                    name != session.metadata.name.orEmpty() || tag != session.metadata.tag?.name.orEmpty()
                ),
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_session_metadata_save"),
            ) {
                if (saving) CircularProgressIndicator()
                else Text(stringResource(R.string.focus_session_metadata_save))
            }
            if (pending != null && saveError) {
                TextButton(
                    onClick = { submit(pending) },
                    enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_session_metadata_retry"),
                ) { Text(stringResource(R.string.retry)) }
            }
            TextButton(
                onClick = {
                    name = session.metadata.name.orEmpty()
                    tag = session.metadata.tag?.name.orEmpty()
                    pending = null
                    rejected = false
                    conflict = false
                    saveError = false
                    editing = false
                },
                enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_session_metadata_cancel"),
            ) { Text(stringResource(R.string.cancel)) }
        }
    }
}
