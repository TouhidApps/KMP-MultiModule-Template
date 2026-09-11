package com.touhidapps.language.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LanguageContract {
    val state: StateFlow<LanguageState>
    fun onEvent(event: LanguageEvents)
    val actions: Flow<LanguageActions>
}


