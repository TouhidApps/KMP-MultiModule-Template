package com.touhidapps.settings.presentation

import androidx.lifecycle.viewModelScope
import com.touhidapps.common.base.BaseViewModel
import com.touhidapps.kmp.core.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: AppPreferences,
) : BaseViewModel(), SettingsContract {

    private val _state = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        preferences.darkMode
            .onEach { enabled -> _state.update { it.copy(darkMode = enabled) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: SettingsEvents) {
        when (event) {
            is SettingsEvents.ToggleDarkMode -> {
                viewModelScope.launch { preferences.setDarkMode(event.enabled) }
            }
        }
    }
}
