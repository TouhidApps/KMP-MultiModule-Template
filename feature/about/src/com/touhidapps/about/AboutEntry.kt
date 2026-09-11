package com.touhidapps.about

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.about.presentation.AboutContract
import com.touhidapps.about.presentation.AboutScreen
import com.touhidapps.about.presentation.AboutScreenContent
import com.touhidapps.about.presentation.AboutViewModel
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform.getKoin


fun EntryProviderScope<NavKey>.aboutEntry() {

    entry<Route.About>(
        metadata = TwoPaneScene.twoPane()
    ) { key ->

        val contract: AboutContract = getKoin().get<AboutViewModel>(
//            parameters = { parametersOf(key.data?.flowerRouteModel?.toDomainModel()) }
        )

        AboutScreen(contract)

    }

    entry<Route.AboutNext>(
        metadata = TwoPaneScene.twoPane()
    ) { key ->

//        val contract: AboutContract = getKoin().get<AboutViewModel>(
//            parameters = { parametersOf(key.data?.flowerRouteModel?.toDomainModel()) }
//        )

        AboutScreenContent(key.data?.aboutType ?: "")
//        AboutScreenContent(contract)

    }

}
