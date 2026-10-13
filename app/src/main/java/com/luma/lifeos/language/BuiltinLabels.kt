package com.luma.lifeos.language

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.Emotion
import com.luma.lifeos.domain.model.EventSource
import java.util.UUID

private val seededEmotions = listOf(
    "Happy" to R.string.emotion_happy,
    "Calm" to R.string.emotion_calm,
    "Grateful" to R.string.emotion_grateful,
    "Excited" to R.string.emotion_excited,
    "Hopeful" to R.string.emotion_hopeful,
    "Proud" to R.string.emotion_proud,
    "Sad" to R.string.emotion_sad,
    "Anxious" to R.string.emotion_anxious,
    "Angry" to R.string.emotion_angry,
    "Lonely" to R.string.emotion_lonely,
    "Tired" to R.string.emotion_tired,
    "Overwhelmed" to R.string.emotion_overwhelmed,
).associate { (name, resource) ->
    UUID.nameUUIDFromBytes("luma.emotion.$name".toByteArray(Charsets.UTF_8)) to (name to resource)
}

/** Only unchanged seeded rows are app vocabulary; custom and renamed rows remain user content. */
@StringRes
internal fun builtinEmotionResource(id: UUID, name: String): Int? =
    seededEmotions[id]?.takeIf { it.first == name }?.second

@Composable
fun emotionDisplayName(id: UUID, name: String): String =
    builtinEmotionResource(id, name)?.let { stringResource(it) } ?: name

@Composable
fun emotionDisplayName(emotion: Emotion): String = emotionDisplayName(emotion.id, emotion.name)

/** Health Connect owns this canonical pair; arbitrary recorded activity labels remain unchanged. */
internal fun nativeActivityResources(type: String, unit: String, source: EventSource): Pair<Int, Int>? =
    if (source == EventSource.HEALTH_CONNECT && type == "steps" && unit == "count")
        R.string.activity_steps to R.string.activity_count else null

@Composable
fun activityTypeDisplayName(type: String, unit: String, source: EventSource): String =
    nativeActivityResources(type, unit, source)?.let { stringResource(it.first) } ?: type

@Composable
fun activityUnitDisplayName(type: String, unit: String, source: EventSource): String =
    nativeActivityResources(type, unit, source)?.let { stringResource(it.second) } ?: unit
