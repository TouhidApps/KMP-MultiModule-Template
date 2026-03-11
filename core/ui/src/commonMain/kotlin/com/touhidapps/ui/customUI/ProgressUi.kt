package com.touhidapps.ui.customUI

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun ProgressUi(isLoading: Boolean = false) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(50.dp), // adjust size
            color = MaterialTheme.colorScheme.primary, // or your custom color
            strokeWidth = 4.dp
        )
    }
}

