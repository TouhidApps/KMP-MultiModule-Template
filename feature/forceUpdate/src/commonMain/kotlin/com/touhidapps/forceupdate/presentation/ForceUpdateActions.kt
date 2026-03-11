package com.touhidapps.forceupdate.presentation



sealed interface ForceUpdateActions { // HomeEffects
    data class ShowSnackbar(val message: String) : ForceUpdateActions

}