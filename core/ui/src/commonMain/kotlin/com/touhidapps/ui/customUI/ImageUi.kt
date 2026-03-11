package com.touhidapps.ui.customUI

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

@Composable
fun MyAsyncImage(iUrl: String? = null, imgDescription: String? = "Image") {

    if (!iUrl.isNullOrBlank()) {
        SubcomposeAsyncImage(
            modifier = Modifier.size(100.dp),
            model = iUrl,
            loading = {
                Box(modifier = Modifier.size(45.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            contentDescription = imgDescription ?: "Image"
        )

//        AsyncImage(
//            model = iUrl,
//            modifier = Modifier.size(100.dp),
//            contentDescription = null,
//        )


    }


//    AsyncImage(
//        model = ImageRequest.Builder(LocalPlatformContext.current)
//            .data("item.imageUrl")
//            .crossfade(true)
//            .build(),
////        placeholder = painterResource(R.drawable.placeholder),
////        error = painterResource(R.drawable.error),
//        contentDescription = item.name,
//        contentScale = ContentScale.Crop,
//        modifier = Modifier
//            .size(90.dp)
//            .clip(RoundedCornerShape(10.dp))
//    )

}