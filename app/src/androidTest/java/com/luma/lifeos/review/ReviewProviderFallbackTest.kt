package com.luma.lifeos.review

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.luma.lifeos.R
import com.luma.lifeos.domain.review.ReviewDates
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewMetricKind
import com.luma.lifeos.ui.review.ReviewSourcesDestination
import com.luma.lifeos.ui.review.ReviewUsageDestination
import com.luma.lifeos.ui.theme.LifeOsTheme
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewProviderFallbackTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun absentReviewRepositoryShowsSourcesFallbackAndBackAction() {
        val date = LocalDate.of(2026, 10, 22)
        var wentBack = false
        compose.setContent {
            LifeOsTheme {
                ReviewSourcesDestination(
                    repository = null,
                    dates = ReviewDates(date, date),
                    zone = ZoneOffset.UTC,
                    metric = ReviewMetric(ReviewMetricKind.ENTRIES),
                    onBack = { wentBack = true },
                    onEvent = {},
                )
            }
        }

        compose.onNodeWithTag("screen_review_sources").assertIsDisplayed()
        compose.onNodeWithTag("review_sources_unavailable_message")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.review_sources_provider_unavailable))
        compose.onNodeWithTag("review_sources_back").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test fun absentUsageIntegrationShowsDetailsFallbackAndBackAction() {
        var wentBack = false
        compose.setContent {
            LifeOsTheme {
                ReviewUsageDestination(
                    integration = null,
                    date = LocalDate.of(2026, 10, 22),
                    zone = ZoneOffset.UTC,
                    onBack = { wentBack = true },
                )
            }
        }

        compose.onNodeWithTag("screen_review_usage").assertIsDisplayed()
        compose.onNodeWithTag("review_usage_unavailable_message")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.review_usage_provider_unavailable))
        compose.onNodeWithTag("review_usage_back").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }
}
