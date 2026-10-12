package com.luma.lifeos.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.search.SearchOption
import com.luma.lifeos.ui.today.pickerDate
import com.luma.lifeos.ui.today.pickerMillis
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchFilters(state: SearchState, vm: SearchViewModel, onDone: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var datePicking by rememberSaveable { mutableStateOf<String?>(null) }
    val input = state.input
    AlertDialog(onDismissRequest = onDone, title = { Text(stringResource(R.string.search_filters)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 450.dp).testTag("search_filter_panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.search_filters_help), style = MaterialTheme.typography.bodySmall)
                    SearchDate(stringResource(R.string.search_from), input.from, "search_from", onPick = { datePicking = "from" }, onClear = { vm.setFrom(null) })
                    SearchDate(stringResource(R.string.search_through), input.through, "search_through", onPick = { datePicking = "through" }, onClear = { vm.setThrough(null) })
                    Text(state.zone.id, style = MaterialTheme.typography.bodySmall)
                    if (state.invalidDates) Text(stringResource(R.string.search_invalid_dates), color = MaterialTheme.colorScheme.error)
                }
                item { SelectionButton(stringResource(R.string.journal_tags), "search_tags", state.options.tags, input.tagIds) { picking = "tags" } }
                item { SelectionButton(stringResource(R.string.people), "search_people", state.options.people, input.personIds) { picking = "people" } }
                item { SelectionButton(stringResource(R.string.places), "search_places", state.options.places, setOfNotNull(input.placeId)) { picking = "places" } }
            }
        }, confirmButton = { TextButton(onClick = onDone, modifier = Modifier.testTag("search_filters_done")) { Text(stringResource(R.string.search_done)) } })
    picking?.let { kind ->
        val choices = when (kind) { "tags" -> state.options.tags; "people" -> state.options.people; else -> state.options.places }
        val selected = when (kind) { "tags" -> input.tagIds; "people" -> input.personIds; else -> setOfNotNull(input.placeId) }
        SearchOptionPicker(stringResource(when (kind) { "tags" -> R.string.journal_tags; "people" -> R.string.people; else -> R.string.places }),
            choices, selected, single = kind == "places", loading = state.optionsLoading, error = state.optionsError,
            onRetry = vm::retryOptions, onToggle = { id ->
                when (kind) { "tags" -> vm.toggleTag(id); "people" -> vm.togglePerson(id); else -> vm.setPlace(if (id == input.placeId) null else id) }
            }, onClear = {
                when (kind) { "tags" -> input.tagIds.forEach(vm::toggleTag); "people" -> input.personIds.forEach(vm::togglePerson); else -> vm.setPlace(null) }
            }, onDone = { picking = null })
    }
    datePicking?.let { side ->
        val selected = (if (side == "from") input.from else input.through) ?: LocalDate.now(state.zone)
        val picker = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(selected), yearRange = minOf(1900, selected.year)..maxOf(2100, selected.year))
        DatePickerDialog(onDismissRequest = { datePicking = null }, modifier = Modifier.testTag("search_date_dialog"),
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { if (side == "from") vm.setFrom(pickerDate(it)) else vm.setThrough(pickerDate(it)) }
                datePicking = null
            }) { Text(stringResource(R.string.select_date)) } },
            dismissButton = { TextButton(onClick = { datePicking = null }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
    }
}

@Composable
private fun SearchDate(label: String, date: LocalDate?, tag: String, onPick: () -> Unit, onClear: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = onPick, modifier = Modifier.testTag(tag)) {
            Text(date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: stringResource(R.string.search_any_date))
        }
        if (date != null) TextButton(onClick = onClear, modifier = Modifier.testTag("${tag}_clear")) {
            Text(stringResource(R.string.search_clear_date, label))
        }
    }
}

@Composable
private fun SelectionButton(label: String, tag: String, options: List<SearchOption>, selected: Set<UUID>, onClick: () -> Unit) {
    val unknown = stringResource(R.string.search_unavailable)
    val names = selected.take(3).map { id -> options.find { it.id == id }?.name ?: "$unknown (${id.toString().take(8)})" }
    Column {
        TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Text(stringResource(R.string.search_category, label, selected.size)) }
        if (names.isNotEmpty()) Text(names.joinToString(", "), style = MaterialTheme.typography.bodySmall, maxLines = 3)
    }
}
