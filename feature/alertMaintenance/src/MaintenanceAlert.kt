package com.touhidapps.kmp.feature.alertmaintenance

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Overlays a maintenance notice on the app content. A blocking window has no
 * dismiss button — only "Retry", which re-checks the backend.
 */
@Composable
fun MaintenanceAlert(
    viewModel: MaintenanceViewModel,
    content: @Composable () -> Unit,
) {
    content()

    val status by viewModel.status.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()

    val current = status
    if (current !is MaintenanceStatus.UnderMaintenance) return
    if (!current.blocking && dismissed) return

    AlertDialog(
        onDismissRequest = { if (!current.blocking) viewModel.dismiss() },
        title = { Text("Under maintenance") },
        text = {
            Text(
                current.endsAt
                    ?.let { "${current.message}\n\nExpected back: $it" }
                    ?: current.message
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::refresh) { Text("Retry") }
        },
        dismissButton = if (current.blocking) {
            null
        } else {
            { TextButton(onClick = viewModel::dismiss) { Text("Dismiss") } }
        },
    )
}
