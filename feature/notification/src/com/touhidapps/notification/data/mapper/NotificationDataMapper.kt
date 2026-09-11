package com.touhidapps.notification.data.mapper

import com.touhidapps.notification.data.datasource.remote.dto.NotificationDto
import com.touhidapps.notification.data.datasource.remote.dto.NotificationListDto
import com.touhidapps.notification.domain.model.NotificationData
import com.touhidapps.notification.domain.model.NotificationListData

fun NotificationListDto.toDomain(): NotificationListData =
    NotificationListData(notifications = notifications.mapNotNull { it.toDomainOrNull() })

private fun NotificationDto.toDomainOrNull(): NotificationData? {
    val id = id ?: return null
    return NotificationData(
        id = id,
        title = title.orEmpty(),
        body = body.orEmpty(),
        createdAt = createdAt.orEmpty(),
    )
}
