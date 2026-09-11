package com.touhidapps.flowers.presentation.flowerDetails

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DetailsContract {
    val state: StateFlow<DetailsState>
    fun onEvent(event: DetailsEvents)
    val actions: Flow<DetailsActions>
}


