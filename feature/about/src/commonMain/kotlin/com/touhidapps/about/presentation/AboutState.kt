package com.touhidapps.about.presentation

//import com.touhidapps.domain.flowers.model.FlowersModel
//import com.touhidapps.room.domain.model.Summary
//import com.touhidapps.room.domain.model.Transaction
//import com.touhidapps.room.utils.endOfTodayMillis

data class AboutState(
    val isLoading: Boolean = false,
//    val flowersModel: FlowersModel? = null,
    val errorMsg: String? = null
//    val transactions: List<Transaction> = emptyList(),
//    val summary: Summary? = null,
//
//    val title: String = "",
//    val amount: String = "",
//    val isIncome: Boolean = false,
//    val selectedDate: Long = endOfTodayMillis(),
//    val itemIdForUpdate: Int? = null,
)
