package com.touhidapps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.touhidapps.common.localization.Strings
import com.touhidapps.ui.nav.LocalNavigator
import com.touhidapps.ui.nav.Route


/**
 * How to use UiHelper:
 * @Composable
 * fun LanguageScreen(
 *     contract: LanguageContract,
 *     onLanguageSelected: (Language) -> Unit,
 * ) {
 *     val env = rememberScreenEnv()
 *
 *     Text(env.strings.languageTitle)
 *
 *     Button(onClick = {
 *         env.navigate(Route.Home)
 *     }) {
 *         Text(env.strings.confirm)
 *     }
 * }
 *
 */
@Immutable
data class ScreenEnv(
    val navigate: (Route) -> Unit,
    val strings: Strings
)

@Composable
fun rememberScreenEnv(): ScreenEnv {
    return ScreenEnv(
        navigate = LocalNavigator.current,
        strings = LocalizationManager.strings
    )
}
