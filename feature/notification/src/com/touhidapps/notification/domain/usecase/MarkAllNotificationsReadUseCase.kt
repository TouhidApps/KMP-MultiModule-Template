package com.touhidapps.notification.domain.usecase

import com.touhidapps.notification.domain.repository.NotificationRepository

class MarkAllNotificationsReadUseCase(private val repository: NotificationRepository) {
    operator fun invoke() = repository.markAllRead()
}
