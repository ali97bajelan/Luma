package com.luma.lifeos.ui.people

import androidx.activity.compose.BackHandler
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
import com.luma.lifeos.ui.common.contextOptionMatches
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaClickableListSurface
import com.luma.lifeos.ui.common.LumaListSurface
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.PageNavigation
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
    var peopleQuery by rememberSaveable { mutableStateOf("") }
    var placesQuery by rememberSaveable { mutableStateOf("") }
    AppPageScaffold(
        title = stringResource(if (placesSelected) R.string.places else R.string.people),
        navigation = if (onBack != null) PageNavigation.BACK else PageNavigation.NONE,
        onNavigate = onBack,
        navigationTag = "people_back",
    ) { pagePadding ->
      LumaReadingPane(Modifier.fillMaxSize().padding(pagePadding)) {
       Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (placesSelected) 1 else 0) {
            Tab(selected = !placesSelected, onClick = { placesSelected = false },
                modifier = Modifier.testTag("catalog_people"), text = { Text(stringResource(R.string.catalog_people)) })
            Tab(selected = placesSelected, onClick = { placesSelected = true },
                modifier = Modifier.testTag("catalog_places"), text = { Text(stringResource(R.string.catalog_places)) })
        }
        if (placesSelected) PlacesListContent(places, onCreatePlace, onPlace, modifier = Modifier.weight(1f),
            query = placesQuery, onQueryChange = { placesQuery = it })
        else PeopleListContent(people, onCreatePerson, onPerson, query = peopleQuery,
            onQueryChange = { peopleQuery = it }, modifier = Modifier.weight(1f))
       }
      }
    }
}

@Composable
private fun PeopleListContent(repository: PersonRepository, onCreate: () -> Unit,
    onPerson: (UUID) -> Unit, query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, retry) { observePeople(repository) }
    val state by observation.collectAsStateWithLifecycle(PeopleState.Loading)
    val empty = (state as? PeopleState.Data)?.people?.isEmpty() == true
    LazyColumn(modifier.fillMaxSize().testTag("screen_people"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            LumaSectionSurface {
             Column(Modifier.testTag(if (state is PeopleState.Data) "people_ready" else "people_header"),
                 verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.people_intro))
                OutlinedTextField(value = query, onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_options_hint)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("people_search"))
                if (!empty) Button(onClick = onCreate, modifier = Modifier.testTag("people_create")) { Text(stringResource(R.string.person_create)) }
             }
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
                val filteredPeople = current.people.filter { contextOptionMatches(it.name, it.note, query) }
                if (current.people.isNotEmpty() && filteredPeople.isEmpty()) item {
                    Text(stringResource(R.string.search_no_options), modifier = Modifier.testTag("people_no_results"))
                }
                items(filteredPeople, key = { it.id.toString() }) { person ->
                    LumaClickableListSurface(onClick = { onPerson(person.id) }, modifier = Modifier.testTag("person_${person.id}")) {
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
    val displayTime = com.luma.lifeos.language.rememberDisplayTimePolicy()
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
                        LumaListSurface(Modifier.testTag("person_event_${record.event.id}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(record.event.occurredAt.atZone(ZoneId.systemDefault())
                                    .format(displayTime.dateTime(FormatStyle.MEDIUM)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
