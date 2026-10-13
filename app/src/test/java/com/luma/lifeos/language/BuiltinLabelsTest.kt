package com.luma.lifeos.language

import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventSource
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltinLabelsTest {
    @Test fun unchangedSeededEmotionsHaveTranslatedDisplayResources() {
        val fixtures = listOf(
            Triple("e278885f-841a-3c0e-a9d7-0451f80a5076", "Happy", R.string.emotion_happy),
            Triple("e62c9cb2-1ffa-3ce1-9bb6-edca2f47231e", "Calm", R.string.emotion_calm),
            Triple("132f62f6-3a23-39c0-8f76-36de551d1ace", "Grateful", R.string.emotion_grateful),
            Triple("dde8eba4-6c05-37eb-b524-17db6d43c2c1", "Excited", R.string.emotion_excited),
            Triple("7ec3e2ce-93fa-3300-abb1-5957987d60cd", "Hopeful", R.string.emotion_hopeful),
            Triple("1639dc03-dfec-37b6-9b2b-6139aa0fa97a", "Proud", R.string.emotion_proud),
            Triple("c296643c-c45c-3ff7-ac66-741faa7c44eb", "Sad", R.string.emotion_sad),
            Triple("7790e2e2-6c9d-3cfd-81f4-2fc8b2ed8434", "Anxious", R.string.emotion_anxious),
            Triple("c6df7110-21b7-3324-8c37-2cb988633472", "Angry", R.string.emotion_angry),
            Triple("29109e1d-d933-3d39-b61e-603441a7c860", "Lonely", R.string.emotion_lonely),
            Triple("5ca6e35a-58ad-3763-98a5-d994c35fb412", "Tired", R.string.emotion_tired),
            Triple("7b7b4831-32ca-303b-a1da-2c8dff3a3b13", "Overwhelmed", R.string.emotion_overwhelmed),
        )
        fixtures.forEach { (id, name, resource) ->
            assertEquals(name, resource, builtinEmotionResource(UUID.fromString(id), name))
        }
    }

    @Test fun customSameNameEmotionKeepsItsUserLabel() {
        assertNull(builtinEmotionResource(UUID(0, 1), "Happy"))
    }

    @Test fun renamedSeededEmotionKeepsItsUserLabel() {
        assertNull(builtinEmotionResource(UUID.fromString("e278885f-841a-3c0e-a9d7-0451f80a5076"), "Joy"))
    }

    @Test fun anotherCanonicalNameOnSeededIdentityIsStillUserContent() {
        assertNull(builtinEmotionResource(UUID.fromString("e278885f-841a-3c0e-a9d7-0451f80a5076"), "Calm"))
    }

    @Test fun nativeStepsAndCountHaveTranslatedDisplayResources() {
        assertEquals(R.string.activity_steps to R.string.activity_count,
            nativeActivityResources("steps", "count", EventSource.HEALTH_CONNECT))
    }

    @Test fun customActivityLabelsStayUnchangedEvenWhenNamesMatch() {
        assertNull(nativeActivityResources("steps", "count", EventSource.MANUAL))
        assertNull(nativeActivityResources("steps", "km", EventSource.HEALTH_CONNECT))
        assertNull(nativeActivityResources("custom", "count", EventSource.HEALTH_CONNECT))
    }
}
