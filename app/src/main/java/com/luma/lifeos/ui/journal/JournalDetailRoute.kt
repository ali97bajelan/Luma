package com.luma.lifeos.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luma.lifeos.ui.people.LinkedPeople
import com.luma.lifeos.ui.places.LinkedPlace
import com.luma.lifeos.R
import com.luma.lifeos.ui.memory.MemoryStatus
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.JournalRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException

@Composable
fun JournalDetailRoute(repository: JournalRepository, eventId: EventId, onBack: () -> Unit, onEdit: () -> Unit, onMemory: () -> Unit) {
    var record by remember(repository, eventId) { mutableStateOf<LifeEventRecord?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository, eventId, lifecycle, retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            failed = false
            try { record = requireNotNull(repository.get(eventId)) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                record = null
                failed = true
            }
            loading = false
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().testTag("screen_journal_detail").verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            if (!loading && record?.event?.isEditable == true) TextButton(onClick = onEdit, modifier = Modifier.testTag("journal_edit")) {
                Text(stringResource(R.string.journal_edit))
            }
        }
        if (loading) CircularProgressIndicator()
        if (failed) {
            Text(stringResource(R.string.journal_load_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
        }
        if (!loading) record?.let { entry ->
            val journal = requireNotNull(entry.journal)
            LinkedPeople(entry.people)
            LinkedPlace(entry.place)
            Text(journal.title ?: stringResource(R.string.event_journal), style = MaterialTheme.typography.headlineMedium)
            Text(entry.event.occurredAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)))
            if (journal.tags.isNotEmpty()) Text(journal.tags.joinToString(", ") { it.name })
            entry.memory?.let {
                MemoryStatus(it)
                it.note?.let { note -> Text(note) }
            }
            if (entry.canPromoteToMemory) TextButton(onClick = onMemory, modifier = Modifier.testTag("journal_memory")) {
                Text(stringResource(if (entry.memory == null) R.string.memory_promote else R.string.memory_edit))
            }
            Text(journal.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("journal_detail_body"))
        }
    }
}
