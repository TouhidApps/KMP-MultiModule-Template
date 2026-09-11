package com.touhidapps.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.nav.isItHome
import com.touhidapps.ui.rememberScreenEnv
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
//    strings: Strings,
    appTitle: String? = null,
    backStack: NavBackStack<NavKey>,
//    drawerRoute: ((Route) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {

    val env = rememberScreenEnv()


//    val navigate = LocalNavigator.current
//    val strings = LocalizationManager.strings

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerContent = {
                NavigationDrawerContent(
                    modifier = Modifier.fillMaxWidth(0.75f)
                ) { mRoute ->

                    scope.launch {
                        drawerState.close()
                    }

                    env.navigate(mRoute)

//                if (tempCurrentMenu != mRoute) {
//                    tempCurrentMenu = mRoute
//
//                    navController.navigate(
//                        mRoute,
//                        //  navOptions { popUpTo(0) } // Go home and remove all backstack
//                    )
//                }

                }
            },
            scrimColor = Color.Black.copy(alpha = 0.30f),
            drawerState = drawerState
        ) {

            Scaffold(
                modifier = Modifier.background(color = Color.Black),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent // bg gradient applied from modifier
                        ),
                        modifier = Modifier.background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Gray, Color.DarkGray
                                )
                            )
                        ),
                        title = {
                            Text(
                                text = appTitle ?: "",
                                style = TextStyle(
                                    color = Color.White,
                                    fontFamily = myFontFamily(),
                                    fontSize = 15.sp,
                                    //     fontFamily = Font
                                    //     fontFamily = org.jetbrains.compose.resources.Font(
                                    //         Res.font.solaiman_lipi,
                                    //         weight = FontWeight.Bold
                                    //     ),
                                    //     spanStyle = SpanStyle(),
                                    //     paragraphStyle = ParagraphStyle()
                                )
                            )
                        },
                        navigationIcon = {

                            if (backStack.isItHome()) {
                                IconButton(onClick = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.Menu,
                                        contentDescription = "Open drawer menu",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        env.navigate(Route.Back)
                                    }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back menu",
                                        tint = Color.White
                                    )
                                }
                            }

                        }
                    )
                },
                content = { content(it) }
            )

        }
    }

}