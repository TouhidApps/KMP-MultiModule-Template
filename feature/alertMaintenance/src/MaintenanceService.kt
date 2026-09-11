package com.touhidapps.kmp.feature.alertmaintenance

import com.touhidapps.network.NetworkError
import com.touhidapps.network.ResultNet
import com.touhidapps.network.safeApiCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodedPath

interface MaintenanceService {
    suspend fun status(): ResultNet<MaintenanceStatus, NetworkError>
}

/**
 * Expects `ok` when everything is fine, otherwise
 * `maintenance|<blocking>|<message>|<endsAt>`. Swap the parsing for
 * kotlinx-serialization once the real endpoint exists.
 */
class RemoteMaintenanceService(
    private val httpClient: HttpClient,
    // TODO - point this at your real backend endpoint
    private val path: String = "/app/maintenance",
) : MaintenanceService {

    override suspend fun status(): ResultNet<MaintenanceStatus, NetworkError> = safeApiCall {
        val body = httpClient.get { url { encodedPath = path } }.bodyAsText()
        val parts = body.trim().split('|')
        if (parts.firstOrNull() != "maintenance") {
            MaintenanceStatus.Operational
        } else {
            MaintenanceStatus.UnderMaintenance(
                message = parts.getOrNull(2) ?: "We are performing maintenance.",
                blocking = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true,
                endsAt = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
            )
        }
    }
}
