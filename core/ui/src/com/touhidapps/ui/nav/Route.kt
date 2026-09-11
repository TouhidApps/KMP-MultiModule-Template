package com.touhidapps.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Route: NavKey {

    // TODO - add your route here for your every screen

    @Serializable
    data object Language: Route()

    @Serializable
    data object Home: Route()

    @Serializable
    data object FlowerList: Route()

    @Serializable
    data class FlowerDetails(val data: Data? = null): Route() {
        @Serializable
        data class Data(
            val flowerId: Int? = null
        )
    }

    @Serializable
    data object About: Route()

    @Serializable
    data class AboutNext(val data: Data? = null): Route() {
        @Serializable
        data class Data(
            val title: String? = null,
            val aboutType: String? = null
        )
    }

    @Serializable
    data object AlertMaintenance: Route()

    @Serializable
    data object AlertWelcome: Route()

    @Serializable
    data object ForceUpdate: Route()




    @Serializable
    data object Faq: Route()

    @Serializable
    data object More: Route()

    @Serializable
    data object Settings: Route()



    @Serializable
    data object Notification: Route()

    @Serializable
    data class WebView(val fromWhere: String): Route()

    @Serializable
    data object Back: Route()

}

