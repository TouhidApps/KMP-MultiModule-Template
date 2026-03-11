package com.touhidapps.preferences

import com.russhwolf.settings.Settings

object AppSettings {

    private val settings: Settings = Settings()

    fun putString(key: String, value: String) {
        settings.putString(key, value)
    }

    fun getString(key: String, default: String = ""): String {
        return settings.getString(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        settings.putBoolean(key, value)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return settings.getBoolean(key, default)
    }

    fun remove(key: String) {
        settings.remove(key)
    }

    fun hasKey(key: String): Boolean {
        return settings.hasKey(key)
    }

    fun clearAll() {
        settings.clear()
    }

}