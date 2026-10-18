package com.luma.lifeos.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.ui.places.LinkedPlace
import com.luma.lifeos.ui.photos.PhotoCount
import com.luma.lifeos.ui.common.EmptyStateCard
import com.luma.lifeos.ui.common.DetailTopBar
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.places.PlacesListContent
import com.luma.lifeos.ui.memory.MemoryStatus
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun PeopleRoute(people: PersonRepository, places: PlaceRepository, onCreatePerson: () -> Unit,
    onPerson: (UUID) -> Unit, onCreatePlace: () -> Unit, onPlace: (UUID) -> Unit,
    onBack: (() -> Unit)? = null) {
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    // Owned by this destination so navigation's saveable state restores the selected catalog.
    var placesSelected by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("people_back")) {
                Text(stringResource(R.string.back))
            }
        }
        TabRow(selectedTabIndex = if (placesSelected) 1 else 0) {
            Tab(selected = !placesSelected, onClick = { placesSelected = false },
                modifier = Modifier.testTag("catalog_people"), text = { Text(stringResource(R.string.catalog_people)) })
            Tab(selected = placesSelected, onClick = { placesSelected = true },
                modifier = Modifier.testTag("catalog_places"), text = { Text(stringResource(R.string.catalog_places)) })
        }
        if (placesSelected) PlacesListContent(places, onCreatePlace, onPlace, modifier = Modifier.weight(1f))
        else PeopleListContent(people, onCreatePerson, onPerson, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PeopleListContent(repository: PersonRepository, onCreate: () -> Unit,
    onPerson: (UUID) -> Unit, modifier: Modifier = Modifier) {
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, retry) { observePeople(repository) }
    val state by observation.collectAsStateWithLifecycle(PeopleState.Loading)
    val empty = (state as? PeopleState.Data)?.people?.isEmpty() == true
    LazyColumn(modifier.fillMaxSize().testTag("screen_people"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.testTag(if (state is PeopleState.Data) "people_ready" else "people_header")) {
                Text(stringResource(R.string.people), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.people_intro))
                if (!empty) Button(onClick = onCreate, modifier = Modifier.testTag("people_create")) { Text(stringResource(R.string.person_create)) }
            }
        }
        when (val current = state) {
            PeopleState.Loading -> item { CircularProgressIndicator() }
            PeopleState.Error -> item {
                Text(stringResource(R.string.people_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PeopleState.Data -> {
                if (current.people.isEmpty()) item {
                    EmptyStateCard(R.drawable.ic_nav_people, R.string.people_empty_heading, R.string.people_empty,
                        "people_empty", R.string.person_create, onCreate, "people_create")
                }
                items(current.people, key = { it.id.toString() }) { person ->
                    Card(Modifier.fillMaxWidth().testTag("person_${person.id}").clickable { onPerson(person.id) }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_nav_people), contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(person.name, style = MaterialTheme.typography.titleMedium)
                                person.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonDetailRoute(repository: PersonRepository, personId: UUID, onBack: () -> Unit,
    onEdit: () -> Unit, onEvent: (LifeEventRecord) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, personId, retry) { observePersonHistory(repository, personId) }
    val state by observation.collectAsStateWithLifecycle(PersonHistoryState.Loading)
    BackHandler(onBack = onBack)
    val canEdit = state is PersonHistoryState.Data
    Scaffold(
        modifier = Modifier.testTag("screen_person_detail"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.person_detail_title),
                backTag = "person_detail_back",
                onBack = onBack,
                editTag = if (canEdit) "person_edit" else null,
                editLabel = if (canEdit) stringResource(R.string.person_edit) else null,
                onEdit = if (canEdit) onEdit else null,
                modifier = Modifier.testTag(if (canEdit) "person_detail_ready" else "person_detail_header"),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("person_detail_content"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val current = state) {
                PersonHistoryState.Loading -> item { CircularProgressIndicator() }
                PersonHistoryState.Missing -> item { Text(stringResource(R.string.person_missing)) }
                PersonHistoryState.Error -> item {
                    Text(stringResource(R.string.people_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
                is PersonHistoryState.Data -> {
                    val events = current.history.events.sortedWith(
                        compareBy<LifeEventRecord, LifeEvent>(LifeEvent.chronologicalOrder) { it.event })
                    item {
                        Text(current.history.person.name, style = MaterialTheme.typography.headlineMedium)
                        current.history.person.note?.let { Text(it) }
                        Text(stringResource(R.string.person_history), style = MaterialTheme.typography.titleLarge)
                        if (events.isNotEmpty()) Text(stringResource(R.string.entry_order_oldest_first),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("person_history_order"))
                        if (events.isEmpty()) EmptyStateCard(R.drawable.ic_nav_people,
                            R.string.person_history_empty_heading, R.string.person_history_empty,
                            "person_history_empty")
                    }
                    items(events, key = { it.event.id.toString() }) { record ->
                        Card(Modifier.fillMaxWidth().testTag("person_event_${record.event.id}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(record.event.occurredAt.atZone(ZoneId.systemDefault())
                                    .format(com.luma.lifeos.language.localizedDateTime(FormatStyle.MEDIUM)))
                                Text(stringResource(when (record.event.type) {
                                    EventType.MOOD -> R.string.event_mood
                                    EventType.JOURNAL -> R.string.event_journal
                                    EventType.MEMORY -> R.string.event_memory
                                    EventType.ACTIVITY -> R.string.event_activity
                                    EventType.APP_USAGE -> R.string.event_app_usage
                                }), style = MaterialTheme.typography.labelLarge)
                                record.event.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                                record.mood?.let { Text(stringResource(R.string.memory_mood_score, it.score)) }
                                (record.journal?.body ?: record.mood?.note ?: record.event.note)?.let {
                                    Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                                record.memory?.let { MemoryStatus(it) }
                                LinkedPeople(record.people)
                                LinkedPlace(record.place)
                                PhotoCount(record.photos.size)
                                TextButton(onClick = { onEvent(record) }) { Text(stringResource(R.string.person_open_event)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
