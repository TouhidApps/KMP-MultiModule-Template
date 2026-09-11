package com.touhidapps.kmp

import com.touhidapps.kmp.core.common.Analytics
import com.touhidapps.kmp.core.common.NoOpAnalytics
import com.touhidapps.kmp.core.database.AppDatabase
import com.touhidapps.kmp.core.database.buildDatabase
import com.touhidapps.kmp.core.database.iosDatabaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings that need an iOS-only API, so they can't live in [appModule].
 * Combined with it in [ViewController].
 */
val iosKoinModule: Module = module {
    single<AppDatabase> { buildDatabase(iosDatabaseBuilder()) }

    // core/firebase is Android-only (see its module.yaml) — GitLive's iOS
    // binding needs native Firebase frameworks linked via CocoaPods/SPM,
    // which Amper can't wire — so iOS always gets the no-op.
    single<Analytics> { NoOpAnalytics }
}
