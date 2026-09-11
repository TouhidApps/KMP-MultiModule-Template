package com.touhidapps.language.data.repository

import com.touhidapps.language.domain.repository.LanguageRepository
import com.touhidapps.preferences.AppSettings
import com.touhidapps.common.localization.Language

class LanguageRepositoryImpl(
    private val appSettings: AppSettings
) : LanguageRepository {

    private val LANG = "lang"

    override fun getLanguage(): Language {
        return appSettings.getString(LANG, "EN")
            ?.let { Language.valueOf(it) }
            ?: Language.EN
    }

    override fun setLanguage(language: Language) {
        appSettings.putString(LANG, language.name)
    }

}