package com.touhidapps.language.domain.usecase

import com.touhidapps.common.localization.Language
import com.touhidapps.language.domain.repository.LanguageRepository

class GetLanguageUseCase(
    private val repository: LanguageRepository
) {
    operator fun invoke(): Language = repository.getLanguage()
}

class SetLanguageUseCase(
    private val repository: LanguageRepository
) {
    operator fun invoke(language: Language) = repository.setLanguage(language)
}
