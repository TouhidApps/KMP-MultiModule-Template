package com.touhidapps.forceupdate

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.forceupdate.presentation.ForceUpdateContract
import com.touhidapps.forceupdate.presentation.ForceUpdateScreen
import com.touhidapps.forceupdate.presentation.ForceUpdateViewModel
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.mp.KoinPlatform.getKoin


fun EntryProviderScope<NavKey>.forceUpdateEntry() {
    entry<Route.ForceUpdate>(
        metadata = TwoPaneScene.twoPane()
    ) { key ->

        val contract: ForceUpdateContract = getKoin().get<ForceUpdateViewModel>(
//            parameters = { parametersOf(key.data?.flowerRouteModel?.toDomainModel()) }
        )

        ForceUpdateScreen(contract)

    }

}
