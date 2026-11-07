package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusOutcome
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductInvalidReason
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusReflection
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.normalizeFocusReflection
import com.luma.lifeos.ui.common.LumaButton
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingReflection(
    val commandId: UUID,
    val sessionId: UUID,
    val expectedRevision: Long,
    val reflection: FocusReflection,
    val generation: UUID,
)

@Composable
internal fun FocusReflectionEditor(
    session: FocusSession,
    repository: FocusRepository,
    productRepository: FocusProductRepository?,
    generation: UUID?,
    isFocusReadable: Boolean,
    showWhenEmpty: Boolean = false,
    onSaved: () -> Unit = {},
    onSessionMissing: (UUID, UUID) -> Unit = { _, _ -> },
) {
    val product = productRepository ?: return
    val currentGeneration = generation ?: return
    if (!session.isTerminal) return

    var currentSession by remember(session.id, currentGeneration, isFocusReadable) {
        mutableStateOf(session)
    }
    var outcome by remember(session.id, currentGeneration, isFocusReadable) {
        mutableStateOf(if (isFocusReadable) session.reflection.outcome else null)
    }
    var nextStep by remember(session.id, currentGeneration, isFocusReadable) {
        mutableStateOf(if (isFocusReadable) session.reflection.nextStep.orEmpty() else "")
    }
    var editing by remember(session.id, currentGeneration, isFocusReadable) {
        mutableStateOf(showWhenEmpty && session.reflection.isEmpty)
    }
    var dismissed by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(false) }
    var pending by remember(session.id, currentGeneration, isFocusReadable) {
        mutableStateOf<PendingReflection?>(null)
    }
    var saving by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(false) }
    var saveError by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(false) }
    var conflict by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(false) }
    var inputRejected by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(false) }
    var authorityLoading by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(true) }
    var authorityError by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(false) }
    var authorityRetry by remember(session.id, currentGeneration, isFocusReadable) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.id, currentGeneration, isFocusReadable, authorityRetry) {
        if (!isFocusReadable) {
            currentSession = session.copy(reflection = FocusReflection())
            outcome = null
            nextStep = ""
            pending = null
            saving = false
            saveError = false
            conflict = false
            inputRejected = false
            authorityError = false
            authorityLoading = false
            editing = false
            dismissed = false
            return@LaunchedEffect
        }
        authorityLoading = true
        authorityError = false
        try {
            val beforeRead = withContext(Dispatchers.IO) { product.currentGeneration() }
            val readAuthorized = isFocusReadable && beforeRead == currentGeneration
            val latest = if (readAuthorized) {
                withContext(Dispatchers.IO) { repository.terminalSession(session.id) }
            } else null
            val afterRead = withContext(Dispatchers.IO) { product.currentGeneration() }
            when {
                !readAuthorized || afterRead != currentGeneration -> authorityError = true
                latest == null || latest.id != session.id || !latest.isTerminal -> {
                    dismissed = true
                    onSessionMissing(session.id, currentGeneration)
                }
                latest.revision >= currentSession.revision -> {
                    currentSession = latest
                    outcome = latest.reflection.outcome
                    nextStep = latest.reflection.nextStep.orEmpty()
                    pending = null
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            authorityError = true
        } finally {
            authorityLoading = false
        }
    }

    LaunchedEffect(session.id, currentGeneration, session.revision) {
        if (session.revision > currentSession.revision) {
            currentSession = session
            outcome = session.reflection.outcome
            nextStep = session.reflection.nextStep.orEmpty()
            pending = null
            saveError = false
            conflict = false
        }
    }

    fun updateDraft(newOutcome: FocusOutcome? = outcome, newNextStep: String = nextStep) {
        outcome = newOutcome
        nextStep = newNextStep
        pending = null
        saveError = false
        conflict = false
    }

    fun save(command: PendingReflection? = null) {
        if (saving) return
        val normalized = runCatching { normalizeFocusReflection(FocusReflection(outcome, nextStep)) }
            .getOrElse {
                inputRejected = true
                return
            }
        val request = command ?: pending?.takeIf {
            it.sessionId == currentSession.id && it.expectedRevision == currentSession.revision &&
                it.reflection == normalized && it.generation == currentGeneration
        } ?: PendingReflection(
            commandId = UUID.randomUUID(),
            sessionId = currentSession.id,
            expectedRevision = currentSession.revision,
            reflection = normalized,
            generation = currentGeneration,
        )
        pending = request
        saving = true
        saveError = false
        conflict = false
        inputRejected = false
        scope.launch {
            try {
                val beforeWrite = withContext(Dispatchers.IO) { product.currentGeneration() }
                if (beforeWrite != request.generation) {
                    pending = null
                    conflict = true
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    product.saveReflection(
                        request.sessionId,
                        request.expectedRevision,
                        request.reflection,
                        FocusProductContext(request.commandId, request.generation),
                    )
                }
                when (result) {
                    FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> {
                        val afterWrite = withContext(Dispatchers.IO) { product.currentGeneration() }
                        val saved = if (afterWrite == request.generation) {
                            withContext(Dispatchers.IO) { repository.terminalSession(request.sessionId) }
                        } else null
                        if (saved == null || !saved.isTerminal) {
                            saveError = true
                        } else {
                            currentSession = saved
                            outcome = saved.reflection.outcome
                            nextStep = saved.reflection.nextStep.orEmpty()
                            pending = null
                            saveError = false
                            conflict = false
                            editing = false
                            onSaved()
                        }
                    }
                    is FocusProductResult.SessionConflict,
                    is FocusProductResult.TagCatalogConflict,
                    is FocusProductResult.Conflict -> {
                        pending = null
                        val latestGeneration = withContext(Dispatchers.IO) { product.currentGeneration() }
                        val latest = if (latestGeneration == request.generation) {
                            withContext(Dispatchers.IO) { repository.terminalSession(request.sessionId) }
                        } else null
                        if (latest != null && latest.isTerminal) {
                            currentSession = latest
                            outcome = latest.reflection.outcome
                            nextStep = latest.reflection.nextStep.orEmpty()
                        }
                        conflict = true
                    }
                    is FocusProductResult.Invalid -> {
                        pending = null
                        inputRejected = result.reason == FocusProductInvalidReason.INVALID_REFLECTION
                        saveError = !inputRejected
                    }
                    is FocusProductResult.Unavailable -> saveError = true
                    is FocusProductResult.MergeRequired -> saveError = true
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                saveError = true
            } finally {
                saving = false
            }
        }
    }

    if (!isFocusReadable) return
    if (authorityLoading) {
        CircularProgressIndicator(Modifier.testTag("focus_reflection_loading"))
        return
    }
    if (authorityError) {
        Text(stringResource(R.string.focus_reflection_load_error), color = MaterialTheme.colorScheme.error)
        TextButton(
            onClick = { authorityRetry++ },
            modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_load_retry"),
        ) { Text(stringResource(R.string.retry)) }
        return
    }
    if (dismissed) return
    val reflection = currentSession.reflection
    if (!editing) {
        if (reflection.isEmpty) {
            if (showWhenEmpty) {
                Text(stringResource(R.string.focus_reflection_prompt), style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = { editing = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_add"),
                ) { Text(stringResource(R.string.focus_reflection_add)) }
            } else {
                TextButton(
                    onClick = { editing = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_add"),
                ) { Text(stringResource(R.string.focus_reflection_add)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("focus_reflection_summary")) {
                reflection.outcome?.let { Text(stringResource(it.labelResource())) }
                reflection.nextStep?.let { Text(stringResource(R.string.focus_reflection_saved_next_step, it)) }
            }
            TextButton(
                onClick = { editing = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_edit"),
            ) { Text(stringResource(R.string.focus_reflection_edit)) }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("focus_reflection_editor")) {
        if (showWhenEmpty) {
            Text(stringResource(R.string.focus_reflection_prompt), style = MaterialTheme.typography.titleMedium)
        } else {
            Text(stringResource(R.string.focus_reflection_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.focus_reflection_outcome_label), style = MaterialTheme.typography.bodyMedium)
        val outcomes = listOf(
            FocusOutcome.MADE_PROGRESS to R.string.focus_outcome_made_progress,
            FocusOutcome.FINISHED_INTENDED_WORK to R.string.focus_outcome_finished,
            FocusOutcome.GOT_INTERRUPTED to R.string.focus_outcome_interrupted,
        )
        outcomes.forEach { (value, label) ->
            FilterChip(
                selected = outcome == value,
                onClick = { updateDraft(newOutcome = if (outcome == value) null else value) },
                label = { Text(stringResource(label)) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
        }
        OutlinedTextField(
            value = nextStep,
            onValueChange = { value ->
                val count = value.codePointCount(0, value.length)
                if (count <= 280) updateDraft(newNextStep = value) else inputRejected = true
            },
            label = { Text(stringResource(R.string.focus_reflection_next_step_label)) },
            supportingText = {
                Text(stringResource(R.string.focus_reflection_next_step_hint, nextStep.codePointCount(0, nextStep.length)))
            },
            isError = inputRejected,
            enabled = !saving,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("focus_reflection_next_step"),
        )
        if (inputRejected) {
            Text(stringResource(R.string.focus_reflection_invalid), color = MaterialTheme.colorScheme.error)
        }
        if (conflict) {
            Text(stringResource(R.string.focus_reflection_conflict), color = MaterialTheme.colorScheme.error)
        }
        if (saveError) {
            Text(stringResource(R.string.focus_reflection_save_error), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LumaButton(
                onClick = { save() },
                enabled = !saving && !inputRejected,
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_save"),
            ) { Text(stringResource(if (saving) R.string.focus_saving else R.string.focus_reflection_save)) }
            if (pending != null && saveError) {
                TextButton(
                    onClick = { save(pending) },
                    enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_retry"),
                ) { Text(stringResource(R.string.retry)) }
            }
            TextButton(
                onClick = {
                    editing = false
                    pending = null
                    saveError = false
                    conflict = false
                    if (showWhenEmpty && currentSession.reflection.isEmpty) dismissed = true
                },
                enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_skip"),
            ) { Text(stringResource(if (showWhenEmpty) R.string.focus_reflection_skip else R.string.cancel)) }
        }
    }
}

private fun FocusOutcome.labelResource(): Int = when (this) {
    FocusOutcome.MADE_PROGRESS -> R.string.focus_outcome_made_progress
    FocusOutcome.FINISHED_INTENDED_WORK -> R.string.focus_outcome_finished
    FocusOutcome.GOT_INTERRUPTED -> R.string.focus_outcome_interrupted
}
