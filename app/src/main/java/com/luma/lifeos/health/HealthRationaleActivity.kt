package com.luma.lifeos.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.theme.LifeOsTheme

/** Native Health Connect privacy links open an explanation without opening the database. */
class HealthRationaleActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.luma.lifeos.language.AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeOsTheme {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.health_privacy_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.health_explanation))
                        Text(stringResource(R.string.health_privacy_retention))
                        Button(onClick = { finish() }) { Text(stringResource(R.string.health_close)) }
                    }
                }
            }
        }
    }
}
