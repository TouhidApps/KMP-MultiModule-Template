package com.touhidapps.flowers.presentation.flowerList

import com.touhidapps.flowers.domain.model.FlowerModel


//import com.touhidapps.room.domain.model.Transaction


sealed interface FlowerListEvents {
    data class SetFlowerData(val flowerModel: FlowerModel? = null) : FlowerListEvents
    data object GetFlowerData : FlowerListEvents
//    data object SaveButtonClick : HomeEvents
//    data class EditItemButtonClick(val transaction: Transaction?) : HomeEvents
//    data object CancelUpdateClick : HomeEvents
//    data class ChangeTitle(val title: String) : HomeEvents
//    data class ChangeAmount(val amount: String) : HomeEvents
//    data class ChangeIsIncome(val isIncome: Boolean) : HomeEvents
//    data class ChangeDate(val date: Long) : HomeEvents
//    data class DeleteItemYesClick(val transaction: Transaction?) : HomeEvents

}


