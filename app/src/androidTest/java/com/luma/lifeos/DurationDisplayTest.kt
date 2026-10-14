package com.luma.lifeos

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.lifeos.ui.review.reviewDuration
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.usage.appUsageDuration
import com.luma.lifeos.ui.common.exactDuration
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DurationDisplayTest {
    @get:Rule val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()

    @Before fun setEnglishLocale() { Locale.setDefault(Locale.US) }
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }

    @Test fun summaryPrecisionMatchesAcrossWrappersAndExactDetailKeepsMilliseconds() {
        val values = listOf(0L, 1L, 999L, 1_000L, 59_999L, 60_000L, 3_599_999L, 3_600_000L)
        compose.setContent {
            LifeOsTheme {
                Column {
                    values.forEachIndexed { index, value ->
                        Text(appUsageDuration(value), Modifier.testTag("app_$index"))
                        Text(reviewDuration(value), Modifier.testTag("review_$index"))
                    }
                    Text(reviewDuration(null), Modifier.testTag("review_null"))
                    Text(stringResource(R.string.usage_exact_duration, exactDuration(1_001)), Modifier.testTag("exact"))
                }
            }
        }
        val expected = listOf("0 sec", "<1 sec", "<1 sec", "1 sec", "59 sec", "1 min", "59 min", "1 h 0 min")
        expected.forEachIndexed { index, value ->
            compose.onNodeWithTag("app_$index").assertTextEquals(value)
            compose.onNodeWithTag("review_$index").assertTextEquals(value)
        }
        compose.onNodeWithTag("review_null").assertTextEquals("Unavailable")
        compose.onNodeWithTag("exact").assertTextEquals("Exact duration: 0 h 0 min 1.001 s")
    }

    @Test fun languageResourcesAndExactDecimalUseTheActiveLocale() {
        val target = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val context = mutableStateOf(localizedContext(target, Locale.US))
        compose.setContent {
            val localized = context.value
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
            ) {
                LifeOsTheme {
                    Column {
                        Text(appUsageDuration(1), Modifier.testTag("under_second"))
                        Text(stringResource(R.string.usage_exact_duration, exactDuration(1_001)), Modifier.testTag("exact_locale"))
                    }
                }
            }
        }
        compose.onNodeWithTag("under_second").assertTextEquals("<1 sec")
        compose.onNodeWithTag("exact_locale").assertTextEquals("Exact duration: 0 h 0 min 1.001 s")

        compose.runOnIdle {
            Locale.setDefault(Locale.FRANCE)
            context.value = localizedContext(target, Locale.FRANCE)
        }
        compose.onNodeWithTag("under_second").assertTextEquals("<1 s")
        compose.onNodeWithTag("exact_locale").assertTextEquals("Durée exacte : 0 h 0 min 1,001 s")

        val persian = Locale.forLanguageTag("fa")
        compose.runOnIdle {
            Locale.setDefault(persian)
            context.value = localizedContext(target, persian)
        }
        compose.onNodeWithTag("under_second").assertTextEquals("کمتر از ۱ ثانیه")
        compose.onNodeWithTag("exact_locale").assertTextEquals("مدت دقیق: ۰ ساعت ۰ دقیقه ۱٫۰۰۱ ثانیه")
    }

    private fun localizedContext(target: Context, locale: Locale): Context {
        val configuration = Configuration(target.resources.configuration).apply { setLocale(locale) }
        return target.createConfigurationContext(configuration)
    }
}
