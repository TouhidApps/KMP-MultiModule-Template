package com.touhidapps.mmca

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.touhidapps.language.domain.usecase.GetLanguageUseCase
//import com.touhidapps.language.domain.usecase.GetLanguageUseCase
import com.touhidapps.ui.LocalizationManager
import com.touhidapps.ui.nav.MainNavigation
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.mp.KoinPlatform.getKoin


@Composable
@Preview
fun App() {
    MaterialTheme {

        /**
         * Load previously saved language from settings
         */
        val initLanguageUseCase: GetLanguageUseCase = getKoin().get()
        LaunchedEffect(Unit) {
            val language = initLanguageUseCase()
            LocalizationManager.updateLanguage(language)
        }

        /**
         * Start main UI from Navigation
         */
        MainNavigation(appEntryProvider())

    }
}


