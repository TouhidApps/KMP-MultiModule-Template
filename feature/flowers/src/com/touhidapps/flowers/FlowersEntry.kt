package com.touhidapps.flowers

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.flowers.presentation.flowerDetails.DetailsContract
import com.touhidapps.flowers.presentation.flowerDetails.DetailsScreen
import com.touhidapps.flowers.presentation.flowerDetails.DetailsViewModel
import com.touhidapps.flowers.presentation.flowerList.FlowerListContract
import com.touhidapps.flowers.presentation.flowerList.FlowerListScreen
import com.touhidapps.flowers.presentation.flowerList.FlowerListViewModel
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform.getKoin


fun EntryProviderScope<NavKey>.flowersEntry() {

    entry<Route.FlowerList>(
        metadata = TwoPaneScene.twoPane()
    ) { key ->

        val contract: FlowerListContract = getKoin().get<FlowerListViewModel>(
//            parameters = { parametersOf(key.data?.flowerRouteModel?.toDomainModel()) }
        )

        FlowerListScreen(contract)

    }

    entry<Route.FlowerDetails>(
        metadata = TwoPaneScene.twoPane()
    ) { key ->

        val contract: DetailsContract = getKoin().get<DetailsViewModel>(
            parameters = { parametersOf(key.data?.flowerId) }
        )

        DetailsScreen(contract)

    }

}
