package com.touhidapps.kmp.feature.alertmaintenance

/** Whether the backend is currently serving, as reported by the backend. */
sealed interface MaintenanceStatus {
    data object Operational : MaintenanceStatus

    /**
     * @param blocking true when the app must not be used at all; false for a
     *   heads-up banner the user can dismiss.
     * @param endsAt human-readable window end, or null when unknown.
     */
    data class UnderMaintenance(
        val message: String,
        val blocking: Boolean,
        val endsAt: String? = null,
    ) : MaintenanceStatus
}
