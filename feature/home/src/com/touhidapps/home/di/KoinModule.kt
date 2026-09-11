package com.touhidapps.home.di

import com.touhidapps.home.presentation.HomeViewModel
import com.touhidapps.home.presentation.HomeContract
import org.koin.core.module.Module
import org.koin.dsl.module


val homeModule: Module = module {

    factory { HomeViewModel() }
    factory<HomeContract> { get<HomeViewModel>() } // while using no need koinViewModel<TransactionViewModel>() only use get()

}