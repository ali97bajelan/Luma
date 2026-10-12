package com.luma.lifeos.ui.search

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.search.SearchOption
import java.util.UUID

@Composable
internal fun SearchOptionPicker(title: String, options: List<SearchOption>, selected: Set<UUID>, single: Boolean,
    loading: Boolean, error: Boolean, onRetry: () -> Unit, onToggle: (UUID) -> Unit, onClear: () -> Unit, onDone: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val unavailable = stringResource(R.string.search_unavailable)
    val all = remember(options, selected, unavailable) {
        val known = options.map { it.id }.toSet()
        selected.filter { it !in known }.map { SearchOption(it, "$unavailable (${it.toString().take(8)})") } + options
    }
    val visible = remember(all, text) { all.filter { it.name.contains(text.trim(), ignoreCase = true) } }
    AlertDialog(onDismissRequest = onDone, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(200) }, singleLine = true,
                    label = { Text(stringResource(R.string.search_options_hint)) }, modifier = Modifier.fillMaxWidth().testTag("search_option_text"))
                TextButton(onClick = onClear) { Text(stringResource(R.string.search_clear_selection)) }
                if (loading) CircularProgressIndicator()
                if (error) {
                    Text(stringResource(R.string.search_options_error))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                if (!loading && visible.isEmpty()) Text(stringResource(R.string.search_no_options))
                LazyColumn(Modifier.heightIn(max = 320.dp).testTag("search_options_list")) {
                    items(visible, key = { it.id.toString() }) { option ->
                        val checked = option.id in selected
                        val enabled = single || checked || selected.size < 100
                        val selection = if (single) Modifier.selectable(selected = checked, role = Role.RadioButton,
                            onClick = { onToggle(option.id) }) else Modifier.toggleable(value = checked, enabled = enabled,
                            role = Role.Checkbox, onValueChange = { onToggle(option.id) })
                        Row(Modifier.fillMaxWidth().then(selection).testTag("search_option_${option.id}").padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (single) RadioButton(selected = checked, onClick = null)
                            else Checkbox(checked = checked, onCheckedChange = null)
                            Text(option.name, Modifier.weight(1f))
                        }
                    }
                }
                if (!single) Text(stringResource(R.string.search_selection_count, selected.size), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = onDone, modifier = Modifier.testTag("search_options_done")) { Text(stringResource(R.string.search_done)) } })
}
