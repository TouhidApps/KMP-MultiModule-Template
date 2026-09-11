package com.touhidapps.notification.di

import com.touhidapps.notification.data.datasource.remote.api.NotificationApi
import com.touhidapps.notification.data.datasource.remote.api.NotificationApiImpl
import com.touhidapps.notification.data.repository.NotificationRepositoryImpl
import com.touhidapps.notification.domain.repository.NotificationRepository
import com.touhidapps.notification.domain.usecase.GetNotificationsUseCase
import com.touhidapps.notification.domain.usecase.MarkAllNotificationsReadUseCase
import com.touhidapps.notification.domain.usecase.MarkNotificationReadUseCase
import com.touhidapps.notification.presentation.NotificationContract
import com.touhidapps.notification.presentation.NotificationViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val notificationModule: Module = module {
    factory<NotificationApi> { NotificationApiImpl(get()) }
    // single: the list is shown from more than one place (More menu badge,
    // the list screen itself), and read-state should be shared between them.
    single<NotificationRepository> { NotificationRepositoryImpl(get()) }

    factory { GetNotificationsUseCase(get()) }
    factory { MarkNotificationReadUseCase(get()) }
    factory { MarkAllNotificationsReadUseCase(get()) }

    factory { NotificationViewModel(get(), get(), get()) }
    factory<NotificationContract> { get<NotificationViewModel>() }
}
