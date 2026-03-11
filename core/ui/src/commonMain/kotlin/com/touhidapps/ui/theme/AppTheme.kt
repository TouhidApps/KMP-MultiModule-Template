package com.touhidapps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable


@Composable
fun AppTheme(
    colorSchemeIndex: Int = 0,
    fontIndex: Int = 1,
    isSystemInDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    MaterialTheme(
        colorScheme = when(colorSchemeIndex) {
            0 -> lightColorScheme
            1 -> darkColorScheme
            2 -> if (isSystemInDark) darkColorScheme else lightColorScheme
            3 -> colorfulScheme
            else -> lightColorScheme
        },
        typography = myTypography(),
//        typography = when(fontIndex) {
//            0 -> Typography()
//            1 -> appCustomTypography(myFont())
//            2 -> appCustomTypography(meriendaFont())
//            else -> Typography()
//        },
        content = content
    )

}