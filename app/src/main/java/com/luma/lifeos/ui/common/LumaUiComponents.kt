package com.luma.lifeos.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.luma.lifeos.R
import com.luma.lifeos.ui.theme.onPrimaryFixed
import com.luma.lifeos.ui.theme.primaryFixed

internal enum class LumaSurfaceTone { Neutral, Reflective }

@Composable
internal fun LumaCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LumaUi.SectionRadius),
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors ?: CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLow,
            contentColor = scheme.onSurface,
        ),
        elevation = elevation ?: CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
internal fun LumaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
        enabled = enabled,
        shape = shape,
        colors = colors ?: ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
        ),
        elevation = null,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun LumaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors? = null,
    border: BorderStroke? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
        enabled = enabled,
        shape = shape,
        colors = colors ?: ButtonDefaults.outlinedButtonColors(),
        border = border ?: BorderStroke(1.dp, if (enabled) scheme.outline else scheme.onSurface.copy(alpha = 0.12f)),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun LumaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
        enabled = enabled,
        shape = shape,
        colors = colors ?: ButtonDefaults.textButtonColors(),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun LumaPrimaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = LumaUi.PrimaryActionHeight),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ),
        elevation = null,
        content = content,
    )
}

@Composable
internal fun LumaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = CircleShape,
    colors: SelectableChipColors? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val chipColors = colors ?: FilterChipDefaults.filterChipColors(
        containerColor = scheme.surfaceContainerLow,
        labelColor = scheme.onSurfaceVariant,
        iconColor = scheme.onSurfaceVariant,
        selectedContainerColor = scheme.primaryFixed,
        selectedLabelColor = scheme.onPrimaryFixed,
        selectedLeadingIconColor = scheme.onPrimaryFixed,
        selectedTrailingIconColor = scheme.onPrimaryFixed,
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = chipColors,
    )
}

@Composable
internal fun LumaSegmentedControl(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(LumaUi.RelatedGap).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun LumaSegmentedOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = LumaUi.MinTouchTargetHeight)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = if (selected) scheme.primaryFixed else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryFixed else scheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = LumaUi.ChoiceGap),
            contentAlignment = Alignment.Center,
        ) {
            label()
        }
    }
}

@Composable
internal fun LumaAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
        trailingIcon = trailingIcon,
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = scheme.surfaceContainerLow,
            labelColor = scheme.onSurfaceVariant,
            trailingIconContentColor = scheme.primary,
        ),
    )
}

@Composable
internal fun LumaInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    InputChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
        enabled = enabled,
        trailingIcon = trailingIcon,
        shape = CircleShape,
        colors = InputChipDefaults.inputChipColors(
            containerColor = scheme.surfaceContainerLow,
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.secondaryContainer,
            selectedLabelColor = scheme.onSecondaryContainer,
            selectedLeadingIconColor = scheme.onSecondaryContainer,
            selectedTrailingIconColor = scheme.onSecondaryContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LumaModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LumaDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        shape = MaterialTheme.shapes.large,
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        tonalElevation = 0.dp,
        properties = properties,
        content = content,
    )
}

@Composable
internal fun LumaEditorActionFooter(
    content: @Composable ColumnScope.() -> Unit,
) {
    LumaReadingPane(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .imePadding()
                .padding(horizontal = LumaUi.PageGutter, vertical = LumaUi.EditorFooterVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            content = content,
        )
    }
}

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
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
    shape: Shape? = null,
    colors: TextFieldColors? = null,
) {
    val scheme = MaterialTheme.colorScheme
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
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = textStyle ?: LocalTextStyle.current,
        shape = shape ?: MaterialTheme.shapes.medium,
        colors = colors ?: OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            disabledTextColor = scheme.onSurfaceVariant,
            errorTextColor = scheme.onSurface,
            focusedContainerColor = scheme.surfaceContainerLow,
            unfocusedContainerColor = scheme.surfaceContainerLow,
            disabledContainerColor = scheme.surfaceContainerLow,
            errorContainerColor = scheme.surfaceContainerLow,
            cursorColor = scheme.primary,
            errorCursorColor = scheme.error,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant,
            disabledBorderColor = scheme.outlineVariant,
            errorBorderColor = scheme.error,
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            disabledLabelColor = scheme.onSurfaceVariant,
            errorLabelColor = scheme.error,
            focusedSupportingTextColor = scheme.onSurfaceVariant,
            unfocusedSupportingTextColor = scheme.onSurfaceVariant,
            disabledSupportingTextColor = scheme.onSurfaceVariant,
            errorSupportingTextColor = scheme.error,
        ),
    )
}

@Composable
internal fun LumaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
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
        icon = icon,
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
    LumaTextButton(
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
