package com.touhidapps.kmp.feature.alertmaintenance

import org.koin.core.module.Module
import org.koin.dsl.module

val alertMaintenanceModule: Module = module {
    factory<MaintenanceService> { RemoteMaintenanceService(get()) }
    factory { MaintenanceViewModel(get()) }
}
