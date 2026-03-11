package com.touhidapps.flowers.presentation.flowerDetails

import com.touhidapps.flowers.domain.model.FlowerModel


sealed interface DetailsEvents {
    data class SetFlowerData(val flowerModel: FlowerModel? = null) : DetailsEvents

    // Examples
//    data object SaveButtonClick : DetailsEvents
//    data class EditItemButtonClick(val transactionId: Int?) : DetailsEvents
//    data object CancelUpdateClick : DetailsEvents
//    data class ChangeAmount(val amount: String) : DetailsEvents

}


