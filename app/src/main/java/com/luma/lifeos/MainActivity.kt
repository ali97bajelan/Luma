package com.luma.lifeos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.theme.LifeOsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LifeOsTheme { LifeOsApp() } }
    }
}
