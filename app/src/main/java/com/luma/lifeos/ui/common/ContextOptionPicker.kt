package com.luma.lifeos.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import java.util.UUID
import java.util.Locale

data class ContextOption(val id: UUID, val label: String, val secondary: String?)

fun contextOptionMatches(label: String, secondary: String?, query: String): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return normalizedQuery.isEmpty() || label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
        secondary?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ContextOptionPicker(
    title: String,
    options: List<ContextOption>,
    selectedIds: Set<UUID>,
    maxSelections: Int,
    onApply: (Set<UUID>) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    require(maxSelections > 0)
    var query by rememberSaveable { mutableStateOf("") }
    var workingIds by rememberSaveable { mutableStateOf(selectedIds.map(UUID::toString).toSet()) }
    val selected = workingIds.mapNotNullTo(linkedSetOf(), UUID::fromString)
    val selectedOptions = selected.mapNotNull { id -> options.firstOrNull { it.id == id }?.let { id to it } }
    val unavailableIds = selected - selectedOptions.mapTo(mutableSetOf()) { it.first }
    val unavailableLabel = stringResource(R.string.search_unavailable)
    val filtered = options.filter { contextOptionMatches(it.label, it.secondary, query) }
    fun remove(id: UUID) { workingIds = workingIds - id.toString() }
    fun toggle(id: UUID) {
        val key = id.toString()
        workingIds = if (key in workingIds) workingIds - key
        else if (workingIds.size < maxSelections) workingIds + key else workingIds
    }
    fun apply() = onApply(workingIds.mapNotNullTo(linkedSetOf(), UUID::fromString))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_options_hint)) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("context_search"),
                )
                if (selected.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        selectedOptions.forEach { (id, option) ->
                            InputChip(
                                selected = true,
                                enabled = enabled,
                                onClick = { remove(id) },
                                modifier = Modifier.testTag("context_remove_$id"),
                                label = { Text(option.label) },
                            )
                        }
                        unavailableIds.forEach { id ->
                            InputChip(
                                selected = true,
                                enabled = enabled,
                                onClick = { remove(id) },
                                modifier = Modifier.testTag("context_remove_$id")
                                    .semantics { contentDescription = unavailableLabel },
                                label = { Text(stringResource(R.string.search_unavailable)) },
                            )
                        }
                    }
                }
                TextButton(enabled = enabled && selected.isNotEmpty(), onClick = { workingIds = emptySet() },
                    modifier = Modifier.align(Alignment.End).testTag("context_clear")) {
                    Text(stringResource(R.string.search_clear_selection))
                }
                if (maxSelections > 1) {
                    Text(stringResource(R.string.search_selection_count, selected.size),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("context_selection_count"))
                }
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.search_no_options), modifier = Modifier.testTag("context_no_results"))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filtered, key = { it.id.toString() }) { option ->
                            val checked = option.id in selected
                            val canChange = enabled && (checked || selected.size < maxSelections)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable(enabled = canChange) { toggle(option.id) }
                                    .testTag("context_option_${option.id}")
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, enabled = canChange,
                                    onCheckedChange = { toggle(option.id) })
                                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                    option.secondary?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = { apply(); onDismiss() },
                modifier = Modifier.testTag("context_done")) { Text(stringResource(R.string.search_done)) }
        },
        dismissButton = {
            FlowRow(horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(enabled = enabled, onClick = { apply(); onDismiss(); onCreate() },
                    modifier = Modifier.testTag("context_create")) {
                    Text(stringResource(R.string.context_new))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("context_cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
