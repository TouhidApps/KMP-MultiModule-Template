package com.touhidapps.kmp.feature.alertwelcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touhidapps.kmp.core.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shows the welcome alert once per install. "Once" is persisted through
 * core/preferences so it survives process death and app restarts.
 */
class WelcomeAlertViewModel(
    private val preferences: AppPreferences,
) : ViewModel() {

    val visible: StateFlow<Boolean> = preferences.hasSeenWelcome
        .map { seen -> !seen }
        // Default false: never flash the dialog before preferences are read.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() {
        viewModelScope.launch { preferences.setHasSeenWelcome(true) }
    }
}
