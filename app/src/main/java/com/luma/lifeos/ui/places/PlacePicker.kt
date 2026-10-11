package com.luma.lifeos.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.Place
import com.luma.lifeos.domain.repository.PlaceRepository
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacePicker(repository: PlaceRepository, selected: UUID?, enabled: Boolean,
    onSelect: (UUID?) -> Unit, onCreate: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, retry) { observePlaces(repository) }
    val state by observation.collectAsStateWithLifecycle(PlacesState.Loading)
    val catalog = (state as? PlacesState.Data)?.places.orEmpty()
    Text(stringResource(R.string.place_optional), style = MaterialTheme.typography.titleMedium)
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
    if (expanded) {
        when (state) {
            PlacesState.Loading -> CircularProgressIndicator()
            PlacesState.Error -> {
                Text(stringResource(R.string.places_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PlacesState.Data -> {
                if (catalog.isEmpty()) Text(stringResource(R.string.places_empty))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selected == null, enabled = enabled, onClick = { onSelect(null) },
                        label = { Text(stringResource(R.string.place_none)) }, modifier = Modifier.testTag("place_none"))
                    catalog.forEach { place ->
                        FilterChip(selected = selected == place.id, enabled = enabled, onClick = { onSelect(place.id) },
                            modifier = Modifier.testTag("place_option_${place.id}"), label = { Text(place.name) })
                    }
                }
            }
        }
    }
}

@Composable
fun LinkedPlace(place: Place?) {
    place?.let { Text(stringResource(R.string.place_linked, it.name), style = MaterialTheme.typography.labelMedium) }
}
