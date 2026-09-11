package com.touhidapps.about.presentation


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touhidapps.about.AboutRouteParams
import com.touhidapps.about.presentation.AboutActions
import com.touhidapps.about.presentation.AboutContract
import com.touhidapps.common.constants.Const.AppName
import com.touhidapps.common.constants.Const.AppSlogan
import com.touhidapps.common.constants.Const.Copyright
import com.touhidapps.common.constants.Const.Disclaimer
import com.touhidapps.common.constants.Const.PrivacyPolicyUrl
import com.touhidapps.ui.customUI.MyAlertDialog
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.rememberScreenEnv
import com.touhidapps.ui.theme.Resources.Drawable.AppLogo
import com.touhidapps.ui.theme.Resources.Drawable.Bg
import com.touhidapps.ui.theme.RoundCorner10
import com.touhidapps.ui.theme.myFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun AboutScreen(
    //    commonContract: CommonContract = koinViewModel<CommonViewModel>(),
    contract: AboutContract
) {


    val env = rememberScreenEnv()


    val state by contract.state.collectAsStateWithLifecycle() // collectAsState() for reactive recomposition.

    // The dialog is purely a UI concern.
    // It doesn’t belong to your domain/business logic — the user can cancel anytime without affecting data.
    // so no need to keep it in state
//    var showDeleteDialog by remember { mutableStateOf<Transaction?>(null) }

//    var showDatePicker by remember { mutableStateOf(false) }

//    val todayEndMillis by remember { mutableStateOf(endOfTodayMillis()) }

//    val datePickerState = rememberDatePickerState(
//        initialSelectedDateMillis = todayEndMillis, // or todayStart depending on UX
//        selectableDates = object : SelectableDates {
//            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
//                // Allow today and past days only
//                return utcTimeMillis <= todayEndMillis
//            }
//        }
//    )

    LaunchedEffect(Unit) {

        // Set default date once
//        homeContract.onEvent(HomeEvents.ChangeDate(endOfTodayMillis()))

        // Collect upsert
        launch {
//            homeContract.state
//                .distinctUntilChangedBy { it.selectedDate } // to avoid multiple calls when isLoading is false multiple times.
//                .map { it.selectedDate }
//                .collect { selectedDate ->
//
//                    datePickerState.selectedDateMillis = selectedDate
//                    datePickerState.displayMode = DisplayMode.Picker // to show current selected date page
//
//                }
        }

        launch {
            contract.actions.collect { action ->
                when (action) {
                    is AboutActions.ShowSnackbar -> {



                        // forward to common VM
//                        commonContract.onEvent(CommonEvents.ShowSnackbar(action.message))
                    }
                }
            }
        }

    }

    val uriHandler = LocalUriHandler.current
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // BG image
        Image(
            painter = painterResource(resource = Bg),
            contentDescription = null, // background should be null
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5F)
        )

        // Example content for the first tab
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.5f))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier = Modifier.height(35.dp)
            )

            Column (
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (showDialog) {
//                    uriHandler.openUri(PrivacyPolicyUrl)
                    MyAlertDialog(
                        title = env.strings.alert,
                        desc = env.strings.thisWillOpenBrowserLink,
                        okText = env.strings.okContinue,
                        onDismiss = {
                            showDialog = false
                        },
                        onCancel = {
                            showDialog = false
                        },
                        onOk = {
                            showDialog = false
                            uriHandler.openUri(PrivacyPolicyUrl)
                        }
                    )
                } // if showDialog

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(50.dp)
                        .clip(shape = RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7F))
                        .padding(10.dp)
                        .clickable {

                              showDialog = true

//                            uriHandler.openUri(PrivacyPolicyUrl) // If no alert need

                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Filled.Info,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                        contentDescription = "Privacy Policy Button"
                    )
                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )
                    Text(
                        text = env.strings.privacyPolicy, style = TextStyle(
                            color = Color.White,
                            fontFamily = myFontFamily()
                        )
                    )

                }

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(50.dp)
                        .clip(shape = RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7F))
                        .padding(10.dp)
                        .clickable {

                            val data = Route.AboutNext.Data(title = env.strings.thirdPartyLicense, aboutType = AboutRouteParams.LICENSE)

                            env.navigate(Route.AboutNext(data))

                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Filled.Info,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                        contentDescription = "Licenses"
                    )
                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )
                    Text(
                        text = env.strings.thirdPartyLicense, style = TextStyle(
                            color = Color.White,
                            fontFamily = myFontFamily()
                        )
                    )

                }

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(50.dp)
                        .clip(shape = RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7F))
                        .padding(10.dp)
                        .clickable {

                            val data = Route.AboutNext.Data(title = env.strings.prominentDisclosure, aboutType = AboutRouteParams.DISCLOSURE)
                            env.navigate(Route.AboutNext(data))

                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Filled.Info,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                        contentDescription = "Prominent Disclosure"
                    )
                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )
                    Text(
                        text = env.strings.prominentDisclosure, style = TextStyle(
                            color = Color.White,
                            fontFamily = myFontFamily()
                        )
                    )

                }
                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(50.dp)
                        .clip(shape = RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7F))
                        .padding(10.dp)
                        .clickable {

                            val data = Route.AboutNext.Data(title = env.strings.engineeringTools, aboutType = AboutRouteParams.TOOLS)
                            env.navigate(Route.AboutNext(data))

                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Filled.Info,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                        contentDescription = "Engineering Tools"
                    )
                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )
                    Text(
                        text = env.strings.engineeringTools, style = TextStyle(
                            color = Color.White,
                            fontFamily = myFontFamily()
                        )
                    )

                }
                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(50.dp)
                        .clip(shape = RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7F))
                        .padding(10.dp)
                        .clickable {

                            val data = Route.AboutNext.Data(title = env.strings.contactUs, aboutType = AboutRouteParams.CONTACT_US)
                            env.navigate(Route.AboutNext(data))

                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Filled.Info,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                        contentDescription = "Contact Us"
                    )
                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )
                    Text(
                        text = env.strings.contactUs, style = TextStyle(
                            color = Color.White,
                            fontFamily = myFontFamily()
                        )
                    )

                }

                Spacer(
                    modifier = Modifier.height(10.dp)
                )
                Spacer(
                    modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White)
                )
                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Image(
                    painter = painterResource(resource = AppLogo),
                    contentDescription = "App Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(55.dp)
                        .clip(shape = RoundCorner10)
                        .border(
                            width = 1.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp)
                        )
                )

                Text(
                    text = AppName,
                    style = TextStyle(
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black, offset = Offset(2f, 2f), blurRadius = 2f
                        ),
                        fontFamily = myFontFamily()
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = AppSlogan,
                    style = TextStyle(
                        color = Color.LightGray,
                        shadow = Shadow(
                            color = Color.Black, offset = Offset(2f, 2f), blurRadius = 2f
                        ),
                        fontFamily = myFontFamily(),
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = Disclaimer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .clip(shape = RoundedCornerShape(4.dp))
                        .padding(5.dp),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = myFontFamily()
                    )
                )

                Text(
                    text = Copyright,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .clip(shape = RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Gray.copy(alpha = 0.8F),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(5.dp),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = myFontFamily()
                    )
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

            }

        }

    } // root box


}
