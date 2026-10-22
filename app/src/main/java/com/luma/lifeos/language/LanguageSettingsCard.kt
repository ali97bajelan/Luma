package com.luma.lifeos.language

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaSectionSurface

@Composable
fun LanguageSettingsCard() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val current = AppLanguage.resolve(configuration.locales[0].language, emptyList())
    var saveError by rememberSaveable { mutableStateOf(false) }
    var saveErrorRevision by rememberSaveable { mutableIntStateOf(0) }
    LumaSectionSurface(Modifier.testTag("language_settings")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.language_description))
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    val label = stringResource(when (language) {
                        AppLanguage.ENGLISH -> R.string.language_english
                        AppLanguage.PERSIAN -> R.string.language_persian
                        AppLanguage.FRENCH -> R.string.language_french
                    })
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("language_${language.tag}")
                        .selectable(selected = current == language, role = Role.RadioButton, onClick = {
                            saveError = context.activity()?.let { !AppLanguages.select(it, language) } ?: true
                            if (saveError) saveErrorRevision++
                        }).padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = current == language, onClick = null)
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (saveError) key(saveErrorRevision) {
                AccessibleFeedback(stringResource(R.string.language_save_error), Modifier.testTag("language_save_error"))
            }
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
