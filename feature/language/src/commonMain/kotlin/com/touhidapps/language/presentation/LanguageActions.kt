package com.touhidapps.language.presentation



sealed interface LanguageActions { // HomeEffects
    data class ShowSnackbar(val message: String) : LanguageActions

}