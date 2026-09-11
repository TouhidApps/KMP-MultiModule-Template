package com.touhidapps.forceupdate.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touhidapps.common.constants.Const.AppName
import com.touhidapps.common.constants.Const.AppSlogan
import com.touhidapps.ui.theme.Resources.Drawable.AppLogo
import com.touhidapps.ui.theme.RoundCorner10
import com.touhidapps.ui.theme.myFontFamily
import org.jetbrains.compose.resources.painterResource


@Composable
fun ForceUpdateScreen(contract: ForceUpdateContract) {

    Column(modifier = Modifier.fillMaxSize().padding(15.dp)) {

//        ForceUpdateScreen(
//            modifier = Modifier.weight(1f),
//            appName = contract.appName,
//            slogan = contract.slogan,
//            onUpdateClick = contract::onUpdateClick
//        )

      //  KMP template with back end and admin frontend
    //    add most common features

    }

}

@Composable
fun ForceUpdateScreen(
    modifier: Modifier = Modifier,
    appName: String,
    slogan: String,
    onUpdateClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.weight(1f))

            // App Logo
            Image(
                painter = painterResource(resource = AppLogo),
                contentDescription = "App Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(55.dp)
                    .clip(shape = RoundCorner10)
                    .border(
                        width = 1.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp)
                    )
            )

            Text(
                text = AppName,
                style = TextStyle(
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black, offset = Offset(2f, 2f), blurRadius = 2f
                    ),
                    fontFamily = myFontFamily()
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = AppSlogan,
                style = TextStyle(
                    color = Color.LightGray,
                    shadow = Shadow(
                        color = Color.Black, offset = Offset(2f, 2f), blurRadius = 2f
                    ),
                    fontFamily = myFontFamily(),
                    fontSize = 12.sp
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slogan
            Text(
                text = slogan,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Divider(
                modifier = Modifier.width(120.dp),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Update Title
            Text(
                text = "Update Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Update Message
            Text(
                text = "A newer version of $appName is available and required to continue. " +
                        "Please update to enjoy the latest features, improvements, and security enhancements.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Update Button
            Button(
                onClick = onUpdateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Update Now",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Optional footer
            Text(
                text = "This update is mandatory to continue using the app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

