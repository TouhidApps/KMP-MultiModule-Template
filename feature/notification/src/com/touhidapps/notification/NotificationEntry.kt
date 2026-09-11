package com.touhidapps.notification

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.notification.presentation.NotificationContract
import com.touhidapps.notification.presentation.NotificationScreen
import com.touhidapps.notification.presentation.NotificationViewModel
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.compose.viewmodel.koinViewModel

fun EntryProviderScope<NavKey>.notificationEntry() {
    entry<Route.Notification>(
        metadata = TwoPaneScene.twoPane()
    ) {
        val contract: NotificationContract = koinViewModel<NotificationViewModel>()
        NotificationScreen(contract)
    }
}
