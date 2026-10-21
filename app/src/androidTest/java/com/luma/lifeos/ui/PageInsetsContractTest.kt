package com.luma.lifeos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.PageNavigation
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PageInsetsContractTest {
    @get:Rule val compose = createComposeRule()

    @Test fun secondaryHeaderStaysPinnedAsBodyScrolls() {
        var navigated = false
        compose.setContent {
            LifeOsTheme {
                AppPageScaffold(
                    title = "Privacy",
                    navigation = PageNavigation.BACK,
                    onNavigate = { navigated = true },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .testTag("page_body")) {
                        Text("First content")
                        repeat(30) { Text("Scrollable content $it") }
                        Text("Last content")
                    }
                }
            }
        }

        compose.onNodeWithTag("page_title").assertIsDisplayed()
        assertEquals(0f, compose.onNodeWithTag("page_header").getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.onNodeWithText("Last content").performTouchInput { swipeUp() }
        compose.onNodeWithText("Last content").assertIsDisplayed()
        compose.onNodeWithTag("page_title").assertIsDisplayed()
        compose.onNodeWithTag("page_back").performClick()
        compose.runOnIdle { assertTrue(navigated) }
    }

    @Test fun navigationRemainsReachableWithLargeTextInRtl() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                LifeOsTheme {
                    AppPageScaffold(
                        title = "Privacy settings and data controls",
                        navigation = PageNavigation.BACK,
                        onNavigate = {},
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) { Text("Content") }
                    }
                }
            }
        }

        compose.onNodeWithTag("page_back").assertIsDisplayed().assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithText("Privacy settings and data controls").assertIsDisplayed()
    }
}
