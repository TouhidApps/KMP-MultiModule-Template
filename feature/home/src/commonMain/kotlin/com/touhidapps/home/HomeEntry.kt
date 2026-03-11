package com.touhidapps.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.home.presentation.HomeViewModel
import com.touhidapps.home.presentation.HomeContract
import com.touhidapps.home.presentation.HomeScreen
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.compose.viewmodel.koinViewModel


fun EntryProviderScope<NavKey>.homeEntry() {
    entry<Route.Home>(
        metadata = TwoPaneScene.twoPane()
    ) { key ->

        val homeContract: HomeContract = koinViewModel<HomeViewModel>()

        HomeScreen(homeContract)

    }
}
