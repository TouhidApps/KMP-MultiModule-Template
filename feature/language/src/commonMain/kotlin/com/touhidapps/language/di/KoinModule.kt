package com.touhidapps.language.di

import com.touhidapps.language.data.repository.LanguageRepositoryImpl
import com.touhidapps.language.domain.repository.LanguageRepository
import com.touhidapps.language.domain.usecase.GetLanguageUseCase
import com.touhidapps.language.domain.usecase.SetLanguageUseCase
import com.touhidapps.language.presentation.LanguageContract
import com.touhidapps.language.presentation.LanguageViewModel
import com.touhidapps.preferences.AppSettings
import org.koin.core.module.Module
import org.koin.dsl.module


val languageModule: Module = module {

    factory { AppSettings }
    factory<LanguageRepository> { LanguageRepositoryImpl(get()) }
    factory { GetLanguageUseCase(get()) }
    factory { SetLanguageUseCase(get()) }

    factory { LanguageViewModel(get(), get()) }
    factory<LanguageContract> { get<LanguageViewModel>() } // while using no need koinViewModel<TransactionViewModel>() only use get()

}


