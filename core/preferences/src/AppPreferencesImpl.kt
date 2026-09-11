package com.touhidapps.kmp.core.preferences

import com.touhidapps.preferences.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_DARK_MODE = "dark_mode"
private const val KEY_HAS_SEEN_WELCOME = "has_seen_welcome"

/**
 * Backed by [AppSettings] (the same key/value store `feature/language`
 * persists the chosen language through), so every preference in the app
 * goes through one underlying mechanism. Exposed as [Flow] via an
 * in-memory [MutableStateFlow] seeded from storage — read-modify-write all
 * goes through [setDarkMode]/[setHasSeenWelcome], so this stays consistent
 * as long as nothing else writes these two keys directly.
 */
class AppPreferencesImpl : AppPreferences {

    private val _darkMode = MutableStateFlow(AppSettings.getBoolean(KEY_DARK_MODE))
    override val darkMode: Flow<Boolean> = _darkMode.asStateFlow()

    override suspend fun setDarkMode(enabled: Boolean) {
        AppSettings.putBoolean(KEY_DARK_MODE, enabled)
        _darkMode.value = enabled
    }

    private val _hasSeenWelcome = MutableStateFlow(AppSettings.getBoolean(KEY_HAS_SEEN_WELCOME))
    override val hasSeenWelcome: Flow<Boolean> = _hasSeenWelcome.asStateFlow()

    override suspend fun setHasSeenWelcome(seen: Boolean) {
        AppSettings.putBoolean(KEY_HAS_SEEN_WELCOME, seen)
        _hasSeenWelcome.value = seen
    }
}
