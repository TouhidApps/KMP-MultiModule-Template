package com.touhidapps.about.presentation

//import com.touhidapps.room.domain.model.Transaction


sealed interface AboutEvents {
    data object LoadAllFlowers : AboutEvents
//    data object SaveButtonClick : HomeEvents
//    data class EditItemButtonClick(val transaction: Transaction?) : HomeEvents
//    data object CancelUpdateClick : HomeEvents
//    data class ChangeTitle(val title: String) : HomeEvents
//    data class ChangeAmount(val amount: String) : HomeEvents
//    data class ChangeIsIncome(val isIncome: Boolean) : HomeEvents
//    data class ChangeDate(val date: Long) : HomeEvents
//    data class DeleteItemYesClick(val transaction: Transaction?) : HomeEvents

}


