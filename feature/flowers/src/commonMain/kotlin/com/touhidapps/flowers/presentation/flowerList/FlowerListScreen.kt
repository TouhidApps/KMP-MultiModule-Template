package com.touhidapps.flowers.presentation.flowerList

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touhidapps.flowers.domain.model.FlowerModel
import com.touhidapps.flowers.domain.model.FlowersModel
import com.touhidapps.ui.customUI.MyAsyncImage
import com.touhidapps.ui.customUI.ProgressUi
import com.touhidapps.ui.nav.Route
import com.touhidapps.ui.rememberScreenEnv


@Composable
fun FlowerListScreen(contract: FlowerListContract) {

    val env = rememberScreenEnv()

    val state by contract.state.collectAsStateWithLifecycle() // collectAsState() for reactive recomposition.

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        FlowerList(
            items = state.flowersModel,
            onItemClick = {
                env.navigate(
                    Route.FlowerDetails(
                        data = Route.FlowerDetails.Data(flowerId = it.id)
                    )
                )
            }
        )

        ProgressUi(state.isLoading)

    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlowerList(
    items: FlowersModel?,
    onItemClick: (FlowerModel) -> Unit
) {
    val env = rememberScreenEnv()

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sticky Header with Summary
        if (items?.flowers?.isNotEmpty() == true) {
            stickyHeader {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF37474F)) // Dark gray-blue
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${env.strings.totalItemShowing}: ${items.flowers.size}",
                        color = Color(0xFF4CAF50), // Green
                        fontWeight = FontWeight.Bold
                    )
                }

            }
        }

        // List Items
        items(items?.flowers?.toList() ?: emptyList()) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // 🔹 LEFT IMAGE
                    MyAsyncImage("${item.imgData?.baseUrl}${item.imgData?.fileName}", item.details)

                    // 🔹 RIGHT CONTENT
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        Text(
                            text = item.name ?: "",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = item.details ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (item.isActive == true) "Active" else "Inactive",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isActive == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )

                    }

                }
            }
        }


    }
}