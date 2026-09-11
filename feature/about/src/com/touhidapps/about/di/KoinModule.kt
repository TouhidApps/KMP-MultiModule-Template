package com.touhidapps.about.di

//import com.touhidapps.details.presentation.DetailsContract
//import com.touhidapps.details.presentation.DetailsViewModel
import com.touhidapps.about.presentation.AboutContract
import com.touhidapps.about.presentation.AboutViewModel
import org.koin.core.module.Module
import org.koin.dsl.module


val aboutModule: Module = module {

//    factory { FlowerGetUseCase(get()) }

//    factory { (flower: FlowerModel?) ->
//        DetailsViewModel(flower)
//    }
//    factory<AboutContract> { get<AboutViewModel>() }

    factory { AboutViewModel() }
    factory<AboutContract> { get<AboutViewModel>() } // while using no need koinViewModel<TransactionViewModel>() only use get()


}