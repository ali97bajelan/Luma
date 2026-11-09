package com.luma.lifeos.ui.privacy

import com.luma.lifeos.ui.common.LumaUi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.R
import com.luma.lifeos.lock.LockCredentialInputPolicy
import com.luma.lifeos.lock.LockCredentialType
import com.luma.lifeos.lock.LockPatternRules
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaOutlinedTextField

/** Process-local editor. Its secret strings are never saveable and are cleared on stop/disposal. */
class LockCredentialEditorState internal constructor(
    val type: LockCredentialType,
    private val confirmationRequired: Boolean = false,
) {
    var entry by mutableStateOf("")
        private set
    var confirmation by mutableStateOf("")
        private set
    var revealed by mutableStateOf(false)
        private set
    var patternMismatch by mutableStateOf(false)
        private set
    var resetGeneration by mutableStateOf(0)
        private set
    private var patternEntry by mutableStateOf<CharArray?>(null)
    private var patternConfirmation by mutableStateOf<CharArray?>(null)

    val hasEntry: Boolean get() = if (type == LockCredentialType.PATTERN) patternEntry != null else entry.isNotEmpty()
    val hasConfirmation: Boolean get() = if (type == LockCredentialType.PATTERN) {
        patternConfirmation != null
    } else {
        confirmation.isNotEmpty()
    }

    val isComplete: Boolean
        get() {
            if (type == LockCredentialType.PATTERN) {
                val first = patternEntry?.copyOf() ?: return false
                if (!confirmationRequired) {
                    first.fill('\u0000')
                    return true
                }
                val second = patternConfirmation?.copyOf()
                val matches = second != null && first.contentEquals(second)
                first.fill('\u0000')
                second?.fill('\u0000')
                return matches
            }
            val first = canonical(entry) ?: return false
            if (!confirmationRequired) {
                first.fill('\u0000')
                return true
            }
            val second = canonical(confirmation)
            val matches = second != null && first.contentEquals(second)
            first.fill('\u0000')
            second?.fill('\u0000')
            return matches
        }

    fun updateEntry(value: String) {
        entry = value
        patternMismatch = false
    }

    fun updateConfirmation(value: String) {
        confirmation = value
        patternMismatch = false
    }

    fun setPatternEntry(nodes: List<Int>) {
        val encoded = LockPatternRules.encode(nodes) ?: return
        patternEntry?.fill('\u0000')
        patternConfirmation?.fill('\u0000')
        patternEntry = encoded
        patternConfirmation = null
        patternMismatch = false
    }

    fun beginPatternEntry() {
        patternEntry?.fill('\u0000')
        patternConfirmation?.fill('\u0000')
        patternEntry = null
        patternConfirmation = null
        patternMismatch = false
    }

    fun clearPatternConfirmation() {
        patternConfirmation?.fill('\u0000')
        patternConfirmation = null
        patternMismatch = false
    }

    fun setPatternConfirmation(nodes: List<Int>) {
        val encoded = LockPatternRules.encode(nodes) ?: return
        val matches = patternEntry?.contentEquals(encoded) == true
        patternConfirmation?.fill('\u0000')
        if (matches) {
            patternConfirmation = encoded
            patternMismatch = false
        } else {
            encoded.fill('\u0000')
            patternConfirmation = null
            patternMismatch = true
        }
    }

    fun toggleReveal() {
        revealed = !revealed
    }

    /** Transfers a fresh canonical array and clears every visible holder. */
    fun takeSecret(): CharArray? {
        val first = if (type == LockCredentialType.PATTERN) patternEntry?.copyOf() else canonical(entry)
        first ?: return null
        if (confirmationRequired) {
            val second = if (type == LockCredentialType.PATTERN) patternConfirmation?.copyOf() else canonical(confirmation)
            val matches = second != null && first.contentEquals(second)
            second?.fill('\u0000')
            if (!matches) {
                first.fill('\u0000')
                return null
            }
        }
        clear()
        return first
    }

    fun clear() {
        entry = ""
        confirmation = ""
        patternEntry?.fill('\u0000')
        patternConfirmation?.fill('\u0000')
        patternEntry = null
        patternConfirmation = null
        revealed = false
        patternMismatch = false
        resetGeneration++
    }

    private fun canonical(value: String): CharArray? {
        val source = value.toCharArray()
        return try {
            LockCredentialInputPolicy.canonicalize(type, source)
        } finally {
            source.fill('\u0000')
        }
    }
}

@Composable
fun rememberLockCredentialEditorState(
    type: LockCredentialType,
    requireConfirmation: Boolean = false,
): LockCredentialEditorState {
    val state = remember(type, requireConfirmation) { LockCredentialEditorState(type, requireConfirmation) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.clear()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.clear()
        }
    }
    return state
}

@Composable
fun LockCredentialTypeSelector(
    selected: LockCredentialType,
    enabled: Boolean,
    onSelected: (LockCredentialType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.app_lock_credential_selector), style = MaterialTheme.typography.titleSmall)
        LockCredentialType.entries.forEach { type ->
            val label = stringResource(type.labelResource())
            Row(
                modifier = Modifier.fillMaxWidth()
                    .selectable(
                        selected = selected == type,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(type) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == type, enabled = enabled, onClick = null)
                Text(label, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LockCredentialInput(
    state: LockCredentialEditorState,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onPatternCompleted: () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(state.type.helpResource()), style = MaterialTheme.typography.bodySmall)
        if (state.type == LockCredentialType.PATTERN) {
            Text(stringResource(R.string.app_lock_pattern_draw))
            LockPatternInput(
                enabled = enabled,
                onCompleted = { nodes ->
                    state.setPatternEntry(nodes)
                    onPatternCompleted()
                },
                onStarted = state::beginPatternEntry,
                onCleared = state::beginPatternEntry,
                resetKey = state to state.resetGeneration,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.hasEntry) Text(stringResource(R.string.app_lock_pattern_ready))
        } else {
            val valid = state.entry.isEmpty() || canonicalValid(state.type, state.entry)
            LumaOutlinedTextField(
                value = state.entry,
                onValueChange = state::updateEntry,
                enabled = enabled,
                singleLine = true,
                isError = !valid,
                label = { Text(label) },
                visualTransformation = if (state.revealed) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (state.type == LockCredentialType.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    LumaTextButton(enabled = enabled, onClick = state::toggleReveal) {
                        Text(stringResource(if (state.revealed) R.string.app_lock_credential_hide else R.string.app_lock_credential_show))
                    }
                },
            )
            if (!valid) AccessibleFeedback(stringResource(R.string.app_lock_error_credential_invalid))
        }
    }
}

@Composable
fun LockCredentialEnrollment(
    state: LockCredentialEditorState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
        Text(stringResource(state.type.helpResource()), style = MaterialTheme.typography.bodySmall)
        if (state.type == LockCredentialType.PATTERN) {
            val confirmingPattern = state.hasEntry
            Text(stringResource(
                if (confirmingPattern) R.string.app_lock_pattern_draw_again
                else R.string.app_lock_pattern_draw,
            ))
            LockPatternInput(
                enabled = enabled,
                onCompleted = { nodes ->
                    if (state.hasEntry) state.setPatternConfirmation(nodes)
                    else state.setPatternEntry(nodes)
                },
                onStarted = {
                    if (state.hasEntry) state.clearPatternConfirmation()
                    else state.beginPatternEntry()
                },
                onCleared = {
                    if (state.hasEntry) state.clearPatternConfirmation()
                    else state.beginPatternEntry()
                },
                resetKey = state to state.resetGeneration,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isComplete) Text(stringResource(R.string.app_lock_pattern_ready))
            if (state.patternMismatch) {
                AccessibleFeedback(stringResource(R.string.app_lock_error_credential_mismatch))
                LumaTextButton(enabled = enabled, onClick = state::clear) {
                    Text(stringResource(R.string.app_lock_pattern_retry))
                }
            }
        } else {
            CredentialTextInput(state, enabled, state.entry, state::updateEntry, R.string.app_lock_new_credential)
            CredentialTextInput(state, enabled, state.confirmation, state::updateConfirmation, R.string.app_lock_confirm_credential)
            if (state.hasEntry && state.hasConfirmation && !state.isComplete) {
                AccessibleFeedback(stringResource(R.string.app_lock_error_credential_mismatch))
            }
        }
    }
}

@Composable
private fun CredentialTextInput(
    state: LockCredentialEditorState,
    enabled: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
) {
    val valid = value.isEmpty() || canonicalValid(state.type, value)
    LumaOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        isError = !valid,
        label = { Text(stringResource(label)) },
        visualTransformation = if (state.revealed) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (state.type == LockCredentialType.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            LumaTextButton(enabled = enabled, onClick = state::toggleReveal) {
                Text(stringResource(if (state.revealed) R.string.app_lock_credential_hide else R.string.app_lock_credential_show))
            }
        },
    )
    if (!valid) AccessibleFeedback(stringResource(R.string.app_lock_error_credential_invalid))
}

private fun canonicalValid(type: LockCredentialType, value: String): Boolean {
    val source = value.toCharArray()
    return try {
        val canonical = LockCredentialInputPolicy.canonicalize(type, source) ?: return false
        canonical.fill('\u0000')
        true
    } finally {
        source.fill('\u0000')
    }
}

private fun LockCredentialType.labelResource(): Int = when (this) {
    LockCredentialType.PIN -> R.string.app_lock_credential_pin
    LockCredentialType.PASSWORD -> R.string.app_lock_credential_password
    LockCredentialType.PATTERN -> R.string.app_lock_credential_pattern
}

private fun LockCredentialType.helpResource(): Int = when (this) {
    LockCredentialType.PIN -> R.string.app_lock_pin_help
    LockCredentialType.PASSWORD -> R.string.app_lock_password_help
    LockCredentialType.PATTERN -> R.string.app_lock_pattern_help
}
