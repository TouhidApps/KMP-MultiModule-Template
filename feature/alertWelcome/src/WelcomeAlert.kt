package com.touhidapps.kmp.feature.alertwelcome

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Drop this next to your app content; it renders nothing once the user has
 * dismissed it. [onGetStarted] runs before the dialog closes, for apps that
 * want to jump straight into onboarding.
 */
@Composable
fun WelcomeAlert(
    viewModel: WelcomeAlertViewModel,
    title: String = "Welcome!",
    message: String = "Thanks for installing. Take a look around — you can change " +
        "the language and theme any time from Settings.",
    onGetStarted: () -> Unit = {},
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    if (!visible) return

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onGetStarted()
                    viewModel.dismiss()
                },
            ) {
                Text("Get started")
            }
        },
    )
}
