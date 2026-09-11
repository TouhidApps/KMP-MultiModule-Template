package com.touhidapps.kmp.core.firebase

/**
 * The values that would otherwise come from `google-services.json` /
 * `GoogleService-Info.plist`. A template can't ship a real Firebase
 * project's secrets, so the app supplies them explicitly — read from
 * build config, a local.properties-backed constant, or similar; never
 * hardcode real values into source control.
 *
 * Find these in the Firebase console under Project settings > General >
 * "Your apps" > the relevant app's config snippet.
 */
data class FirebaseConfig(
    val applicationId: String,
    val apiKey: String,
    val projectId: String,
    val storageBucket: String? = null,
    val gcmSenderId: String? = null,
)
