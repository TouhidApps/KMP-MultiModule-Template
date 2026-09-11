package com.touhidapps.kmp.core.firebase

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize

fun androidInitializeFirebase(context: Context, config: FirebaseConfig) {
    Firebase.initialize(
        context,
        FirebaseOptions(
            applicationId = config.applicationId,
            apiKey = config.apiKey,
            projectId = config.projectId,
            storageBucket = config.storageBucket,
            gcmSenderId = config.gcmSenderId,
        ),
    )
}
