package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FOCUS_GOAL_MAX_MINUTES
import com.luma.lifeos.domain.focus.FocusGoalState
import com.luma.lifeos.domain.focus.FocusGoalVersion
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.nextGoalMonday
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.FormatStyle
import com.luma.lifeos.language.localizedDate
import java.util.Locale
import java.util.UUID

private data class PendingGoalWrite(
    val context: FocusProductContext,
    val expectedRevision: Long,
    val goal: FocusGoalVersion?,
    val enabled: Boolean?,
)

@Composable
internal fun FocusGoalSettings(repository: FocusProductRepository) {
    val goals by repository.observeGoals().collectAsStateWithLifecycle(FocusGoalState.DISABLED)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var deviceZoneId by rememberSaveable { mutableStateOf(ZoneId.systemDefault().id) }
    val deviceZone = remember(deviceZoneId) { ZoneId.of(deviceZoneId) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) deviceZoneId = ZoneId.systemDefault().id
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var targetText by rememberSaveable { mutableStateOf("") }
    var weekdayMask by rememberSaveable { mutableIntStateOf(0) }
    var selectedZoneId by rememberSaveable { mutableStateOf(deviceZone.id) }
    var pending by remember { mutableStateOf<PendingGoalWrite?>(null) }
    var busy by remember { mutableStateOf(false) }
    var writeFailed by remember { mutableStateOf(false) }
    var inputInvalid by remember { mutableStateOf(false) }
    var pendingEffectiveMonday by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(goals.settingsRevision) {
        val now = Instant.now()
        val configured = goals.pendingGoal(now) ?: goals.activeGoal(now)
            ?: goals.versions.maxWithOrNull(compareBy({ it.effectiveMonday }, { it.id.toString() }))
        if (configured != null) {
            targetText = configured.targetMinutes.toString()
            weekdayMask = configured.weekdayMask
            selectedZoneId = configured.zoneId
        } else {
            targetText = ""
            weekdayMask = 0
            selectedZoneId = deviceZone.id
        }
        pending = null
        pendingEffectiveMonday = null
        writeFailed = false
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("focus_goal_settings"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.focus_goal_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.focus_goal_intro), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(if (goals.enabled) R.string.focus_goal_enabled else R.string.focus_goal_disabled),
            modifier = Modifier.testTag("focus_goal_status"),
            style = MaterialTheme.typography.bodyMedium,
        )
        LumaOutlinedTextField(
            value = targetText,
            onValueChange = {
                targetText = it.filter { char -> char.digitToIntOrNull() != null }
                .map { char -> ('0'.code + requireNotNull(char.digitToIntOrNull())).toChar() }
                .joinToString("").take(5)
                pending = null
                pendingEffectiveMonday = null
                inputInvalid = false
            },
            label = { Text(stringResource(R.string.focus_goal_target_label)) },
            modifier = Modifier.fillMaxWidth().testTag("focus_goal_target"),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(stringResource(R.string.focus_goal_days_heading), style = MaterialTheme.typography.labelLarge)
        DayOfWeek.values().toList().chunked(4).forEach { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { day ->
                    val selected = weekdayMask and (1 shl (day.value - 1)) != 0
                    FilterChip(
                        selected = selected,
                        enabled = !busy,
                        onClick = {
                            val bit = 1 shl (day.value - 1)
                            weekdayMask = if (selected) weekdayMask and bit.inv() else weekdayMask or bit
                            pending = null
                            pendingEffectiveMonday = null
                            inputInvalid = false
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
                            .testTag("focus_goal_day_${day.name.lowercase()}"),
                    )
                }
                repeat(4 - days.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        Text(stringResource(R.string.focus_goal_zone, selectedZoneId), style = MaterialTheme.typography.bodySmall)
        if (selectedZoneId != deviceZone.id) {
            TextButton(
                onClick = {
                    selectedZoneId = deviceZone.id
                    pending = null
                    pendingEffectiveMonday = null
                    inputInvalid = false
                },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_goal_use_device_zone"),
            ) { Text(stringResource(R.string.focus_goal_use_device_zone, deviceZone.id)) }
        }
        val effectiveDate = pendingEffectiveMonday
            ?: goals.pendingGoal(Instant.now())?.effectiveMonday?.toString()
            ?: runCatching { nextGoalMonday(Instant.now(), ZoneId.of(selectedZoneId)).toString() }.getOrNull()
        if (effectiveDate != null) {
            Text(
                stringResource(
                    R.string.focus_goal_effective_date,
                    runCatching { java.time.LocalDate.parse(effectiveDate).format(localizedDate(FormatStyle.MEDIUM)) }
                        .getOrDefault(effectiveDate),
                ),
                modifier = Modifier.testTag("focus_goal_effective_date"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (inputInvalid) {
            Text(stringResource(R.string.focus_goal_invalid), color = MaterialTheme.colorScheme.error)
        }
        if (writeFailed) {
            Text(stringResource(R.string.focus_goal_save_error), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_goal_save_error"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (busy) return@TextButton
                    scope.launch {
                        if (busy) return@launch
                        busy = true
                        try {
                            val parsedTarget = targetText.toIntOrNull()
                            val days = DayOfWeek.values().filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet()
                            val zone = runCatching { ZoneId.of(selectedZoneId) }.getOrNull()
                            if (parsedTarget == null || parsedTarget !in 1..FOCUS_GOAL_MAX_MINUTES || days.isEmpty() || zone == null) {
                                inputInvalid = true
                                return@launch
                            }
                            val command = pending?.takeIf { it.goal != null } ?: run {
                                val revision = runCatching { Math.addExact(goals.settingsRevision, 1L) }.getOrNull()
                                if (revision == null) {
                                    inputInvalid = true
                                    return@launch
                                }
                                val generation = withContext(Dispatchers.IO) { repository.currentGeneration() }
                                val effectiveMonday = nextGoalMonday(Instant.now(), zone)
                                PendingGoalWrite(
                                    context = FocusProductContext(UUID.randomUUID(), generation),
                                    expectedRevision = goals.settingsRevision,
                                    goal = FocusGoalVersion(
                                        id = UUID.randomUUID(),
                                        revision = revision,
                                        effectiveMonday = effectiveMonday,
                                        zoneId = zone.id,
                                        targetMinutes = parsedTarget,
                                        preferredDays = days,
                                    ),
                                    enabled = null,
                                ).also {
                                    pending = it
                                    pendingEffectiveMonday = effectiveMonday.toString()
                                }
                            }
                            val goal = requireNotNull(command.goal)
                            writeFailed = false
                            when (withContext(Dispatchers.IO) {
                                repository.scheduleGoal(goal, command.expectedRevision, command.context)
                            }) {
                                FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> {
                                    pending = null
                                    writeFailed = false
                                }
                                is FocusProductResult.Conflict -> {
                                    pending = null
                                    pendingEffectiveMonday = null
                                    writeFailed = true
                                }
                                is FocusProductResult.Invalid -> {
                                    pending = null
                                    pendingEffectiveMonday = null
                                    inputInvalid = true
                                }
                                is FocusProductResult.Unavailable -> writeFailed = true
                            }
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            writeFailed = true
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_goal_save"),
            ) { Text(stringResource(R.string.focus_goal_save)) }
            if (goals.enabled) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (busy) return@TextButton
                        scope.launch {
                            if (busy) return@launch
                            busy = true
                            try {
                                val command = pending?.takeIf { it.enabled == false } ?: run {
                                    val generation = withContext(Dispatchers.IO) { repository.currentGeneration() }
                                    PendingGoalWrite(
                                        context = FocusProductContext(UUID.randomUUID(), generation),
                                        expectedRevision = goals.settingsRevision,
                                        goal = null,
                                        enabled = false,
                                    ).also { pending = it }
                                }
                                writeFailed = false
                                when (withContext(Dispatchers.IO) {
                                    repository.setGoalsEnabled(false, command.expectedRevision, command.context)
                                }) {
                                    FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> {
                                        pending = null
                                        pendingEffectiveMonday = null
                                    }
                                    is FocusProductResult.Conflict -> { pending = null; writeFailed = true }
                                    is FocusProductResult.Invalid -> { pending = null; inputInvalid = true }
                                    is FocusProductResult.Unavailable -> writeFailed = true
                                }
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                writeFailed = true
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_goal_disable"),
                ) { Text(stringResource(R.string.focus_goal_disable)) }
            }
        }
    }
}
