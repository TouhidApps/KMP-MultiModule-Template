package com.touhidapps.kmp.core.preferences

import kotlinx.coroutines.flow.Flow

/** Key/value user settings, observable so the UI can react to changes. */
interface AppPreferences {
    val darkMode: Flow<Boolean>
    suspend fun setDarkMode(enabled: Boolean)

    /** Whether the one-shot welcome alert has already been dismissed. */
    val hasSeenWelcome: Flow<Boolean>
    suspend fun setHasSeenWelcome(seen: Boolean)
}
