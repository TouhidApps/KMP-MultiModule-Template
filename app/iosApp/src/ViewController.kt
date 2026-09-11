package com.touhidapps.kmp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools

// Unlike Android (MyApp.onCreate starts Koin before any UI runs), iOS has no
// equivalent Application-level hook here, so Koin is started on first frame,
// guarded against a second start on hot-reload/re-entry.
fun ViewController() = ComposeUIViewController {

    var isKoinInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val alreadyExists = KoinPlatformTools.defaultContext().getOrNull() != null
        if (!alreadyExists) {
            startKoin {
                modules(iosKoinModule, appModule)
            }
        }
        isKoinInitialized = true
    }

    if (isKoinInitialized) {
        App()
    }
}
