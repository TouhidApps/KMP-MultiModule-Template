package com.touhidapps.more.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touhidapps.ui.rememberScreenEnv

@Composable
fun MoreScreen(contract: MoreContract) {
    val env = rememberScreenEnv()
    val state by contract.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = env.strings.more,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.items, key = { it.route.toString() }) { item ->
                ListItem(
                    headlineContent = { Text(item.title(env.strings)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable { env.navigate(item.route) },
                )
                HorizontalDivider()
            }
        }
    }
}
