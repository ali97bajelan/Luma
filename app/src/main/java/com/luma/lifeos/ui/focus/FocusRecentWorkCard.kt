package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusState
import java.util.UUID

@Composable
internal fun FocusRecentWorkCard(
    state: FocusRecentWorkState,
    hasUserAuthoredDraft: Boolean,
    canRepeat: Boolean,
    onRepeatSession: (UUID) -> Unit,
    onRetry: () -> Unit,
) {
    var replacementSessionId by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(state) {
        if (state !is FocusRecentWorkState.Ready) replacementSessionId = null
    }

    when (state) {
        FocusRecentWorkState.Hidden,
        is FocusRecentWorkState.Empty,
        -> Unit

        is FocusRecentWorkState.Loading -> Card(
            modifier = Modifier.fillMaxWidth().testTag("focus_recent_work_loading"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.focus_recent_work), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.focus_recent_work_loading))
            }
        }

        is FocusRecentWorkState.Missing -> Card(
            modifier = Modifier.fillMaxWidth().testTag("focus_recent_work_missing"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.focus_recent_work), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.focus_recent_work_missing))
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_recent_work_retry"),
                ) { Text(stringResource(R.string.retry)) }
            }
        }

        is FocusRecentWorkState.Error -> Card(
            modifier = Modifier.fillMaxWidth().testTag("focus_recent_work_error"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.focus_recent_work), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.focus_recent_work_error), color = MaterialTheme.colorScheme.error)
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_recent_work_retry"),
                ) { Text(stringResource(R.string.retry)) }
            }
        }

        is FocusRecentWorkState.Ready -> {
            val (_, session) = state
            Card(
                modifier = Modifier.fillMaxWidth().testTag("focus_recent_work_card"),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.focus_recent_work), style = MaterialTheme.typography.titleSmall)
                    Text(
                        session.metadata.name?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.focus_session_fallback),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("focus_recent_work_name"),
                    )
                    if (session.state == FocusState.INTERRUPTED) {
                        Text(
                            stringResource(R.string.focus_recent_interrupted),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    session.reflection.nextStep?.takeIf(String::isNotBlank)?.let { nextStep ->
                        Text(stringResource(R.string.focus_reflection_saved_next_step, nextStep))
                    }
                    TextButton(
                        onClick = {
                            if (hasUserAuthoredDraft) replacementSessionId = session.id
                            else onRepeatSession(session.id)
                        },
                        enabled = canRepeat,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("focus_recent_work_use_settings"),
                    ) { Text(stringResource(R.string.focus_recent_use_settings)) }
                }
            }
        }
    }

    replacementSessionId?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { replacementSessionId = null },
            title = { Text(stringResource(R.string.focus_recent_replace_title)) },
            text = { Text(stringResource(R.string.focus_recent_replace_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        replacementSessionId = null
                        onRepeatSession(sessionId)
                    },
                    modifier = Modifier.testTag("focus_recent_replace_confirm"),
                ) { Text(stringResource(R.string.focus_recent_replace_setup)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { replacementSessionId = null },
                    modifier = Modifier.testTag("focus_recent_replace_cancel"),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
