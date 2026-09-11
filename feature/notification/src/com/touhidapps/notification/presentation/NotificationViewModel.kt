package com.touhidapps.notification.presentation

import androidx.lifecycle.viewModelScope
import com.touhidapps.common.base.BaseViewModel
import com.touhidapps.network.onError
import com.touhidapps.network.onSuccess
import com.touhidapps.notification.domain.usecase.GetNotificationsUseCase
import com.touhidapps.notification.domain.usecase.MarkAllNotificationsReadUseCase
import com.touhidapps.notification.domain.usecase.MarkNotificationReadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val getNotificationsUseCase: GetNotificationsUseCase,
    private val markNotificationReadUseCase: MarkNotificationReadUseCase,
    private val markAllNotificationsReadUseCase: MarkAllNotificationsReadUseCase,
) : BaseViewModel(), NotificationContract {

    private val _state = MutableStateFlow(NotificationState())
    override val state: StateFlow<NotificationState> = _state.asStateFlow()

    init {
        onEvent(NotificationEvents.Load)
    }

    override fun onEvent(event: NotificationEvents) {
        when (event) {
            is NotificationEvents.Load -> load()
            is NotificationEvents.MarkRead -> {
                markNotificationReadUseCase(event.id)
                _state.update { st ->
                    st.copy(notifications = st.notifications.map {
                        if (it.id == event.id) it.copy(isRead = true) else it
                    })
                }
            }
            is NotificationEvents.MarkAllRead -> {
                markAllNotificationsReadUseCase()
                _state.update { st -> st.copy(notifications = st.notifications.map { it.copy(isRead = true) }) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            getNotificationsUseCase()
                .onSuccess { data ->
                    _state.update { it.copy(isLoading = false, notifications = data.notifications) }
                }
                .onError { err ->
                    _state.update { it.copy(isLoading = false, errorMessage = err.errMessage) }
                }
        }
    }
}
