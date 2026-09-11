package com.touhidapps.kmp.core.firebase

import com.touhidapps.kmp.core.common.Analytics
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

/** [Analytics] backed by Firebase Analytics. Call [initializeFirebase] first. */
object FirebaseAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, String>) {
        Firebase.analytics.logEvent(name, params)
    }
}
