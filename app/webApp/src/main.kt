package com.touhidapps.kmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // main() runs exactly once on web, so unlike iOS's ComposeUIViewController
    // (which can re-enter), Koin can start unconditionally, before any UI runs.
    startKoin {
        modules(webKoinModule, appModule)
    }

    ComposeViewport {
        App()
    }
}
