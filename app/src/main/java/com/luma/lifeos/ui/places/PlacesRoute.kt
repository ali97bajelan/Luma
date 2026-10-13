package com.luma.lifeos.ui.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.EmptyStateCard
import com.luma.lifeos.domain.repository.PlaceRepository
import java.util.UUID

@Composable
fun PlacesRoute(repository: PlaceRepository, onBack: () -> Unit, onCreate: () -> Unit, onPlace: (UUID) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, retry) { observePlaces(repository) }
    val state by observation.collectAsStateWithLifecycle(PlacesState.Loading)
    val empty = (state as? PlacesState.Data)?.places?.isEmpty() == true
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("screen_places"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.testTag(if (state is PlacesState.Data) "places_ready" else "places_header"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("places_back")) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.places), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.places_intro))
                if (!empty) Button(onClick = onCreate, modifier = Modifier.testTag("places_create")) { Text(stringResource(R.string.place_create)) }
            }
        }
        when (val current = state) {
            PlacesState.Loading -> item { CircularProgressIndicator() }
            PlacesState.Error -> item {
                Text(stringResource(R.string.places_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PlacesState.Data -> {
                if (current.places.isEmpty()) item {
                    EmptyStateCard(R.drawable.ic_nav_places, R.string.places_empty_heading, R.string.places_empty,
                        "places_empty", R.string.place_create, onCreate, "places_create")
                }
                items(current.places, key = { it.id.toString() }) { place ->
                    Card(Modifier.fillMaxWidth().testTag("place_${place.id}").clickable { onPlace(place.id) }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_nav_places), contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(place.name, style = MaterialTheme.typography.titleMedium)
                                place.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceDetailRoute(repository: PlaceRepository, placeId: UUID, onBack: () -> Unit, onEdit: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, placeId, retry) { observePlace(repository, placeId) }
    val state by observation.collectAsStateWithLifecycle(PlaceDetailState.Loading)
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().testTag("screen_place_detail").verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("place_detail_back")) { Text(stringResource(R.string.back)) }
            if (state is PlaceDetailState.Data) TextButton(onClick = onEdit, modifier = Modifier.testTag("place_edit")) {
                Text(stringResource(R.string.place_edit))
            }
        }
        when (val current = state) {
            PlaceDetailState.Loading -> CircularProgressIndicator()
            PlaceDetailState.Missing -> Text(stringResource(R.string.place_missing))
            PlaceDetailState.Error -> {
                Text(stringResource(R.string.places_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PlaceDetailState.Data -> {
                Text(current.place.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("place_detail_name"))
                current.place.note?.let { Text(it, modifier = Modifier.testTag("place_detail_note")) }
                current.place.latitude?.let { latitude ->
                    Text(stringResource(R.string.place_coordinates, latitude, requireNotNull(current.place.longitude)))
                }
            }
        }
    }
}
