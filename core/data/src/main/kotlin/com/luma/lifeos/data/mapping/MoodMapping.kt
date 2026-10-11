package com.luma.lifeos.data.mapping

import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.domain.model.Emotion
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MoodDetails
import java.util.UUID

fun EventWithDetails.toRecord(): LifeEventRecord = LifeEventRecord(event.toDomain(), mood?.let {
    MoodDetails(it.entry.moodScore,
        it.emotions.map { emotion -> Emotion(UUID.fromString(emotion.id), emotion.name) }
            .sortedWith(compareBy<Emotion> { emotion -> emotion.name }.thenBy { emotion -> emotion.id }),
        it.entry.energy, it.entry.stress, it.entry.note)
})
