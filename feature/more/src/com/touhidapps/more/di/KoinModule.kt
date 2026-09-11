package com.touhidapps.more.di

import com.touhidapps.more.presentation.MoreContract
import com.touhidapps.more.presentation.MoreViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val moreModule: Module = module {
    factory { MoreViewModel() }
    factory<MoreContract> { get<MoreViewModel>() }
}
