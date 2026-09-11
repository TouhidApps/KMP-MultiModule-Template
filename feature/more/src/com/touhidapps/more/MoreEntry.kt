package com.touhidapps.more

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.more.presentation.MoreContract
import com.touhidapps.more.presentation.MoreScreen
import com.touhidapps.more.presentation.MoreViewModel
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.compose.viewmodel.koinViewModel

fun EntryProviderScope<NavKey>.moreEntry() {
    entry<Route.More>(
        metadata = TwoPaneScene.twoPane()
    ) {
        val contract: MoreContract = koinViewModel<MoreViewModel>()
        MoreScreen(contract)
    }
}
