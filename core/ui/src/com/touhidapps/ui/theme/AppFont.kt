package com.touhidapps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.touhidapps.ui.theme.Resources.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font



@OptIn(ExperimentalResourceApi::class)
@Composable
fun myFontFamily() = FontFamily(
//    Font(R.Fonts.SolaimanLipi, weight = FontWeight.Normal),
//    Font(R.Fonts.SolaimanLipi, weight = FontWeight.Bold)
    Font(Fonts.SolaimanLipi)
)

@Composable
fun myTypography() = Typography().run {

    val fontFamily = myFontFamily()
    copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily =  fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily)
    )

}


//@Composable
//fun myFont(): FontFamily {
//
//    return FontFamily(
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Thin),
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Normal),
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Normal),
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Bold)
//    )
//
//}
//
//@Composable
//fun meriendaFont(): FontFamily {
//
//    return FontFamily(
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Thin),
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Normal),
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Bold)
//    )
//
//}
//
//@Composable
//fun lavishlyFont(): FontFamily {
//
//    return FontFamily(
//        Font(mrtmatebd.composeapp.generated.resources.Res.font.solaiman_lipi, FontWeight.Normal)
//    )
//
//}
//
//@Composable
//fun appCustomTypography(fontFamily: FontFamily): Typography {
//
//    val ts = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal)
//
//    return Typography(
//        bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
//        bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Thin, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
//        bodySmall = ts,
//
//        displayLarge = ts,
//        displayMedium = ts,
//        displaySmall = ts,
//
//        headlineLarge = ts,
//        headlineMedium = ts,
//        headlineSmall = ts,
//
//        labelLarge = ts,
//        labelMedium = ts,
//        labelSmall = ts,
//
//        titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.5.sp),
//        titleMedium = ts,
//        titleSmall = ts
//    )
//
//}