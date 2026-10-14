@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.luma.lifeos

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.luma.lifeos.domain.search.SearchOptions
import com.luma.lifeos.domain.search.SearchPage
import com.luma.lifeos.domain.search.SearchQuery
import com.luma.lifeos.domain.repository.SearchRepository
import com.luma.lifeos.ui.privacy.PrivacySettingsContent
import com.luma.lifeos.ui.privacy.PrivacyUiState
import com.luma.lifeos.ui.search.SearchFilters
import com.luma.lifeos.ui.search.SearchInput
import com.luma.lifeos.ui.search.SearchState
import com.luma.lifeos.ui.search.SearchViewModel
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedbackAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun privacySectionsExposeHeadingNavigationAndResetRequestIsPolite() {
        compose.setContent {
            LifeOsTheme {
                PrivacySettingsContent(PrivacyUiState(busy = true, requestAccepted = true), {}, {}, {}, {}, {})
            }
        }

        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val labels = listOf(
            R.string.privacy_title,
            R.string.privacy_storage_heading,
            R.string.privacy_access_heading,
            R.string.privacy_backup_heading,
            R.string.privacy_delete_section,
        ).map(resources::getString)
        labels.forEach { label ->
            compose.onNodeWithText(label).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
        val liveRegion = compose.onNodeWithTag("privacy_requested").fetchSemanticsNode().config[SemanticsProperties.LiveRegion]
        assertEquals(LiveRegionMode.Polite, liveRegion)
    }

    @Test fun invalidSearchDatesBelongToEndpointControlsWithOneLocalAnnouncement() {
        val repository = object : SearchRepository {
            override fun observeChanges(): Flow<Unit> = flowOf(Unit)
            override fun observeOptions(): Flow<SearchOptions> = flowOf(SearchOptions())
            override suspend fun page(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int) = SearchPage(emptyList(), 0, 0)
        }
        val vm = SearchViewModel(repository, SavedStateHandle())
        val input = SearchInput(from = LocalDate.of(2026, 10, 16), through = LocalDate.of(2026, 10, 15))
        compose.setContent {
            LifeOsTheme {
                SearchFilters(SearchState(input, zone = ZoneId.of("UTC"), optionsLoading = false), vm, {})
            }
        }

        compose.onNodeWithTag("search_from").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithTag("search_through").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithTag("search_invalid_dates_dialog").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite), useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
