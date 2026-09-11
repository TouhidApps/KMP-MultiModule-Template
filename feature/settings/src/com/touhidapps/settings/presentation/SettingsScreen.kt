package com.touhidapps.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touhidapps.ui.LocalizationManager
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.rememberScreenEnv

@Composable
fun SettingsScreen(contract: SettingsContract) {
    val env = rememberScreenEnv()
    val state by contract.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Text(
            text = env.strings.settings,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp),
        )

        ListItem(
            headlineContent = { Text(env.strings.darkMode) },
            trailingContent = {
                Switch(
                    checked = state.darkMode,
                    onCheckedChange = { contract.onEvent(SettingsEvents.ToggleDarkMode(it)) },
                )
            },
            modifier = Modifier.clickable {
                contract.onEvent(SettingsEvents.ToggleDarkMode(!state.darkMode))
            },
        )

        ListItem(
            headlineContent = { Text(env.strings.language) },
            supportingContent = { Text(LocalizationManager.currentLanguage.name) },
            modifier = Modifier.clickable { env.navigate(Route.Language) },
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text(env.strings.titleAboutUs) },
            modifier = Modifier.clickable { env.navigate(Route.About) },
        )
    }
}
