package com.touhidapps.language.domain.repository

import com.touhidapps.common.localization.Language


interface LanguageRepository {
    fun getLanguage(): Language
    fun setLanguage(language: Language)
}

