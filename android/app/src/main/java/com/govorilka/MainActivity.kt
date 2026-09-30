package com.govorilka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.govorilka.ui.GovorilkaNavHost
import com.govorilka.ui.GovorilkaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GovorilkaTheme {
                GovorilkaNavHost()
            }
        }
    }
}
