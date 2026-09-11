package com.touhidapps.ui.customUI

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.touhidapps.ui.Res
import com.touhidapps.ui.compose_multiplatform

@Preview
@Composable
fun PreviewMyAsyncImage() {
    Column {
        MyAsyncImage(
            iUrl = "https://example.com/image.png",
            imgDescription = "Sample Image",
        )
    }
}

@Composable
fun MyAsyncImage(
    iUrl: String? = null,
    imgDescription: String? = "Image",
    modifier: Modifier = Modifier.size(100.dp)
) {
    // In Preview mode, we use a local resource as the model so Coil renders it in the IDE.
    // In the real app, we use the provided URL.
    val imageModel = if (LocalInspectionMode.current) {
        Res.drawable.compose_multiplatform
    } else {
        iUrl
    }

    if (imageModel != null) {
        SubcomposeAsyncImage(
            modifier = modifier,
            model = imageModel,
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ImageNotSupported,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            contentDescription = imgDescription ?: "Image"
        )
    }
}
