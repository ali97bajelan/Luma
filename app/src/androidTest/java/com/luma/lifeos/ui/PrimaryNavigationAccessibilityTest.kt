package com.luma.lifeos.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrimaryNavigationAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun labelsFitAtNarrowWidthAndKeepFullLocalizedTabNames() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val languages = listOf(
            LanguageCase(
                tag = "en",
                visible = listOf("Today", "Timeline", "People", "Review", "Settings"),
                full = visibleLabels("Today", "Timeline", "People", "Review", "Settings"),
            ),
            LanguageCase(
                tag = "fr",
                visible = listOf("Jour", "Parcours", "Contacts", "Bilan", "Options"),
                full = visibleLabels("Aujourd’hui", "Chronologie", "Personnes", "Bilan", "Paramètres"),
            ),
            LanguageCase(
                tag = "fa",
                visible = listOf("امروز", "خط زمانی", "افراد", "مرور", "تنظیمات"),
                full = visibleLabels("امروز", "خط زمانی", "افراد", "مرور", "تنظیمات"),
                direction = LayoutDirection.Rtl,
            ),
        )
        val cases = languages.flatMap { language ->
            listOf(320, 411).flatMap { widthDp ->
                listOf(1f, 1.3f, 2f).map { fontScale ->
                    language.copy(widthDp = widthDp, fontScale = fontScale)
                }
            }
        }
        val routes = listOf("today", "timeline", "people", "review", "settings")

        val activeCase = mutableStateOf(cases.first())
        val navigated = mutableListOf<String>()
        compose.setContent {
            val case = activeCase.value
            val locale = Locale.forLanguageTag(case.tag)
            val localized = remember(case.tag) { localizedContext(base, locale) }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density.density, fontScale = case.fontScale),
                LocalLayoutDirection provides case.direction,
            ) {
                LifeOsTheme {
                    PrimaryNavigationBar(
                        selectedRoute = "timeline",
                        onNavigate = { route -> navigated.add(route) },
                        modifier = Modifier.width(case.widthDp.dp),
                    )
                }
            }

        }

        for (case in cases) {
            compose.runOnIdle {
                activeCase.value = case
                navigated.clear()
            }
            compose.waitForIdle()

            routes.forEachIndexed { index, route ->
                val item = compose.onNodeWithTag("nav_$route")
                val itemNode = item.fetchSemanticsNode()
                assertEquals("${case.tag}/${case.widthDp}dp/${case.fontScale}/$route tab role",
                    Role.Tab, itemNode.config[SemanticsProperties.Role])
                item.assertHasClickAction()
                if (route == "timeline") item.assertIsSelected() else item.assertIsNotSelected()
                item.assertContentDescriptionEquals(case.full[index])

                val label = compose.onNodeWithTag("nav_label_$route", useUnmergedTree = true)
                val labelNode = label.fetchSemanticsNode()
                val laidOutText = labelNode.config[SemanticsProperties.Text]
                    .joinToString(separator = "") { it.text }
                    .replace("\u00ad", "")
                assertEquals("${case.tag}/${case.widthDp}dp/${case.fontScale}/$route visible label",
                    case.visible[index], laidOutText)
                val layoutResults = mutableListOf<TextLayoutResult>()
                val layoutAction = labelNode.config[SemanticsActions.GetTextLayoutResult]
                assertTrue("${case.tag}/${case.widthDp}dp/${case.fontScale}/$route text layout is available",
                    layoutAction.action?.invoke(layoutResults) == true && layoutResults.isNotEmpty())
                assertTrue("${case.tag}/${case.widthDp}dp/${case.fontScale}/$route label has more than two lines: ${layoutResults.single().lineCount}",
                    layoutResults.single().lineCount <= 2)
                val textLayout = layoutResults.single()
                val itemBounds = itemNode.boundsInRoot
                val labelBounds = labelNode.boundsInRoot
                (0 until textLayout.lineCount).forEach { line ->
                    val lineWidth = textLayout.getLineRight(line) - textLayout.getLineLeft(line)
                    assertTrue("${case.tag}/${case.widthDp}dp/${case.fontScale}/$route line $line exceeds its text box: " +
                        "lineWidth=$lineWidth textWidth=${textLayout.size.width}",
                        lineWidth <= textLayout.size.width)
                }
                assertTrue("${case.tag}/${case.widthDp}dp/${case.fontScale}/$route label is clipped vertically",
                    !textLayout.didOverflowHeight)
                assertTrue("${case.tag}/${case.widthDp}dp/${case.fontScale}/$route label escaped its tab bounds",
                    labelBounds.left >= itemBounds.left && labelBounds.top >= itemBounds.top &&
                        labelBounds.right <= itemBounds.right && labelBounds.bottom <= itemBounds.bottom)
                label.performClick()
            }
            compose.runOnIdle { assertEquals(routes, navigated) }
        }
    }

    private fun localizedContext(base: Context, locale: Locale): Context {
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    private data class LanguageCase(
        val tag: String,
        val visible: List<String>,
        val full: List<String>,
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val widthDp: Int = 320,
        val fontScale: Float = 2f,
    )

    private companion object {
        fun visibleLabels(vararg labels: String) = labels.toList()
    }
}
