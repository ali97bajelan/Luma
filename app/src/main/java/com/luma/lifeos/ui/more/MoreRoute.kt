package com.luma.lifeos.ui.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaChoiceRow
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation

@Composable
internal fun MoreRoute(
    onPeoplePlaces: () -> Unit,
    onSettings: () -> Unit,
    onTasks: (() -> Unit)? = null,
    onHabits: (() -> Unit)? = null,
) {
    AppPageScaffold(title = stringResource(R.string.more), navigation = PageNavigation.NONE) { pagePadding ->
        LumaReadingPane(
            modifier = Modifier.fillMaxSize().padding(pagePadding).testTag("screen_more"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                onHabits?.let { action ->
                    MoreEntry(
                        title = R.string.habit_title,
                        description = R.string.more_habits_description,
                        icon = R.drawable.ic_nav_habits,
                        testTag = "more_habits",
                        onClick = action,
                    )
                }
                onTasks?.let { action ->
                    MoreEntry(
                        title = R.string.more_tasks,
                        description = R.string.more_tasks_description,
                        icon = R.drawable.ic_nav_tasks,
                        testTag = "more_tasks",
                        onClick = action,
                    )
                }
                MoreEntry(
                    title = R.string.more_people_places,
                    description = R.string.more_people_places_description,
                    icon = R.drawable.ic_nav_people,
                    testTag = "more_people_places",
                    onClick = onPeoplePlaces,
                )
                MoreEntry(
                    title = R.string.more_settings,
                    description = R.string.more_settings_description,
                    icon = R.drawable.ic_nav_settings,
                    testTag = "more_settings",
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun MoreEntry(
    @StringRes title: Int,
    @StringRes description: Int,
    @DrawableRes icon: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    LumaChoiceRow(
        title = stringResource(title),
        icon = icon,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        supportingText = stringResource(description),
        showChevron = true,
    )
}
