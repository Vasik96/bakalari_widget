package com.vasik96.bakalariwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    val s by vm.ui.collectAsStateWithLifecycle()
                    if (!s.loggedIn || s.needsLogin) LoginScreen(s, vm::login)
                    else DashboardScreen(s, vm::sync, vm::logout)
                }
            }
        }
    }
}
