package com.luma.lifeos.ui.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@Composable
internal fun MoreRoute(
    onPeoplePlaces: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("screen_more")
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.more),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
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

@Composable
private fun MoreEntry(
    @StringRes title: Int,
    @StringRes description: Int,
    @DrawableRes icon: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag(testTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
