package com.touhidapps.notification.domain.usecase

import com.touhidapps.notification.domain.repository.NotificationRepository

class MarkNotificationReadUseCase(private val repository: NotificationRepository) {
    operator fun invoke(id: Int) = repository.markRead(id)
}
