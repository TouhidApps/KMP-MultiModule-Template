package com.touhidapps.kmp

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApp: Application() {

    override fun onCreate() {
        super.onCreate()

        // No need to check koin is already running or not. as application class runs one time
        startKoin {
            androidContext(this@MyApp) // Required for Android

            modules(
                androidKoinModule, // load android specific modules
                appModule, // Load common Koin modules,

            )

        } // startKoin

    }

}