package com.touhidapps.flowers.presentation.flowerDetails



sealed interface DetailsActions { // HomeEffects
    data class ShowSnackbar(val message: String) : DetailsActions

}