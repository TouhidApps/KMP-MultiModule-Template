package com.touhidapps.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.touhidapps.settings.presentation.SettingsContract
import com.touhidapps.settings.presentation.SettingsScreen
import com.touhidapps.settings.presentation.SettingsViewModel
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.TwoPaneScene
import org.koin.compose.viewmodel.koinViewModel

fun EntryProviderScope<NavKey>.settingsEntry() {
    entry<Route.Settings>(
        metadata = TwoPaneScene.twoPane()
    ) {
        val contract: SettingsContract = koinViewModel<SettingsViewModel>()
        SettingsScreen(contract)
    }
}
