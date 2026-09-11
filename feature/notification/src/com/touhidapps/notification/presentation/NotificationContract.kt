package com.touhidapps.notification.presentation

import com.touhidapps.notification.domain.model.NotificationData
import kotlinx.coroutines.flow.StateFlow

data class NotificationState(
    val isLoading: Boolean = false,
    val notifications: List<NotificationData> = emptyList(),
    val errorMessage: String? = null,
)

sealed interface NotificationEvents {
    data object Load : NotificationEvents
    data class MarkRead(val id: Int) : NotificationEvents
    data object MarkAllRead : NotificationEvents
}

interface NotificationContract {
    val state: StateFlow<NotificationState>
    fun onEvent(event: NotificationEvents)
}
