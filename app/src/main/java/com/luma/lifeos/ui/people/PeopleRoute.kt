package com.luma.lifeos.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.ui.places.LinkedPlace
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.ui.memory.MemoryStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun PeopleRoute(repository: PersonRepository, onCreate: () -> Unit, onPerson: (UUID) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, retry) { observePeople(repository) }
    val state by observation.collectAsStateWithLifecycle(PeopleState.Loading)
    LazyColumn(Modifier.fillMaxSize().testTag("screen_people"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.testTag(if (state is PeopleState.Data) "people_ready" else "people_header")) {
                Text(stringResource(R.string.people), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.people_intro))
                Button(onClick = onCreate, modifier = Modifier.testTag("people_create")) { Text(stringResource(R.string.person_create)) }
            }
        }
        when (val current = state) {
            PeopleState.Loading -> item { CircularProgressIndicator() }
            PeopleState.Error -> item {
                Text(stringResource(R.string.people_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PeopleState.Data -> {
                if (current.people.isEmpty()) item { Text(stringResource(R.string.people_empty)) }
                items(current.people, key = { it.id.toString() }) { person ->
                    Card(Modifier.fillMaxWidth().testTag("person_${person.id}").clickable { onPerson(person.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(person.name, style = MaterialTheme.typography.titleMedium)
                            person.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
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
    LazyColumn(Modifier.fillMaxSize().testTag("screen_person_detail"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().testTag(if (state is PersonHistoryState.Data) "person_detail_ready" else "person_detail_header"),
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                if (state is PersonHistoryState.Data) TextButton(onClick = onEdit, modifier = Modifier.testTag("person_edit")) {
                    Text(stringResource(R.string.person_edit))
                }
            }
        }
        when (val current = state) {
            PersonHistoryState.Loading -> item { CircularProgressIndicator() }
            PersonHistoryState.Missing -> item { Text(stringResource(R.string.person_missing)) }
            PersonHistoryState.Error -> item {
                Text(stringResource(R.string.people_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PersonHistoryState.Data -> {
                item {
                    Text(current.history.person.name, style = MaterialTheme.typography.headlineMedium)
                    current.history.person.note?.let { Text(it) }
                    Text(stringResource(R.string.person_history), style = MaterialTheme.typography.titleLarge)
                    if (current.history.events.isEmpty()) Text(stringResource(R.string.person_history_empty))
                }
                items(current.history.events, key = { it.event.id.toString() }) { record ->
                    val canOpen = record.journal != null || (record.event.isEditable &&
                        (record.mood != null || (record.event.type == EventType.MEMORY && record.memory != null)))
                    Card(Modifier.fillMaxWidth().testTag("person_event_${record.event.id}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.event.occurredAt.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)))
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
                            if (canOpen) TextButton(onClick = { onEvent(record) }) { Text(stringResource(R.string.person_open_event)) }
                        }
                    }
                }
            }
        }
    }
}
