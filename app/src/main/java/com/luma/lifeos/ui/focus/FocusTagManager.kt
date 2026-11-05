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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
internal fun FocusTagManager(repository: FocusProductRepository) {
    val catalogFlow = remember(repository) { repository.observeTagCatalog() }
    val catalog by catalogFlow.collectAsStateWithLifecycle(
        initialValue = com.luma.lifeos.domain.focus.FocusTagCatalog(0, emptyList()),
    )
    val scope = rememberCoroutineScope()
    var mergePrompt by remember { mutableStateOf<FocusTagMergePrompt?>(null) }
    var busyTagId by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun rename(tag: FocusTag, name: String) {
        if (busyTagId != null) return
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
                when (result) {
                    FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> Unit
                    is FocusProductResult.MergeRequired -> mergePrompt = FocusTagMergePrompt(
                        result.source, result.target, catalog.revision, generation,
                    )
                    is FocusProductResult.TagCatalogConflict -> failed = true
                    is FocusProductResult.SessionConflict,
                    is FocusProductResult.Conflict,
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

    Column(
        modifier = Modifier.fillMaxWidth().testTag("focus_tag_manager"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.focus_tag_manager_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.focus_tag_manager_explanation), style = MaterialTheme.typography.bodyMedium)
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
        if (failed) Text(
            stringResource(R.string.focus_tag_manager_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("focus_tag_manager_error"),
        )
    }

    mergePrompt?.let { prompt ->
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
                                when (withContext(Dispatchers.IO) {
                                    repository.mergeTags(
                                        prompt.source.id,
                                        prompt.target.id,
                                        prompt.catalogRevision,
                                        FocusProductContext(UUID.randomUUID(), prompt.databaseGeneration),
                                    )
                                }) {
                                    FocusProductResult.Applied,
                                    FocusProductResult.AlreadyApplied -> mergePrompt = null
                                    is FocusProductResult.TagCatalogConflict -> {
                                        failed = true
                                        mergePrompt = null
                                    }
                                    is FocusProductResult.SessionConflict,
                                    is FocusProductResult.Conflict,
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
    var name by rememberSaveable(tag.id.toString(), tag.name) { mutableStateOf(tag.name) }
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
