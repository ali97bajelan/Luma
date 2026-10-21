package com.luma.lifeos.ui.people

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
import com.luma.lifeos.domain.model.Person
import com.luma.lifeos.domain.model.PersonTextLimits
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.ContextOption
import com.luma.lifeos.ui.common.ContextOptionPicker
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonPicker(repository: PersonRepository, selected: Set<UUID>, enabled: Boolean,
    onToggle: (UUID) -> Unit, onCreate: () -> Unit,
    feedback: String? = null, feedbackIsError: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val observation = remember(repository, retry) { observePeople(repository) }
    val state by observation.collectAsStateWithLifecycle(PeopleState.Loading)
    val catalog = (state as? PeopleState.Data)?.people.orEmpty()
    Text(stringResource(R.string.people), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }.testTag("people_heading"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        selected.forEach { id ->
            val person = catalog.firstOrNull { it.id == id }
            InputChip(selected = true, enabled = enabled, onClick = { onToggle(id) },
                modifier = Modifier.testTag("person_selected_$id"),
                label = { Text(if (person != null) stringResource(R.string.person_remove, person.name)
                    else stringResource(if (state is PeopleState.Data) R.string.person_unavailable else R.string.person_selected_loading)) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = enabled, onClick = { expanded = !expanded }, modifier = Modifier.testTag("people_picker")) {
            Text(stringResource(if (expanded) R.string.people_hide else R.string.people_choose))
        }
        TextButton(enabled = enabled, onClick = onCreate, modifier = Modifier.testTag("people_picker_create")) {
            Text(stringResource(R.string.person_create))
        }
    }
    feedback?.let { AccessibleFeedback(it, Modifier.testTag("people_attachment_feedback"), feedbackIsError) }
    if (expanded) {
        when (state) {
            PeopleState.Loading -> CircularProgressIndicator()
            PeopleState.Error -> {
                AccessibleFeedback(stringResource(R.string.people_load_error), Modifier.testTag("people_picker_error"))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            is PeopleState.Data -> {
                if (catalog.isEmpty()) Text(stringResource(R.string.people_empty))
                ContextOptionPicker(
                    title = stringResource(R.string.people),
                    options = catalog.map { ContextOption(it.id, it.name, it.note?.takeIf(String::isNotBlank)) },
                    selectedIds = selected,
                    maxSelections = PersonTextLimits.SELECTION,
                    onApply = { result ->
                        (selected - result).forEach(onToggle)
                        (result - selected).forEach(onToggle)
                    },
                    onCreate = onCreate,
                    onDismiss = { expanded = false },
                    enabled = enabled,
                )
            }
        }
        if (selected.size >= PersonTextLimits.SELECTION) AccessibleFeedback(
            stringResource(R.string.people_selection_limit, PersonTextLimits.SELECTION), Modifier.testTag("people_selection_limit"))
    }
}

@Composable
fun LinkedPeople(people: List<Person>) {
    if (people.isNotEmpty()) Text(stringResource(R.string.people_linked, people.joinToString(", ") { it.name }),
        style = MaterialTheme.typography.labelMedium)
}
