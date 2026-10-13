package com.luma.lifeos.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.luma.lifeos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(title: String, closeTag: String, closeEnabled: Boolean, onClose: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("editor_title")) },
        navigationIcon = {
            IconButton(onClick = onClose, enabled = closeEnabled, modifier = Modifier.testTag(closeTag)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
