package com.touhidapps.flowers.presentation.flowerList



sealed interface FlowerListActions { // HomeEffects
    data class ShowSnackbar(val message: String) : FlowerListActions

}