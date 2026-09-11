package com.touhidapps.notification.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touhidapps.notification.domain.model.NotificationData
import com.touhidapps.ui.rememberScreenEnv

@Composable
fun NotificationScreen(contract: NotificationContract) {
    val env = rememberScreenEnv()
    val state by contract.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = env.strings.notifications, style = MaterialTheme.typography.headlineMedium)
            if (state.notifications.isNotEmpty()) {
                TextButton(onClick = { contract.onEvent(NotificationEvents.MarkAllRead) }) {
                    Text(env.strings.okContinue)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.errorMessage != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.errorMessage!!, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { contract.onEvent(NotificationEvents.Load) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(env.strings.okContinue) }
                }

                state.notifications.isEmpty() -> Text(env.strings.notificationsEmpty)

                else -> LazyColumn {
                    items(state.notifications, key = { it.id }) { item ->
                        NotificationRow(item) { contract.onEvent(NotificationEvents.MarkRead(item.id)) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationData, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = { Text(item.body) },
        trailingContent = if (!item.isRead) {
            { Text("•", color = MaterialTheme.colorScheme.primary) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
