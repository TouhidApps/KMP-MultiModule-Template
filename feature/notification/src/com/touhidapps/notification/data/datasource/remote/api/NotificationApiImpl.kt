package com.touhidapps.notification.data.datasource.remote.api

import com.touhidapps.notification.data.datasource.remote.dto.NotificationListDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodedPath

private object ApiUrls {
    // TODO - point this at your real backend endpoint
    const val NOTIFICATIONS = "/api/demo/notifications.php"
}

class NotificationApiImpl(private val client: HttpClient) : NotificationApi {
    override suspend fun getNotifications(): NotificationListDto {
        return client.get {
            url { encodedPath = ApiUrls.NOTIFICATIONS }
        }.body()
    }
}
