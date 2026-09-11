package com.touhidapps.settings.presentation

import kotlinx.coroutines.flow.StateFlow

data class SettingsState(
    val darkMode: Boolean = false,
)

sealed interface SettingsEvents {
    data class ToggleDarkMode(val enabled: Boolean) : SettingsEvents
}

interface SettingsContract {
    val state: StateFlow<SettingsState>
    fun onEvent(event: SettingsEvents)
}
