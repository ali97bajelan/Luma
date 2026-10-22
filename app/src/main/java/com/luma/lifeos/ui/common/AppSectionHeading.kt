package com.luma.lifeos.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

@Composable
fun AppSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    Text(text, modifier = modifier.semantics { heading() },
        style = style.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily))
}
