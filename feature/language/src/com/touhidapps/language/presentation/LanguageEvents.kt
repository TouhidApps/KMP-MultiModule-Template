package com.touhidapps.language.presentation

import com.touhidapps.common.localization.Language

//import com.touhidapps.language.domain.model.Language


//import com.touhidapps.room.domain.model.Transaction


sealed interface LanguageEvents {
    data object GetCurrentLanguage : LanguageEvents
    data class SetNewLanguage(val language: Language) : LanguageEvents
//    data object SaveButtonClick : HomeEvents
//    data class EditItemButtonClick(val transaction: Transaction?) : HomeEvents
//    data object CancelUpdateClick : HomeEvents
//    data class ChangeTitle(val title: String) : HomeEvents
//    data class ChangeAmount(val amount: String) : HomeEvents
//    data class ChangeIsIncome(val isIncome: Boolean) : HomeEvents
//    data class ChangeDate(val date: Long) : HomeEvents
//    data class DeleteItemYesClick(val transaction: Transaction?) : HomeEvents

}


