package com.luma.lifeos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.theme.LifeOsTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.luma.lifeos.language.AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val luma = application as LumaApplication
        val storage = luma.storage
        setContent { LifeOsTheme { LifeOsApp(storage.lifeEvents, storage.moods, storage.journals, storage.memories, storage.people, storage.places, storage.photos, storage.timeline, luma.usage, storage.summaries, luma.health, storage.search, storage.reviews, storage.backup) } }
    }
}
