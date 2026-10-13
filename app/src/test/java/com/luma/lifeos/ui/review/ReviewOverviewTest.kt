package com.luma.lifeos.ui.review

import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.review.ReviewActivity
import com.luma.lifeos.domain.summary.ActivityTotal
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ReviewOverviewTest {
    @Test fun mostSampledActivityIgnoresNumericMagnitude() {
        val sampled = activity("distance", "km", EventSource.MANUAL, 4, "2")
        val larger = activity("steps", "count", EventSource.HEALTH_CONNECT, 1, "9000")
        assertEquals(sampled, mostSampledActivity(listOf(larger, sampled)))
        assertNull(mostSampledActivity(emptyList()))
    }

    @Test fun mostSampledActivityUsesStableTiesAcrossUnits() {
        val first = activity("a", "km", EventSource.HEALTH_CONNECT, 3, "1")
        val others = listOf(
            activity("b", "count", EventSource.MANUAL, 3, "9999"),
            activity("a", "m", EventSource.HEALTH_CONNECT, 3, "9999"),
            activity("a", "km", EventSource.MANUAL, 3, "9999"),
        )
        assertEquals(first, mostSampledActivity(others + first))
        assertEquals(first, mostSampledActivity(listOf(first) + others.reversed()))
    }

    private fun activity(type: String, unit: String, source: EventSource, samples: Int, value: String) =
        ReviewActivity(ActivityTotal(type, unit, source, samples, BigDecimal(value)), 1)
}
