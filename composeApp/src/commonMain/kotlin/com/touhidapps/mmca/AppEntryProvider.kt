package com.touhidapps.mmca

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.touhidapps.about.aboutEntry
import com.touhidapps.flowers.flowersEntry
import com.touhidapps.forceupdate.forceUpdateEntry
import com.touhidapps.home.homeEntry
import com.touhidapps.language.languageEntry


fun appEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntry()
    flowersEntry()
    languageEntry()
    aboutEntry()
    forceUpdateEntry()
}



