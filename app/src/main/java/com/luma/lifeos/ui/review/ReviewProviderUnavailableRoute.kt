package com.luma.lifeos.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppSectionHeading
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaSectionSurface

@Composable
internal fun ReviewProviderUnavailableRoute(
    @StringRes title: Int,
    @StringRes message: Int,
    screenTag: String,
    messageTag: String,
    backTag: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LumaReadingPane(Modifier.fillMaxSize().testTag(screenTag)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag(backTag)) {
                Text(stringResource(R.string.back))
            }
            AppSectionHeading(stringResource(title))
            LumaSectionSurface {
                AccessibleFeedback(stringResource(message), Modifier.testTag(messageTag), isError = false)
            }
        }
    }
}
