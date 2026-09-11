package com.touhidapps.notification.data.datasource.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationListDto(
    @SerialName("notifications") val notifications: List<NotificationDto> = emptyList(),
)

@Serializable
data class NotificationDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
)
