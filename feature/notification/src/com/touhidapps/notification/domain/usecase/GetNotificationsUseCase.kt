package com.touhidapps.notification.domain.usecase

import com.touhidapps.network.NetworkError
import com.touhidapps.network.ResultNet
import com.touhidapps.notification.domain.model.NotificationListData
import com.touhidapps.notification.domain.repository.NotificationRepository

class GetNotificationsUseCase(private val repository: NotificationRepository) {
    suspend operator fun invoke(): ResultNet<NotificationListData, NetworkError> = repository.getNotifications()
}
