package com.touhidapps.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touhidapps.ui.LocalizationManager.strings
import com.touhidapps.ui.nav.Route
import org.jetbrains.compose.resources.painterResource

@Composable
fun NavigationDrawerContent(
    modifier: Modifier = Modifier,
    route: ((Route) -> Unit)? = null
) {

    ModalDrawerSheet {
        Column(
            modifier = modifier.fillMaxHeight().padding(16.dp)
        ) {
//            NavigationDrawerItem(
//                modifier = Modifier.fillMaxWidth(),
//                label = { Text(text = "Close") },
//                selected = false,
//                onClick = onClose,
//                icon = { Icon(Icons.Filled.Close, contentDescription = "Close drawer") }
//            )

            Column(
                modifier = Modifier.fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundCorner8)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5F), Color.DarkGray.copy(alpha = 0.5F)
                            )
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Image(
                    painter = painterResource(resource = Resources.Drawable.IcInfo),
                    contentDescription = "App Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(shape = RoundedCornerShape(10.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp)
                        )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = strings.appName, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = TextStyle(
                    fontSize = 17.sp, color = Color.White
                ))

                Text(text = strings.appSlogan, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = TextStyle(
                    fontSize = 13.sp, color = Color.White
                ))

            }

            NavigationDrawerItem(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = strings.home) },
                selected = false,
                onClick = {
                    route?.invoke(Route.Home)
                },
                icon = {
                    Image(
                        painter = rememberVectorPainter(Icons.Default.Home),
                        contentDescription = strings.home,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundCorner8)
                            .background(Color.White)
                            .padding(2.dp)
                    )
                }
            )

            NavigationDrawerItem(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Language/ভাষা") },
                selected = false,
                onClick = {
                    route?.invoke(Route.Language)
                },
                icon = {
                    Image(
                        painter = painterResource(resource = Resources.Drawable.IcLanguage),
                        contentDescription = "Language/ভাষা",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(2.dp)
                    )
                }
            )

            NavigationDrawerItem(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = strings.titleAboutUs) },
                selected = false,
                onClick = {
                    route?.invoke(Route.About)
                },
                icon = {
                    Image(
                        painter = painterResource(resource = Resources.Drawable.IcInfo),
                        contentDescription = strings.titleAboutUs,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundCorner8)
                            .background(Color.White)
                            .padding(2.dp)
                    )
                }
            )

        }
    }

}