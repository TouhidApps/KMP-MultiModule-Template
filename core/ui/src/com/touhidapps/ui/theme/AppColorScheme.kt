package com.touhidapps.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

val lightColorScheme = lightColorScheme(
    primary = Blue900,
    onPrimary = white,
    primaryContainer = Grey200,
    onPrimaryContainer = Grey900,
)

val darkColorScheme = darkColorScheme(
    primary = white,
    onPrimary = Blue900,
    primaryContainer = Grey900,
    onPrimaryContainer = Grey200,
)

val colorfulScheme = lightColorScheme(
    primary = Red,
    onPrimary = Red100,
    primaryContainer = Red200,
    onPrimaryContainer = Red400,

    surface = Red200,
    onSurface = Red400
)