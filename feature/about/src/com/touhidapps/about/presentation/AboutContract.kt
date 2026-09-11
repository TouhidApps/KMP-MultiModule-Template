package com.touhidapps.about.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AboutContract {
    val state: StateFlow<AboutState>
    fun onEvent(event: AboutEvents)
    val actions: Flow<AboutActions>
}


