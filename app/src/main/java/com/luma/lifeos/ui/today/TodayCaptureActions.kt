package com.luma.lifeos.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.ui.common.LumaUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayCaptureActions(
    onDateCapture: (EventType) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
        verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
    ) {
        listOf(EventType.MOOD to R.string.today_capture_mood, EventType.MEMORY to R.string.today_capture_memory)
            .forEach { (type, label) ->
            val icon = when (type) {
                EventType.MOOD -> R.drawable.ic_review_mood
                EventType.MEMORY -> R.drawable.ic_bookmark_outline
                EventType.JOURNAL -> return@forEach
                EventType.ACTIVITY, EventType.APP_USAGE -> return@forEach
            }
            val iconTint = when (type) {
                EventType.MOOD -> colors.primary
                EventType.MEMORY -> colors.secondary
                EventType.JOURNAL -> return@forEach
                EventType.ACTIVITY, EventType.APP_USAGE -> return@forEach
            }
            FilledTonalButton(onClick = { onDateCapture(type) },
                modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
                    .testTag("today_capture_${type.name.lowercase()}"),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.onSurfaceVariant,
                )) {
                Icon(painter = painterResource(icon), contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = iconTint)
                Spacer(Modifier.width(LumaUi.RelatedGap))
                Text(stringResource(label))
            }
        }
    }
}
