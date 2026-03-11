package com.touhidapps.flowers.presentation.flowerList

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FlowerListContract {
    val state: StateFlow<FlowerListState>
    fun onEvent(event: FlowerListEvents)
    val actions: Flow<FlowerListActions>
}


