package com.touhidapps.notification.domain.model

data class NotificationListData(
    val notifications: List<NotificationData> = emptyList(),
)

data class NotificationData(
    val id: Int,
    val title: String,
    val body: String,
    val createdAt: String,
    val isRead: Boolean = false,
)
