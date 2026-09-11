package com.touhidapps.home.presentation



sealed interface HomeActions { // HomeEffects
    data class ShowSnackbar(val message: String) : HomeActions

}