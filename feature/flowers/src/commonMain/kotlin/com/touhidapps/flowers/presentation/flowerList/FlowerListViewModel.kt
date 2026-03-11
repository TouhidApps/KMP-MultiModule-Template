package com.touhidapps.flowers.presentation.flowerList

import androidx.lifecycle.viewModelScope
import com.touhidapps.common.base.BaseViewModel
import com.touhidapps.flowers.domain.usecase.FlowerListUseCase
import com.touhidapps.network.onError
import com.touhidapps.network.onSuccess
//import com.touhidapps.common.networkResult.onError
//import com.touhidapps.home.domain.usecase.FlowerGetUseCase
//import com.touhidapps.room.base.BaseViewModel
//import com.touhidapps.room.domain.model.Transaction
//import com.touhidapps.room.domain.usecase.TransactionDeleteUseCase
//import com.touhidapps.room.domain.usecase.TransactionGetAllUseCase
//import com.touhidapps.room.domain.usecase.TransactionSummaryUseCase
//import com.touhidapps.room.domain.usecase.TransactionUpsertUseCase
//import com.touhidapps.room.utils.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FlowerListViewModel(
    private val useCase: FlowerListUseCase,
): BaseViewModel(), FlowerListContract {

//    private val _homeState = MutableStateFlow(HomeState())
//    override val state: StateFlow<HomeState> = _homeState.asStateFlow()

    override final val state: StateFlow<FlowerListState> field = MutableStateFlow(FlowerListState())
//    val city: StateFlow<String> field = MutableStateFlow("")


    /**
     * @param replay = 0: No previously emitted values will be replayed to new collectors after config change
     * @param extraBufferCapacity = 1 makes sure at least one action can be sent even if the collector isn’t ready yet,
     * so UI won’t miss the event.
     */
//    private val _actions = MutableSharedFlow<FlowerListActions>(replay = 0, extraBufferCapacity = 1)
//    override val actions: Flow<FlowerListActions> = _actions

    override final val actions: Flow<FlowerListActions> field = MutableSharedFlow<FlowerListActions>(replay = 0, extraBufferCapacity = 1)

    init {
        onEvent(FlowerListEvents.GetFlowerData)
    }

    override fun onEvent(event: FlowerListEvents) {

        when(event) {

            is FlowerListEvents.GetFlowerData -> {

                loadFlowers()

//                state.update {
//
//                    it.copy(isLoading = true)

//                    it.copy(
//                        title = event.transaction?.title ?: "",
//                        amount = "${event.transaction?.amount ?: 0.0}",
//                        itemIdForUpdate = event.transaction?.id,
//                        isIncome = event.transaction?.isIncome ?: false,
//                        selectedDate = event.transaction?.transactionTimeStamp ?: endOfTodayMillis(),
//                    )
//                }

            }

//            HomeEvents.CancelUpdateClick -> {
//
//                state.update {
//                    it.copy(
//                        title = "",
//                        amount = "",
//                        itemIdForUpdate = null,
//                        isIncome = false,
//                        selectedDate = endOfTodayMillis(),
//                    )
//                }
//
//            }
//
//            is HomeEvents.ChangeTitle -> {
//                state.update { it.copy(title = event.title) }
//            }
//
//            is HomeEvents.ChangeAmount -> {
//                state.update { it.copy(amount = "${event.amount}") }
//            }
//
//            is HomeEvents.ChangeIsIncome -> {
//                state.update { it.copy(isIncome = event.isIncome) }
//            }
//
//            is HomeEvents.ChangeDate -> {
//                state.update { it.copy(selectedDate = event.date) }
//            }
//
//            HomeEvents.LoadAllTransactions -> {
//
//                getAllTransactions()
//
//            }
//
//            HomeEvents.SaveButtonClick -> {
//
//                saveTransaction()
//
//            }
//
//            is HomeEvents.DeleteItemYesClick -> {
//
//                deleteTransaction(event.transaction)
//
//            }

            else -> {}

        }

    }

    private fun loadFlowers() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, errorMsg = null) }
            useCase()
                .onSuccess { data ->
                    state.update { it.copy(flowersModel = data, isLoading = false, errorMsg = null) }
                }
                .onError { err ->
                    state.update { it.copy(flowersModel = null, isLoading = false, errorMsg = err.errMessage) }
                }
        }
    }

//    private fun saveTransaction() {
//
//        viewModelScope.launch {
//
//            when {
//                state.value.title?.isEmpty() == true -> {
//                    _actions.emit(HomeActions.ShowSnackbar("⚠️ Title required"))
//                    return@launch
//                }
//
//                state.value.amount?.isEmpty() == true -> {
//                    _actions.emit(HomeActions.ShowSnackbar("⚠️ Amount required"))
//                    return@launch
//                }
//            }
//
//            val transaction = Transaction(
//                id = state.value.itemIdForUpdate ?: 0, // 0 means insert, Room will autogenerate
//                title = state.value.title ?: "",
//                amount = state.value.amount?.toDouble() ?: 0.0,
//                isIncome = state.value.isIncome,
//                transactionTimeStamp = state.value.selectedDate,
//                entryTimeStamp = formatMillisWithTimeNow()
//            )
//
//            state.update { it.copy(isLoading = true) }
//            val ok = transactionUpsertUseCase(transaction) // suspend Boolean
//            delay(1000)
//            state.update { it.copy(isLoading = false) }
//            _actions.emit(HomeActions.ShowSnackbar(if (ok) "Saved ✅" else "Failed ❌"))
//            if (state.value.itemIdForUpdate != null) { // when update data
//                state.update { it.copy(selectedDate = endOfTodayMillis()) }
//            }
//            if (ok) onEvent(HomeEvents.LoadAllTransactions) // Refresh data
//
//        }
//    }
//
//    private fun getAllTransactions() {
//
//        viewModelScope.launch {
//
//            if (state.value.isLoading) return@launch
//
//            state.update { it.copy(isLoading = true, title = "", amount = "", itemIdForUpdate = null) }
//            val res = transactionGetAllUseCase() // suspend Boolean
//            delay(1000)
//
//
//            val summary = transactionSummaryUseCase()
//
//            state.update { it.copy(isLoading = false, transactions = res, summary = summary) }
//
//
//
//        }
//
//    }
//
//
//    private fun deleteTransaction(transaction: Transaction?) {
//
//        viewModelScope.launch {
//
//            transaction?.let {
//                state.update { it.copy(isLoading = true) }
//                val res = transactionDeleteUseCase(it)
//                delay(1000)
//                state.update { it.copy(isLoading = false) }
//                _actions.emit(HomeActions.ShowSnackbar(if(res) "Transaction deleted successfully" else "⚠️ Something went wrong"))
//
//                onEvent(HomeEvents.LoadAllTransactions) // Refresh data
//
//
////                    .onStart {
////                        state.update { it.copy(isLoading = true) }
////                    }
////                    .catch { cause ->
////                        state.update { it.copy(isLoading = false) }
////                        _actions.emit(HomeActions.ShowSnackbar("⚠️ Something went wrong (Exception: ${cause.message})"))
////                    }
////                    .collect { result ->
////                        state.update { it.copy(isLoading = false) }
////                        _actions.emit(HomeActions.ShowSnackbar(if(result) "Transaction deleted successfully" else "⚠️ Something went wrong"))
////
////                        onEvent(HomeEvents.LoadAllTransactions) // Refresh data
////                    }
//            }
//
//
//        }
//    }

}