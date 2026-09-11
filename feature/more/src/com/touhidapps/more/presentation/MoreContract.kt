package com.touhidapps.more.presentation

import com.touhidapps.common.localization.Strings
import com.touhidapps.ui.nav.Route
import kotlinx.coroutines.flow.StateFlow

/** One row in the More menu. [route] is where tapping it navigates to. */
data class MoreMenuItem(
    val title: (Strings) -> String,
    val route: Route,
)

data class MoreState(
    val items: List<MoreMenuItem> = listOf(
        MoreMenuItem({ it.notifications }, Route.Notification),
        MoreMenuItem({ it.language }, Route.Language),
        MoreMenuItem({ it.titleAboutUs }, Route.About),
        MoreMenuItem({ it.settings }, Route.Settings),
    ),
)

interface MoreContract {
    val state: StateFlow<MoreState>
}
