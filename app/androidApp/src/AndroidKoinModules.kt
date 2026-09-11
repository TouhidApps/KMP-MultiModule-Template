package com.touhidapps.kmp

import com.touhidapps.kmp.core.common.Analytics
import com.touhidapps.kmp.core.common.NoOpAnalytics
import com.touhidapps.kmp.core.database.AppDatabase
import com.touhidapps.kmp.core.database.androidDatabaseBuilder
import com.touhidapps.kmp.core.database.buildDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings that need an Android `Context` or another platform-only API, so
 * they can't live in [appModule]. Combined with it in [MyApp.onCreate].
 */
val androidKoinModule: Module = module {
    single<AppDatabase> { buildDatabase(androidDatabaseBuilder(androidContext())) }

    // core/firebase has no real project to talk to out of the box, so
    // Analytics is a no-op until you wire your own. Once you have a
    // Firebase project:
    //   1. call androidInitializeFirebase(context, FirebaseConfig(...))
    //      once in MyApp.onCreate, with your project's real values
    //      (Firebase console > Project settings > General), and
    //   2. bind FirebaseAnalytics here instead of NoOpAnalytics.
    single<Analytics> { NoOpAnalytics }
}
