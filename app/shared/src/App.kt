package com.touhidapps.kmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import com.touhidapps.kmp.feature.alertmaintenance.MaintenanceAlert
import com.touhidapps.kmp.feature.alertmaintenance.MaintenanceViewModel
import com.touhidapps.kmp.feature.alertwelcome.WelcomeAlert
import com.touhidapps.kmp.feature.alertwelcome.WelcomeAlertViewModel
import com.touhidapps.language.domain.usecase.GetLanguageUseCase
import com.touhidapps.ui.LocalizationManager
import com.touhidapps.ui.nav.MainNavigation
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.koin.compose.viewmodel.koinViewModel
import org.koin.mp.KoinPlatform.getKoin

@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    /**
     * Load previously saved language from settings
     */
    val getLanguageUseCase: GetLanguageUseCase = getKoin().get()
    LaunchedEffect(Unit) {
        val language = getLanguageUseCase()
        LocalizationManager.updateLanguage(language)
    }

    /**
     * Gate the whole app behind the maintenance check, then start
     * Navigation. The one-shot welcome dialog sits alongside the nav
     * content, so it never appears on top of a blocking maintenance notice.
     */
    MaintenanceAlert(viewModel = koinViewModel<MaintenanceViewModel>()) {
        MainNavigation(appEntryProvider())
        WelcomeAlert(viewModel = koinViewModel<WelcomeAlertViewModel>())
    }
}
