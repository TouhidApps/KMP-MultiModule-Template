package com.touhidapps.kmp

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.touhidapps.about.aboutEntry
import com.touhidapps.flowers.flowersEntry
import com.touhidapps.forceupdate.forceUpdateEntry
import com.touhidapps.home.homeEntry
import com.touhidapps.language.languageEntry
import com.touhidapps.more.moreEntry
import com.touhidapps.notification.notificationEntry
import com.touhidapps.settings.settingsEntry

// TODO: alertWelcome/alertMaintenance still use the placeholder scaffolding
//  (the old app never had real screens for them), so they are not wired
//  into this Navigation3 router.
fun appEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntry()
    flowersEntry()
    languageEntry()
    aboutEntry()
    forceUpdateEntry()
    moreEntry()
    notificationEntry()
    settingsEntry()
}
