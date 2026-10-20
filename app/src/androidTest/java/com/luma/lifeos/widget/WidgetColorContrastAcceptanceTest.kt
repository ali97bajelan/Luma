package com.luma.lifeos.widget

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetColorContrastAcceptanceTest {
    @Test fun semanticLightAndDarkRolesMeetContrastTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lightSurface = color(context, "widget_surface_light")
        val darkSurface = color(context, "widget_surface_dark")
        val lightMutation = color(context, "widget_mutation_fill_light")
        val lightMutationPressed = color(context, "widget_mutation_fill_pressed_light")
        val darkMutation = color(context, "widget_mutation_fill_dark")
        val darkMutationPressed = color(context, "widget_mutation_fill_pressed_dark")

        listOf(
            "widget_text_primary_light" to lightSurface,
            "widget_text_secondary_light" to lightSurface,
            "widget_action_utility_light" to lightSurface,
            "widget_overdue_light" to lightSurface,
            "widget_error_light" to lightSurface,
        ).forEach { (foreground, background) ->
            assertContrast(context, foreground, background, 4.5, "light surface")
        }
        assertContrast(context, "widget_mutation_text_light", lightMutation, 4.5, "light mutation fill")
        assertContrast(context, "widget_mutation_text_light", lightMutationPressed, 4.5, "light pressed mutation fill")
        assertContrast(context, "widget_action_utility_light", lightMutationPressed, 4.5, "light pressed utility action")
        assertContrast(context, "widget_focus_ring_light", lightSurface, 3.0, "light surface focus")
        assertContrast(context, "widget_focus_ring_light", lightMutation, 3.0, "light mutation focus")
        assertContrast(context, "widget_focus_ring_light", lightMutationPressed, 3.0, "light pressed focus")

        listOf(
            "widget_text_primary_dark" to darkSurface,
            "widget_text_secondary_dark" to darkSurface,
            "widget_action_utility_dark" to darkSurface,
            "widget_overdue_dark" to darkSurface,
            "widget_error_dark" to darkSurface,
        ).forEach { (foreground, background) ->
            assertContrast(context, foreground, background, 4.5, "dark surface")
        }
        assertContrast(context, "widget_mutation_text_dark", darkMutation, 4.5, "dark mutation fill")
        assertContrast(context, "widget_mutation_text_dark", darkMutationPressed, 4.5, "dark pressed mutation fill")
        assertContrast(context, "widget_action_utility_dark", darkMutationPressed, 4.5, "dark pressed utility action")
        assertContrast(context, "widget_focus_ring_dark", darkSurface, 3.0, "dark surface focus")
        assertContrast(context, "widget_focus_ring_dark", darkMutation, 3.0, "dark mutation focus")
        assertContrast(context, "widget_focus_ring_dark", darkMutationPressed, 3.0, "dark pressed focus")
    }

    private fun assertContrast(
        context: android.content.Context,
        foregroundName: String,
        background: Int,
        minimum: Double,
        state: String,
    ) {
        val foreground = color(context, foregroundName)
        val ratio = contrastRatio(foreground, background)
        assertTrue("$foregroundName on $state is ${"%.2f".format(ratio)}:1; expected at least $minimum:1",
            ratio >= minimum)
    }

    private fun color(context: android.content.Context, name: String): Int {
        val id = context.resources.getIdentifier(name, "color", context.packageName)
        assertTrue("Missing semantic widget color $name", id != 0)
        return context.getColor(id)
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val high = maxOf(luminance(first), luminance(second))
        val low = minOf(luminance(first), luminance(second))
        return (high + 0.05) / (low + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color))
    }
}
