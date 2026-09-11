package com.touhidapps.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.SnackbarHost
//import androidx.compose.material3.SnackbarHostState
//import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.touhidapps.ui.LocalizationManager
import com.touhidapps.common.localization.Strings
import com.touhidapps.ui.theme.AppScaffold
import com.touhidapps.ui.theme.AppTheme
//import com.touhidapps.corecommon.localization.LocalizationManager
//import com.touhidapps.coreui.localization.LocalizationManager
//import com.touhidapps.coreui.localization.Strings
//import com.touhidapps.coreui.theme.AppScaffold
//import com.touhidapps.coreui.theme.AppTheme
//import com.touhidapps.domain.core.LanguageProvider
//import com.touhidapps.home.HomeScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic


val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Language::class, Route.Language.serializer())
            subclass(Route.Home::class, Route.Home.serializer())
            subclass(Route.FlowerDetails::class, Route.FlowerDetails.serializer())
            subclass(Route.About::class, Route.About.serializer())
            subclass(Route.AboutNext::class, Route.AboutNext.serializer())
            subclass(Route.More::class, Route.More.serializer())
            subclass(Route.Notification::class, Route.Notification.serializer())
            subclass(Route.Settings::class, Route.Settings.serializer())

//            subclass(Route.CARD::class, Route.CARD.serializer())
//            subclass(Route.MAP::class, Route.MAP.serializer())
//            subclass(Route.CHAT::class, Route.CHAT.serializer())
//            subclass(Route.FAQ::class, Route.FAQ.serializer())
//            subclass(Route.NOTICE::class, Route.NOTICE.serializer())
//            subclass(Route.NoticeDetails::class, Route.NoticeDetails.serializer())
//            subclass(Route.WEB_VIEW::class, Route.WEB_VIEW.serializer())

//            subclass(Route.History::class, Route.History.serializer())
//            subclass(Route.Register::class, Route.Register.serializer())
//            subclass(Route.Login::class, Route.Login.serializer())
//            subclass(Route.ForgotPassword::class, Route.ForgotPassword.serializer())
            subclass(Route.Back::class, Route.Back.serializer())
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<(Route) -> Unit> {
    error("Not provided")
}

val LocalStrings = staticCompositionLocalOf<Strings> {
    error("Strings not provided")
}

@Suppress("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MainNavigation(appEntryProvider: (NavKey) -> NavEntry<NavKey>) {

//    val language = remember {
//        languageProvider.getLanguage()
//    }
//
//    LaunchedEffect(language) {
//        LocalizationManager.setLanguage(language)
//    }
//    val strings = LocalizationManager.strings
//    LocalizationManager.setLanguage(AppSettings.getLanguage("lang"))

//    val strings = LocalizationManager.strings

    val backStack = rememberNavBackStack(config, Route.Home)

    var appTitle by remember { mutableStateOf("Flowers | HOME ") } // ${"strings.appName"} || ${"strings.appSlogan"}


    // Override the defaults so that there isn't a horizontal space between the panes.
    // See b/418201867
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp)
    }

    // First try 2 panel strategy if doesn't support use ListDetail Strategy if it also doesn't support automatically use SinglePaneSceneStrategy
    val strategy = rememberTwoPaneSceneStrategy<NavKey>() then rememberListDetailSceneStrategy<NavKey>(
            directive = directive
    ) then remember { DialogSceneStrategy<NavKey>() }

    val navAnimation = NavDisplay.transitionSpec {
        // Slide new content up, keeping the old content in place underneath
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(1000)
        ) togetherWith ExitTransition.KeepUntilTransitionsFinished
    } + NavDisplay.popTransitionSpec {
        // Slide old content down, revealing the new content in place underneath
        EnterTransition.None togetherWith slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(1000)
        )
    } + NavDisplay.predictivePopTransitionSpec {
        // Slide old content down, revealing the new content in place underneath
        EnterTransition.None togetherWith slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(1000)
        )
    }

//    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AppTheme {
        CompositionLocalProvider(
            LocalNavigator provides { route ->
                backStack.toRoute(route)
                titleChangeByRoute(backStack) {
                    appTitle = it
                }
            },
            LocalStrings provides LocalizationManager.strings
        ) {

            Surface(modifier = Modifier.fillMaxSize()) {
                AppScaffold(
                    appTitle = appTitle,
                    backStack = backStack,
//            drawerRoute = { r ->
//                backStack.toRoute(r)
//            },
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {

                        NavDisplay(
                            backStack = backStack,
                            sceneStrategies = listOf(strategy),
                            onBack = {
                                backStack.toRoute(Route.Back)
                                titleChangeByRoute(backStack) {
                                    appTitle = it
                                }
                            },
                            // In order to add the `ViewModelStoreNavEntryDecorator` (see comment below for why)
                            // we also need to add the default `NavEntryDecorator`s as well. These provide
                            // extra information to the entry's content to enable it to display correctly
                            // and save its state.
                            //                    entryDecorators = listOf( // when use viewmodel
                            //                        rememberSaveableStateHolderNavEntryDecorator(),
                            //                        rememberViewModelStoreNavEntryDecorator()
                            //                    ),
                            entryProvider = appEntryProvider,
                            transitionSpec = {
                                // Slide in from right when navigating forward
                                slideInHorizontally(initialOffsetX = { it }) togetherWith slideOutHorizontally(
                                    targetOffsetX = { -it })
                            },
                            popTransitionSpec = {
                                // Slide in from left when navigating back
                                slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(
                                    targetOffsetX = { it })
                            },
                            predictivePopTransitionSpec = {
                                // Slide in from left when navigating back
                                slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(
                                    targetOffsetX = { it })
                            }
                        )

                    }

                }
            }
        }
    }


//
//    AppTheme {
//        AppScaffold(
//            strings = strings,
//            appTitle = appTitle,
//            backStack = backStack,
//            drawerRoute = { r ->
//                backStack.toRoute(r)
//            }
//        ) { paddingValues ->
//            Box(modifier = Modifier.padding(paddingValues)) {

//    CompositionLocalProvider() {
//        Text
//    }


//
//                // Place snackbar at the TOP
//                SnackbarHost(
//                    hostState = snackBarHostState,
//                    modifier = Modifier.align(Alignment.TopCenter),   // 👈 top position,
//                    snackbar = { snackData ->
//
//                        // 🔥 Custom toast UI
//                        Box(
//                            contentAlignment = Alignment.Center,
//                            modifier = Modifier.fillMaxWidth().height(100.dp)
//                                .background(
//                                    color = Color(0xFF222222),   // dark background
//                                    shape = RoundedCornerShape(12.dp)
//                                )
//                                .padding(horizontal = 20.dp, vertical = 12.dp) // custom padding
//                        ) {
//                            Text(
//                                text = snackData.visuals.message,
//                                color = Color.White,
//                                style = MaterialTheme.typography.bodyLarge
//                            )
//                        }
//
//                    }
//                )
//
//            }
//        }
//    }


}



/**
 * Track each navigation event and add/remove it to the back stack
 */
fun NavBackStack<NavKey>.toRoute(route: Route) {
    println("Navigate to: ${route}")
    val backStack = this
    when (route) {
        is Route.Back -> {
            if (backStack.isNotEmpty()) {
                backStack.removeLastOrNull()
            }
        }

        is Route.Home -> {
            if (backStack.isNotEmpty()) {
                backStack.removeAll { it != Route.Home }
            }
        }

        else -> {
            if (!backStack.contains(route)) {
                backStack.add(route)
            }
        }
    }
}

/**
 * Set title based on route
 */
fun titleChangeByRoute(backStack: NavBackStack<NavKey>, title: ((String) -> Unit)) {
    when(val route = backStack.last()) {
        is Route.Home -> {
            println("Home")
            title("Flowers | HOME") // App title change
        }

        is Route.FlowerList -> {
            println("Flower List")
            title("Flower List")
        }

        is Route.FlowerDetails -> {
            println("Flower Details")
            title("Flower Details")
        }

        is Route.Language -> {
            println("Language")
            title("Language")
        }

        is Route.About -> {
            println("About")
            title("About Us")
        }

        is Route.AboutNext -> {
            println("AboutNext")
            title("${route.data?.title}") // This title set when navigating from About to AboutNext
        }

        else -> {}
    }
}

fun NavBackStack<NavKey>.isItHome(): Boolean {
    val backStack = this
    return backStack.size == 1 && backStack.first() == Route.Home
}





