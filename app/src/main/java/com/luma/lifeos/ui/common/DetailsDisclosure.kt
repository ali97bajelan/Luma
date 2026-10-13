package com.luma.lifeos.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@Composable
fun DetailsDisclosure(@StringRes title: Int, tag: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag(tag)) {
            Text(stringResource(if (expanded) R.string.hide_details else title))
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
