package com.touhidapps.settings.di

import com.touhidapps.settings.presentation.SettingsContract
import com.touhidapps.settings.presentation.SettingsViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val settingsModule: Module = module {
    factory { SettingsViewModel(get()) }
    factory<SettingsContract> { get<SettingsViewModel>() }
}
