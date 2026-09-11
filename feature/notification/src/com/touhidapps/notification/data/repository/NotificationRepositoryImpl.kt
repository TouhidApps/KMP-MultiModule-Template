package com.touhidapps.notification.data.repository

import com.touhidapps.network.NetworkError
import com.touhidapps.network.ResultNet
import com.touhidapps.network.safeApiCall
import com.touhidapps.notification.data.datasource.remote.api.NotificationApi
import com.touhidapps.notification.data.mapper.toDomain
import com.touhidapps.notification.domain.model.NotificationListData
import com.touhidapps.notification.domain.repository.NotificationRepository

class NotificationRepositoryImpl(
    private val api: NotificationApi,
) : NotificationRepository {

    // Read state is local-only (no backend endpoint for it in this template) —
    // a real app would persist this via core/preferences or core/database
    // and/or push it back to the backend.
    private val readIds = mutableSetOf<Int>()
    private var lastSeenIds: List<Int> = emptyList()

    override suspend fun getNotifications(): ResultNet<NotificationListData, NetworkError> =
        safeApiCall {
            val data = api.getNotifications().toDomain()
            lastSeenIds = data.notifications.map { it.id }
            data.copy(notifications = data.notifications.map { it.copy(isRead = it.id in readIds) })
        }

    override fun markRead(id: Int) {
        readIds.add(id)
    }

    override fun markAllRead() {
        readIds.addAll(lastSeenIds)
    }
}
