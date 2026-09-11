package com.touhidapps.kmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin

fun main() {
    // main() runs exactly once on desktop, so unlike iOS's ComposeUIViewController
    // (which can re-enter), Koin can start unconditionally, before any UI runs.
    startKoin {
        modules(desktopKoinModule, appModule)
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "KMP-MultiModule-Template") {
            App()
        }
    }
}
