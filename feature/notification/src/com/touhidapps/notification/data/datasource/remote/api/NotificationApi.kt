package com.touhidapps.notification.data.datasource.remote.api

import com.touhidapps.notification.data.datasource.remote.dto.NotificationListDto

interface NotificationApi {
    suspend fun getNotifications(): NotificationListDto
}
