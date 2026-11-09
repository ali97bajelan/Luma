package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class FocusTagMergePrompt(
    val source: FocusTag,
    val target: FocusTag,
    val catalogRevision: Long,
    val databaseGeneration: UUID,
)

@Composable
internal fun FocusTagManager(
    repository: FocusProductRepository,
    canReadTaskOrigin: Boolean = true,
    onRequestUnlock: () -> Unit = {},
) {
    val catalogFlow = remember(repository, canReadTaskOrigin) {
        repository.observeTagCatalog(includeTaskOrigin = canReadTaskOrigin)
    }
    val catalog by catalogFlow.collectAsStateWithLifecycle(
        initialValue = com.luma.lifeos.domain.focus.FocusTagCatalog(0, emptyList()),
    )
    val scope = rememberCoroutineScope()
    val latestTaskAccess by rememberUpdatedState(canReadTaskOrigin)
    var mergePrompt by remember { mutableStateOf<FocusTagMergePrompt?>(null) }
    var busyTagId by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(catalog, canReadTaskOrigin) {
        val prompt = mergePrompt
        if (!canReadTaskOrigin || prompt?.let {
                it.catalogRevision != catalog.revision ||
                    it.databaseGeneration != catalog.databaseGeneration ||
                    catalog.tags.none { tag -> tag.id == it.source.id } ||
                    catalog.tags.none { tag -> tag.id == it.target.id }
            } == true
        ) mergePrompt = null
    }

    fun rename(tag: FocusTag, name: String) {
        if (!canReadTaskOrigin || busyTagId != null) return
        val generation = catalog.databaseGeneration ?: run {
            failed = true
            return
        }
        busyTagId = tag.id.toString()
        failed = false
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.renameTag(
                        tag.id,
                        name,
                        catalog.revision,
                        FocusProductContext(UUID.randomUUID(), generation),
                    )
                }
                if (!latestTaskAccess) {
                    mergePrompt = null
                    failed = false
                    return@launch
                }
                when (result) {
                    FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> Unit
                    is FocusProductResult.MergeRequired -> mergePrompt = FocusTagMergePrompt(
                        result.source, result.target, catalog.revision, generation,
                    )
                    is FocusProductResult.TagCatalogConflict -> failed = true
                    is FocusProductResult.SessionConflict,
                    is FocusProductResult.Conflict,
                    is FocusProductResult.InvitationConflict,
                    is FocusProductResult.Invalid,
                    is FocusProductResult.Unavailable -> failed = true
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                failed = true
            } finally {
                busyTagId = null
            }
        }
    }

    val tagSummary = when {
        !canReadTaskOrigin -> stringResource(R.string.focus_tag_manager_locked_summary)
        failed -> stringResource(R.string.focus_tag_manager_error)
        catalog.tags.isEmpty() -> stringResource(R.string.focus_tag_manager_empty)
        else -> pluralStringResource(R.plurals.focus_tag_manager_count, catalog.tags.size, catalog.tags.size)
    }
    FocusSettingsDisclosure(
        title = stringResource(R.string.focus_tag_manager_title),
        summary = tagSummary,
        sectionTag = "focus_settings_tags",
        contentStateIdentity = catalog.databaseGeneration?.toString(),
        preserveContentState = canReadTaskOrigin,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("focus_tag_manager"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.focus_tag_manager_explanation), style = MaterialTheme.typography.bodyMedium)
            if (!canReadTaskOrigin) {
                Text(stringResource(R.string.app_lock_gate_body))
                TextButton(
                    onClick = onRequestUnlock,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_tag_manager_unlock"),
                ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
            }
            if (canReadTaskOrigin) {
                if (catalog.tags.isEmpty()) {
                    Text(stringResource(R.string.focus_tag_manager_empty), modifier = Modifier.testTag("focus_tag_manager_empty"))
                } else {
                    catalog.tags.forEach { tag ->
                        FocusTagManagerRow(
                            tag = tag,
                            busy = busyTagId == tag.id.toString(),
                            enabled = busyTagId == null,
                            onRename = { rename(tag, it) },
                        )
                    }
                }
            }
            if (failed) Text(
                stringResource(R.string.focus_tag_manager_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("focus_tag_manager_error"),
            )
        }
    }
    mergePrompt?.takeIf { prompt ->
        canReadTaskOrigin && prompt.catalogRevision == catalog.revision &&
            prompt.databaseGeneration == catalog.databaseGeneration &&
            catalog.tags.any { it.id == prompt.source.id } && catalog.tags.any { it.id == prompt.target.id }
    }?.let { prompt ->
        AlertDialog(
            onDismissRequest = { if (busyTagId == null) mergePrompt = null },
            title = { Text(stringResource(R.string.focus_tag_merge_title)) },
            text = {
                Text(stringResource(R.string.focus_tag_merge_confirm, prompt.source.name, prompt.target.name))
            },
            confirmButton = {
                TextButton(
                    enabled = busyTagId == null,
                    onClick = {
                        if (busyTagId != null) return@TextButton
                        busyTagId = prompt.source.id.toString()
                        failed = false
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    repository.mergeTags(
                                        prompt.source.id,
                                        prompt.target.id,
                                        prompt.catalogRevision,
                                        FocusProductContext(UUID.randomUUID(), prompt.databaseGeneration),
                                    )
                                }
                                if (!latestTaskAccess) {
                                    mergePrompt = null
                                    failed = false
                                    return@launch
                                }
                                when (result) {
                                    FocusProductResult.Applied,
                                    FocusProductResult.AlreadyApplied -> mergePrompt = null
                                    is FocusProductResult.TagCatalogConflict -> {
                                        failed = true
                                        mergePrompt = null
                                    }
                                    is FocusProductResult.SessionConflict,
                                    is FocusProductResult.Conflict,
                                    is FocusProductResult.InvitationConflict,
                                    is FocusProductResult.MergeRequired,
                                    is FocusProductResult.Invalid,
                                    is FocusProductResult.Unavailable -> {
                                        failed = true
                                        mergePrompt = null
                                    }
                                }
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                failed = true
                            } finally {
                                busyTagId = null
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_tag_merge_confirm_button"),
                ) { Text(stringResource(R.string.focus_tag_merge_action)) }
            },
            dismissButton = {
                TextButton(
                    enabled = busyTagId == null,
                    onClick = { mergePrompt = null },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_tag_merge_cancel"),
                ) { Text(stringResource(R.string.cancel)) }
            },
            modifier = Modifier.testTag("focus_tag_merge_dialog"),
        )
    }
}

@Composable
private fun FocusTagManagerRow(
    tag: FocusTag,
    busy: Boolean,
    enabled: Boolean,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(tag.id, tag.name) { mutableStateOf(tag.name) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 50) name = it },
            label = { Text(stringResource(R.string.focus_tag_manager_name)) },
            enabled = enabled && !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("focus_tag_name_${tag.id}"),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { onRename(name) },
                enabled = enabled && !busy && name != tag.name,
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_tag_rename_${tag.id}"),
            ) { Text(stringResource(R.string.focus_tag_manager_rename)) }
        }
    }
}
