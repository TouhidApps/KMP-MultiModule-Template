package com.touhidapps.kmp

import com.touhidapps.kmp.core.common.Analytics
import com.touhidapps.kmp.core.common.NoOpAnalytics
import com.touhidapps.kmp.core.database.AppDatabase
import com.touhidapps.kmp.core.database.buildDatabase
import com.touhidapps.kmp.core.database.jvmDatabaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings that need a desktop (JVM)-only API, so they can't live in
 * [appModule]. Combined with it in `main()`.
 */
val desktopKoinModule: Module = module {
    single<AppDatabase> { buildDatabase(jvmDatabaseBuilder()) }

    // core/firebase doesn't target jvm (see its module.yaml), so desktop
    // always gets the no-op — there's no "real" alternative to swap in here.
    single<Analytics> { NoOpAnalytics }
}
