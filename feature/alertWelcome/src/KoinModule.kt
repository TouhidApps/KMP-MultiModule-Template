package com.touhidapps.kmp.feature.alertwelcome

import org.koin.core.module.Module
import org.koin.dsl.module

val alertWelcomeModule: Module = module {
    factory { WelcomeAlertViewModel(get()) }
}
