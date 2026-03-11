package com.touhidapps.ui.theme

import com.touhidapps.ui.Res
import com.touhidapps.ui.*


/**
 * With gradle config: (Outside of kotlin{} block)
 * compose.resources {
 *     publicResClass = true // exposes Res.drawable.*
 * }
 * Inside plugin:
 *     alias(libs.plugins.composeMultiplatform)
 *     alias(libs.plugins.composeCompiler)
 *
 * Use:
 * Image(
 *     painterResource(Icon.Logo),
 *     contentDescription = null
 * )
 */

class Resources {

    object Drawable {
        val AppLogo = Res.drawable.app_logo
        val IcLanguage = Res.drawable.ic_language
        val IcInfo = Res.drawable.baseline_info_outline_24
        val Bg = Res.drawable.bg
        val FlagBd = Res.drawable.flag_bd
        val FlagEn = Res.drawable.flag_en
    }

    object Fonts {
        val SolaimanLipi = Res.font.solaiman_lipi
    }

}
