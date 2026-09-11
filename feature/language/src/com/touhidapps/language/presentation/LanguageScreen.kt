package com.touhidapps.language.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touhidapps.common.localization.Language
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.rememberScreenEnv
import com.touhidapps.ui.theme.Resources.Drawable.FlagBd
import com.touhidapps.ui.theme.Resources.Drawable.FlagEn
import com.touhidapps.ui.theme.myFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource


@Composable
fun LanguageScreen(
    contract: LanguageContract,
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
//        contract.onEvent(HomeEvents.ChangeDate(endOfTodayMillis()))

        // Collect upsert
        launch {
//            contract.state
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
                    is LanguageActions.ShowSnackbar -> {



                        // forward to common VM
//                        commonContract.onEvent(CommonEvents.ShowSnackbar(action.message))
                    }
                }
            }
        }

    }

    // Background & Main Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0)), // Light background color
        contentAlignment = Alignment.Center
    ) {
        // Language Options Container
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Select Your Language\nভাষা সিলেক্ট করুন", style = TextStyle(
                    fontSize = 25.sp, color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    fontFamily = myFontFamily()
                )
            )

            Spacer(modifier = Modifier.height(25.dp))

            TriangleButton() {
                contract.onEvent(LanguageEvents.SetNewLanguage(language = it))
                env.navigate(Route.Home)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Current Language: ${state.languageTitle}", style = TextStyle(
                    fontSize = 15.sp, color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    fontFamily = myFontFamily()
                )
            )

        }
    }
}

@Composable
fun LanguageOption(
    modifier: Modifier = Modifier,
    flagRes: DrawableResource,
    languageName: String
) {

    Column(
        modifier = modifier
            .height(180.dp)
            .width(180.dp)
            .padding(15.dp)
//            .background(Color.White, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(resource = flagRes),
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
//                .background(Color.LightGray)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = languageName,
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }

}


@Composable
fun TriangleButton(
    onLanguageSelected: (Language) -> Unit
) {

    Box {
        Box(
            modifier = Modifier
                .size(350.dp)
                .rotate(90F)
                .clip(TriangleShape())
                .background(Color(0xFF6C6C6C))
//                .background(Color(0xFF012169))
                .clickable(onClick = {
                    onLanguageSelected(Language.EN)
                }),
            contentAlignment = Alignment.BottomStart
        ) {
            LanguageOption(
                modifier = Modifier.rotate(-90F),
                flagRes = FlagEn,
                languageName = "English"
            )
        }

        Box(
            modifier = Modifier
                .size(350.dp)
                .rotate(-90F)
                .clip(TriangleShape())
                .background(Color(0xFF939393))
//                .background(Color.Red)
                .clickable(onClick = {
                    onLanguageSelected(Language.BN)
                }),
            contentAlignment = Alignment.BottomStart
        ) {
            LanguageOption(
                modifier = Modifier.rotate(90F),
                flagRes = FlagBd,
                languageName = "Bangla/বাংলা"
            )
        }
    }

}

class TriangleShape : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(0F, 0F)              // Top center
            lineTo(size.width, size.height)          // Bottom right
            lineTo(0f, size.height)                  // Bottom left
            close()
        }
        return Outline.Generic(path)
    }

}