package com.touhidapps.kmp.core.common

/**
 * Thin abstraction over an analytics backend so feature code never imports
 * an SDK directly. `core/firebase` provides the real implementation for
 * Android/iOS; platforms it doesn't cover (desktop, web) wire [NoOpAnalytics]
 * instead — same interface either way, so feature code never has to care.
 */
interface Analytics {
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
}

object NoOpAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, String>) = Unit
}
