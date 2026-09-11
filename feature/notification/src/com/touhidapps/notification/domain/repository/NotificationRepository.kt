package com.touhidapps.notification.domain.repository

import com.touhidapps.network.NetworkError
import com.touhidapps.network.ResultNet
import com.touhidapps.notification.domain.model.NotificationListData

interface NotificationRepository {
    suspend fun getNotifications(): ResultNet<NotificationListData, NetworkError>

    /** Marks read locally (in-memory) so re-opening the list keeps state within the session. */
    fun markRead(id: Int)

    fun markAllRead()
}
