package com.touhidapps.forceupdate.di

import com.touhidapps.forceupdate.presentation.ForceUpdateContract
import com.touhidapps.forceupdate.presentation.ForceUpdateViewModel
import org.koin.core.module.Module
import org.koin.dsl.module


val forceUpdateModule: Module = module {

//    factory { (flower: FlowerModel?) ->
//        ForceUpdateViewModel(flower)
//    }
    factory<ForceUpdateContract> { get<ForceUpdateViewModel>() }

//    factory { ForceUpdateViewModel() }
//    factory<ForceUpdateContract> { get<ForceUpdateViewModel>() } // while using no need koinViewModel<TransactionViewModel>() only use get()


}