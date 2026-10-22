package com.luma.lifeos.ui.review

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.LumaSectionSurface

@Composable
internal fun ReviewSection(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    tag: String,
    @StringRes explanation: Int? = null,
    infoTag: String = "${tag}_about",
    explanationTag: String = "${infoTag}_text",
    content: @Composable ColumnScope.() -> Unit,
) {
    var about by rememberSaveable { mutableStateOf(false) }
    LumaSectionSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                Icon(painterResource(icon), contentDescription = null,
                    modifier = Modifier.size(LumaUi.IconSize), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).testTag("review_heading_$tag").semantics { heading() })
                if (explanation != null) {
                    val state = stringResource(if (about) R.string.review_details_expanded else R.string.review_details_collapsed)
                    IconButton(onClick = { about = true }, modifier = Modifier.testTag(infoTag)
                        .semantics { stateDescription = state }) {
                        Icon(painterResource(R.drawable.ic_info),
                            contentDescription = "${stringResource(title)} · ${stringResource(R.string.review_methodology)}",
                            modifier = Modifier.size(LumaUi.IconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
    if (about && explanation != null) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text(stringResource(title)) },
        text = {
            Text(stringResource(explanation), Modifier.heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()).testTag(explanationTag))
        },
        confirmButton = {
            TextButton(onClick = { about = false }, modifier = Modifier.testTag("${infoTag}_close")) {
                Text(stringResource(R.string.health_close))
            }
        },
    )
}

@Composable
internal fun ReviewEmpty(@StringRes text: Int, tag: String) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(tag))
}
