package com.touhidapps.mmca.di

import com.touhidapps.about.di.aboutModule
import com.touhidapps.flowers.di.flowersModule
import com.touhidapps.forceupdate.di.forceUpdateModule
import com.touhidapps.home.di.homeModule
import com.touhidapps.language.di.languageModule
import com.touhidapps.network.createHttpClient
import io.ktor.client.HttpClient
import org.koin.core.context.loadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {

    single<HttpClient> { createHttpClient() }

    // TODO - add DI modules here for new modules
    loadKoinModules(listOf(
        homeModule,
        flowersModule,
        languageModule,
        aboutModule,
        forceUpdateModule
    ))

    // Provide MyTransactionDao
//    single { get<AppDatabase>().transDao() }

//    single { NoticeRemoteSource(get()) }
//    single<FlowerApi> { NoticeRemoteSource(get()) }

//    single { FlowerRepositoryImpl(get()) }


//    single<FlowerRepository> { FlowerRepositoryImpl(get()) }

//    factory { AppSettings }
//    factory<LanguageRepository> { LanguageRepositoryImpl(get()) }

//    factory { FlowerGetUseCase(get()) }
//    factory { TransactionUpsertUseCase(get()) }
//    factory { TransactionDeleteUseCase(get()) }
//    factory { TransactionSummaryUseCase(get()) }

//    viewModel { FlowerViewModel(get()) }
//    single<HomeContract> { get<FlowerViewModel>() } // app-wide singleton

//    viewModel { TransactionViewModel(get(), get(), get()) }
//
//    factory { FlowerViewModel(get()) }
//    factory<HomeContract> { get<FlowerViewModel>() } // while using no need koinViewModel<TransactionViewModel>() only use get()

//    viewModel { TransactionViewModel(get(), get(), get(), get()) } bind HomeContract::class
//    viewModel<FlowerViewModel> { FlowerViewModel(get()) } bind HomeContract::class




}