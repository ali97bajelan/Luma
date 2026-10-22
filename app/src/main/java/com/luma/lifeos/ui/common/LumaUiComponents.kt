package com.luma.lifeos.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.luma.lifeos.R

internal enum class LumaSurfaceTone { Neutral, Reflective }

@Composable
internal fun LumaOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            disabledTextColor = colors.onSurfaceVariant,
            errorTextColor = colors.onSurface,
            focusedContainerColor = colors.surfaceContainerLow,
            unfocusedContainerColor = colors.surfaceContainerLow,
            disabledContainerColor = colors.surfaceContainerLow,
            errorContainerColor = colors.surfaceContainerLow,
            cursorColor = colors.primary,
            errorCursorColor = colors.error,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outlineVariant,
            disabledBorderColor = colors.outlineVariant,
            errorBorderColor = colors.error,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            disabledLabelColor = colors.onSurfaceVariant,
            errorLabelColor = colors.error,
            focusedSupportingTextColor = colors.onSurfaceVariant,
            unfocusedSupportingTextColor = colors.onSurfaceVariant,
            disabledSupportingTextColor = colors.onSurfaceVariant,
            errorSupportingTextColor = colors.error,
        ),
    )
}

@Composable
internal fun LumaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = MaterialTheme.shapes.large,
        containerColor = colors.surfaceContainerHigh,
        iconContentColor = colors.primary,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

@Composable
internal fun LumaDestructiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        content = content,
    )
}

@Composable
internal fun LumaReadingPane(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier.widthIn(max = LumaUi.ReadingWidth).fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
internal fun LumaSectionSurface(
    modifier: Modifier = Modifier,
    tone: LumaSurfaceTone = LumaSurfaceTone.Neutral,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (tone) {
        LumaSurfaceTone.Neutral -> colorScheme.surfaceContainerLow to colorScheme.onSurface
        // Keep a reading surface: consumers include secondary text and primary
        // actions, whose paired colors need a neutral container in both themes.
        LumaSurfaceTone.Reflective -> colorScheme.surfaceContainer to colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LumaUi.SectionRadius),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(LumaUi.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            content = content,
        )
    }
}

@Composable
internal fun LumaListSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LumaUi.InnerCardRadius),
        color = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) { content() }
}

@Composable
internal fun LumaClickableListSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LumaUi.InnerCardRadius),
        color = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) { content() }
}

@Composable
internal fun LumaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
) {
    require((actionLabel == null) == (onAction == null)) {
        "Section header action label and callback must be provided together."
    }

    val titleModifier = Modifier.semantics { heading() }
    if (actionLabel == null || onAction == null) {
        Text(
            text = title,
            modifier = modifier.then(titleModifier),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Start,
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stack = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
        if (stack) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(title, modifier = titleModifier, style = MaterialTheme.typography.headlineSmall)
                SectionActionButton(actionLabel, onAction, actionModifier)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f).then(titleModifier),
                    style = MaterialTheme.typography.headlineSmall,
                )
                SectionActionButton(actionLabel, onAction, actionModifier)
            }
        }
    }
}

@Composable
private fun SectionActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun LumaChoiceRow(
    title: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    showChevron: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        shape = RoundedCornerShape(LumaUi.ChoiceRadius),
        color = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 32.dp)
                .padding(LumaUi.SectionPadding),
            horizontalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(LumaUi.IconSize),
                tint = colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supportingText != null) {
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showChevron) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_forward),
                    contentDescription = null,
                    modifier = Modifier.size(LumaUi.IconSize),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
