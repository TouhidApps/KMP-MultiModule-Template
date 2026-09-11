package com.touhidapps.kmp

import com.touhidapps.kmp.core.common.Analytics
import com.touhidapps.kmp.core.common.NoOpAnalytics
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings that need a web (wasmJs)-only API, so they can't live in
 * [appModule]. Combined with it in `main()`.
 */
val webKoinModule: Module = module {
    // core/database and core/firebase don't target wasmJs (see their
    // module.yaml files), so web always gets the no-op — there's no "real"
    // alternative to swap in here for either.
    single<Analytics> { NoOpAnalytics }
}
