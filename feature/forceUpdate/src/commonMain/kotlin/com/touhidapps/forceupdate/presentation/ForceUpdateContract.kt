package com.touhidapps.forceupdate.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ForceUpdateContract {
    val state: StateFlow<ForceUpdateState>
    fun onEvent(event: ForceUpdateEvents)
    val actions: Flow<ForceUpdateActions>
}


