package com.touhidapps.language

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.language.presentation.LanguageContract
import com.touhidapps.language.presentation.LanguageScreen
import com.touhidapps.language.presentation.LanguageViewModel
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.mp.KoinPlatform.getKoin


fun EntryProviderScope<NavKey>.languageEntry() {
    entry<Route.Language>(
        metadata = TwoPaneScene.twoPane()
    ) { key ->

//        val contract: DetailsContract = koinViewModel<DetailsViewModel>(
//            parameters = { parametersOf(key.data?.flowerRouteModel?.toDomainModel()) } // To pass nav data to next screen add data while creating view model, to avoid recomposition
//        )

        val contract: LanguageContract = getKoin().get<LanguageViewModel>(
//            parameters = { parametersOf(key.data?.flowerRouteModel?.toDomainModel()) }
        )


        LanguageScreen(contract = contract)

    }

}
