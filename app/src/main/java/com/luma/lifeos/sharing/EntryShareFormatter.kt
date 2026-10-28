package com.luma.lifeos.sharing

import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.MoodDetails

fun validateEntryShareText(text: String): EntryShareTextError? = when {
    text.isBlank() -> EntryShareTextError.EMPTY
    text.toByteArray(Charsets.UTF_8).size > 65_536 -> EntryShareTextError.TOO_LARGE
    else -> null
}

fun formatEntryShare(snapshot: EntryShareSnapshot, options: EntryShareOptions, context: EntryShareTextContext): String {
    val record = snapshot.record
    val event = record.event
    val moodMode = snapshot.request.kind == EntryShareKind.MOOD
    val marker = !moodMode && event.type != EventType.MEMORY
    val heading = when {
        moodMode -> context.feelingHeading(context.scoreLabels[requireNotNull(record.mood).score].orEmpty())
        marker -> context.memoryHeading
        else -> event.title?.takeIf { it.isNotBlank() } ?: context.memoryHeading
    }
    val sections = mutableListOf<String>()
    fun add(value: String?) { value?.takeIf { it.isNotBlank() }?.let(sections::add) }
    fun moodSections(mood: MoodDetails, skipNote: String? = null): List<String> {
        val parts = mutableListOf<String>()
        if (mood.emotions.isNotEmpty()) parts.add("${context.emotionHeading}: ${mood.emotions.joinToString(", ") { context.emotionName(it) }}")
        mood.note?.takeIf { options.includeNote && it.isNotBlank() && it != skipNote }?.let(parts::add)
        return parts
    }
    val memoryNote = if (event.type == EventType.MEMORY) record.memory?.note ?: event.note else record.memory?.note
    if (moodMode) sections.addAll(moodSections(requireNotNull(record.mood)))
    else if (options.includeNote) add(memoryNote)
    if (marker && options.includeSourceContent) {
        val source = mutableListOf<String>()
        record.journal?.let { journal ->
            journal.title?.takeIf { it.isNotBlank() }?.let(source::add)
            source.add(journal.body)
        }
        record.mood?.let { mood ->
            source.add(context.feelingHeading(context.scoreLabels[mood.score].orEmpty()))
            source.addAll(moodSections(mood, if (options.includeNote) memoryNote else null))
            if (options.includeEnergy) mood.energy?.let { source.add("${context.energyHeading}: $it/5") }
            if (options.includeStress) mood.stress?.let { source.add("${context.stressHeading}: $it/5") }
        }
        if (source.isNotEmpty()) add(context.sourceHeading + "\n" + source.joinToString("\n\n"))
    }
    if (options.includePeople && record.people.isNotEmpty()) add("${context.peopleHeading}: ${record.people.joinToString(", ") { it.name }}")
    if (options.includePlace) record.place?.let { add("${context.placeHeading}: ${it.name}") }
    if (moodMode) record.mood?.let { mood ->
        if (options.includeEnergy) mood.energy?.let { add("${context.energyHeading}: $it/5") }
        if (options.includeStress) mood.stress?.let { add("${context.stressHeading}: $it/5") }
    }
    val header = listOfNotNull(heading, if (options.includeDate) {
        if (moodMode) context.formatMoodDateTime(event.occurredAt, snapshot.zone)
        else context.formatDate(event.occurredAt, snapshot.zone)
    } else null).joinToString("\n")
    return (listOf(header) + sections + "#Luma").joinToString("\n\n")
}
