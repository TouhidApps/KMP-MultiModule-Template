package com.touhidapps.kmp

import com.touhidapps.about.di.aboutModule
import com.touhidapps.flowers.di.flowersModule
import com.touhidapps.forceupdate.di.forceUpdateModule
import com.touhidapps.home.di.homeModule
import com.touhidapps.kmp.core.preferences.AppPreferences
import com.touhidapps.kmp.core.preferences.AppPreferencesImpl
import com.touhidapps.kmp.feature.alertmaintenance.alertMaintenanceModule
import com.touhidapps.kmp.feature.alertwelcome.alertWelcomeModule
import com.touhidapps.language.di.languageModule
import com.touhidapps.more.di.moreModule
import com.touhidapps.network.createHttpClient
import com.touhidapps.notification.di.notificationModule
import com.touhidapps.settings.di.settingsModule
import io.ktor.client.HttpClient
import org.koin.core.context.loadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bindings shared by every platform: repositories, use cases, and anything
 * built only from `core`/`feature` interfaces. Platform-specific bindings
 * (a Room database, a Context-backed DataStore, ...) do not belong here —
 * they go in each app's own Koin module instead (see `androidKoinModule`),
 * and are combined with this one when Koin starts.
 */
val appModule: Module = module {

    single<HttpClient> { createHttpClient() }
    single<AppPreferences> { AppPreferencesImpl() }

    loadKoinModules(
        listOf(
            homeModule,
            flowersModule,
            languageModule,
            aboutModule,
            forceUpdateModule,
            moreModule,
            notificationModule,
            settingsModule,
            alertWelcomeModule,
            alertMaintenanceModule,
        )
    )

}
