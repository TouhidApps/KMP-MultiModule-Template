package com.touhidapps.kmp.core.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

/** Firebase user, narrowed to what callers actually need. */
data class AppUser(val uid: String, val isAnonymous: Boolean)

/**
 * Thin wrapper over Firebase Auth. Exposes [AppUser], not GitLive's
 * `FirebaseUser`, so callers never import the SDK type. Call
 * [initializeFirebase] first.
 */
class AuthService {

    val currentUser: AppUser?
        get() = Firebase.auth.currentUser?.let { AppUser(it.uid, it.isAnonymous) }

    /** Returns the current user, signing in anonymously first if there isn't one. */
    suspend fun ensureSignedIn(): AppUser {
        currentUser?.let { return it }
        val result = Firebase.auth.signInAnonymously()
        val user = requireNotNull(result.user) { "signInAnonymously() returned no user" }
        return AppUser(user.uid, user.isAnonymous)
    }

    suspend fun signOut() {
        Firebase.auth.signOut()
    }
}
