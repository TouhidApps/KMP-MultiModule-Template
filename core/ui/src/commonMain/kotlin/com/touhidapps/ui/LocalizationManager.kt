package com.touhidapps.ui

import androidx.compose.runtime.*
import com.touhidapps.common.localization.Language
import com.touhidapps.common.localization.Strings
import com.touhidapps.common.localization.StringsBn
import com.touhidapps.common.localization.StringsEn


/**
 * Use:
 * val s = LocalizationManager.strings
 * println(s.greeting)
 *
 * Change language:
 * LocalizationManager.setLanguage(Language.BN)
 *
 */
object LocalizationManager {

    var currentLanguage by mutableStateOf(Language.EN)
        private set

    private val localizedStrings = mapOf(
        Language.EN to StringsEn,
        Language.BN to StringsBn
    )

    val strings: Strings
        @Composable get() = localizedStrings[currentLanguage] ?: StringsEn

    fun updateLanguage(language: Language) {
        currentLanguage = language
    }

}

fun String.localizeNumber(): String =
    if (LocalizationManager.currentLanguage == Language.BN) {
        this.replace("0", "০")
            .replace("1", "১")
            .replace("2", "২")
            .replace("3", "৩")
            .replace("4", "৪")
            .replace("5", "৫")
            .replace("6", "৬")
            .replace("7", "৭")
            .replace("8", "৮")
            .replace("9", "৯")
    } else {
        this.replace("০", "0" )
            .replace("১", "1" )
            .replace("২", "2" )
            .replace("৩", "3" )
            .replace("৪", "4" )
            .replace("৫", "5" )
            .replace("৬", "6" )
            .replace("৭", "7" )
            .replace("৮", "8" )
            .replace("৯", "9" )
    }


