package com.nzshores.llmserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nzshores.llmserver.ui.nav.LlmManagerNavHost
import com.nzshores.llmserver.ui.theme.LlmManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LlmManagerTheme {
                LlmManagerNavHost()
            }
        }
    }
}
