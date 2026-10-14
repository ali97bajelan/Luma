package com.luma.lifeos.ui.search

import com.luma.lifeos.domain.search.SearchOption
import com.luma.lifeos.domain.search.SearchOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.ui.common.AccessibleFeedback
import java.time.LocalDate
import java.time.format.FormatStyle
import java.util.UUID
import java.util.Locale

internal enum class SearchFilterKind { FROM, THROUGH, TAG, PERSON, PLACE }

internal data class SearchFilterKey(val kind: SearchFilterKind, val id: UUID? = null)

internal data class ActiveSearchFilter(
    val key: SearchFilterKey,
    val date: LocalDate? = null,
    val name: String? = null,
    val duplicateName: Boolean = false,
)

internal fun activeSearchFilters(input: SearchInput, options: SearchOptions): List<ActiveSearchFilter> {
    val filters = buildList {
        input.from?.let { add(ActiveSearchFilter(SearchFilterKey(SearchFilterKind.FROM), date = it)) }
        input.through?.let { add(ActiveSearchFilter(SearchFilterKey(SearchFilterKind.THROUGH), date = it)) }
        addSelections(SearchFilterKind.TAG, input.tagIds, options.tags)
        addSelections(SearchFilterKind.PERSON, input.personIds, options.people)
        input.placeId?.let { place ->
            addSelections(SearchFilterKind.PLACE, setOf(place), options.places)
        }
    }
    return filters
}

@Composable
internal fun ActiveSearchFilters(state: SearchState, onRemove: (SearchFilterKey) -> Unit) {
    val filters = activeSearchFilters(state.input, state.options)
    if (filters.isEmpty()) {
        Text(
            stringResource(R.string.search_no_active_filters),
            modifier = Modifier.fillMaxWidth().testTag("search_no_active_filters"),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    Column(Modifier.fillMaxWidth().testTag("search_active_filters")) {
        Text(stringResource(R.string.search_active_filters), style = MaterialTheme.typography.labelLarge)
        if (state.invalidDates) AccessibleFeedback(stringResource(R.string.search_invalid_dates),
            Modifier.testTag("search_invalid_dates"))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { filter ->
                val category = stringResource(filter.key.kind.categoryResource())
                val id = filter.key.id
                val value = when {
                    filter.date != null -> filter.date.format(localizedDate(FormatStyle.MEDIUM))
                    filter.name != null -> filter.name
                    state.optionsLoading -> stringResource(R.string.search_selection_loading)
                    state.optionsError -> stringResource(R.string.search_selection_unavailable)
                    else -> stringResource(R.string.search_unavailable)
                }
                val suffix = when {
                    filter.duplicateName -> id?.toString()?.takeLast(8)
                    filter.name == null -> id?.toString()?.take(8)
                    else -> null
                }
                val visibleValue = suffix?.let { stringResource(R.string.search_filter_identity, value, it) } ?: value
                val label = stringResource(R.string.search_filter_named, category, visibleValue)
                val accessibleValue = suffix?.let { stringResource(R.string.search_filter_identity, value, it) } ?: value
                val removeLabel = stringResource(R.string.search_remove_filter, category, accessibleValue)
                AssistChip(
                    onClick = { onRemove(filter.key) },
                    label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Text("×", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { })
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .testTag(filter.key.testTag())
                        .semantics { contentDescription = removeLabel },
                )
            }
        }
    }
}

private fun SearchFilterKind.categoryResource(): Int = when (this) {
    SearchFilterKind.FROM -> R.string.search_from
    SearchFilterKind.THROUGH -> R.string.search_through
    SearchFilterKind.TAG -> R.string.journal_tags
    SearchFilterKind.PERSON -> R.string.people
    SearchFilterKind.PLACE -> R.string.places
}

private fun SearchFilterKey.testTag(): String {
    val kind = kind.name.lowercase(Locale.ROOT)
    return id?.let { "search_filter_${kind}_${it}" } ?: "search_filter_$kind"
}

private fun MutableList<ActiveSearchFilter>.addSelections(
    kind: SearchFilterKind,
    ids: Set<UUID>,
    options: List<SearchOption>,
) {
    val byId = options.associateBy(SearchOption::id)
    val names = ids.mapNotNull { byId[it]?.name }
    val duplicateNames = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    ids.sortedBy(UUID::toString).forEach { id ->
        val name = byId[id]?.name
        add(ActiveSearchFilter(
            key = SearchFilterKey(kind, id),
            name = name,
            duplicateName = name != null && name in duplicateNames,
        ))
    }
}
