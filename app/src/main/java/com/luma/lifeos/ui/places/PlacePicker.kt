package com.luma.lifeos.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.Place
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.ContextOption
import com.luma.lifeos.ui.common.ContextOptionPicker
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacePicker(repository: PlaceRepository, selected: UUID?, enabled: Boolean,
    onSelect: (UUID?) -> Unit, onCreate: () -> Unit,
    feedback: String? = null, feedbackIsError: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, retry) { observePlaces(repository) }
    val state by observation.collectAsStateWithLifecycle(PlacesState.Loading)
    val catalog = (state as? PlacesState.Data)?.places.orEmpty()
    Text(stringResource(R.string.place_optional), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }.testTag("place_heading"))
    if (selected != null) {
        val place = catalog.firstOrNull { it.id == selected }
        InputChip(selected = true, enabled = enabled, onClick = { onSelect(null) },
            modifier = Modifier.testTag("place_selected"),
            label = { Text(if (place != null) stringResource(R.string.place_remove, place.name)
                else stringResource(if (state is PlacesState.Data) R.string.place_unavailable else R.string.place_selected_loading)) })
    } else Text(stringResource(R.string.place_none), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = enabled, onClick = { expanded = !expanded }, modifier = Modifier.testTag("place_picker")) {
            Text(stringResource(if (expanded) R.string.places_hide else R.string.place_choose))
        }
        TextButton(enabled = enabled, onClick = onCreate, modifier = Modifier.testTag("place_picker_create")) {
            Text(stringResource(R.string.place_create))
        }
    }
    feedback?.let { AccessibleFeedback(it, Modifier.testTag("place_attachment_feedback"), feedbackIsError) }
    if (expanded) {
        when (state) {
            PlacesState.Loading -> CircularProgressIndicator()
            PlacesState.Error -> {
                AccessibleFeedback(stringResource(R.string.places_load_error), Modifier.testTag("place_picker_error"))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PlacesState.Data -> {
                if (catalog.isEmpty()) Text(stringResource(R.string.places_empty))
                ContextOptionPicker(
                    title = stringResource(R.string.place_optional),
                    options = catalog.map { ContextOption(it.id, it.name, it.note?.takeIf(String::isNotBlank)) },
                    selectedIds = selected?.let(::setOf).orEmpty(),
                    maxSelections = 1,
                    onApply = { result -> onSelect(result.firstOrNull()) },
                    onCreate = onCreate,
                    onDismiss = { expanded = false },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
fun LinkedPlace(place: Place?) {
    place?.let { Text(stringResource(R.string.place_linked, it.name), style = MaterialTheme.typography.labelMedium) }
}
