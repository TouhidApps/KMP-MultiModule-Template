package com.touhidapps.about.presentation



sealed interface AboutActions { // HomeEffects
    data class ShowSnackbar(val message: String) : AboutActions

}