package com.touhidapps.home.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

//import com.touhidapps.home.domain.model.FlowerModel
//import com.touhidapps.home.domain.model.FlowersModel
import com.touhidapps.ui.customUI.ErrorMessageWithIcon
import com.touhidapps.ui.customUI.MyAsyncImage
import com.touhidapps.ui.customUI.ProgressUi
import com.touhidapps.ui.nav.Route
//import com.touhidapps.ui.nav.mapper.toRouteModel
import com.touhidapps.ui.rememberScreenEnv
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
//    commonContract: CommonContract = koinViewModel<CommonViewModel>(),
    contract: HomeContract
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
                    is HomeActions.ShowSnackbar -> {



                        // forward to common VM
//                        commonContract.onEvent(CommonEvents.ShowSnackbar(action.message))
                    }
                }
            }
        }

    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Spacer(modifier = Modifier.height(15.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
              //  contract.onEvent(HomeEvents.GoToFlowers)
                env.navigate(Route.FlowerList)
            }) {
                Text(env.strings.loadFlowers)
            }
        }

        ErrorMessageWithIcon(state.errorMsg)

    }

}

