package com.touhidapps.flowers.di

import com.touhidapps.flowers.data.datasource.remote.api.FlowerListApi
import com.touhidapps.flowers.data.datasource.remote.source.FlowerListSource
import com.touhidapps.flowers.data.repository.FlowerListRepositoryImpl
import com.touhidapps.flowers.presentation.flowerDetails.DetailsContract
import com.touhidapps.flowers.presentation.flowerDetails.DetailsViewModel
import com.touhidapps.flowers.domain.repository.FlowerListRepository
import com.touhidapps.flowers.domain.usecase.FlowerListUseCase
import com.touhidapps.flowers.domain.usecase.GetFlowerByIdUseCase
import com.touhidapps.flowers.presentation.flowerList.FlowerListContract
import com.touhidapps.flowers.presentation.flowerList.FlowerListViewModel
import org.koin.core.module.Module
import org.koin.dsl.module


val flowersModule: Module = module {

    factory<FlowerListApi> {
        FlowerListSource(
            get()
        )
    }
    single<FlowerListRepository> {
        FlowerListRepositoryImpl(
            get()
        )
    } // we need repository in 2 pages so use single to share data
    factory { FlowerListUseCase(get()) }

//    factory { (flower: FlowerModel?) ->
//        DetailsViewModel(flower)
//    }
//    factory<DetailsContract> { get<DetailsViewModel>() }

//    factory { DetailsViewModel() }
//    factory<DetailsContract> { get<DetailsViewModel>() } // while using no need koinViewModel<TransactionViewModel>() only use get()

    factory { FlowerListViewModel(get()) }
    factory<FlowerListContract> { get<FlowerListViewModel>() }

    // Detail
    factory { GetFlowerByIdUseCase(get()) }
    factory { DetailsViewModel(flowerId = get(), getFlowerByIdUseCase = get()) }
    factory< DetailsContract> { get<DetailsViewModel>() }

}