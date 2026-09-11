package com.touhidapps.about.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.touhidapps.about.AboutRouteParams
import com.touhidapps.common.constants.Const.ContactUs
import com.touhidapps.common.constants.Const.Disclosure
import com.touhidapps.common.constants.Const.License
import com.touhidapps.common.constants.Const.Tech
import com.touhidapps.ui.customUI.MyAlertDialog
import com.touhidapps.ui.rememberScreenEnv
import org.jetbrains.compose.resources.stringResource


@Composable
fun AboutScreenContent(paramText: String = "") {

    val env = rememberScreenEnv()

    var showDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val mText : String = if (paramText.contains(AboutRouteParams.LICENSE)) {
        License
    } else if (paramText.contains(AboutRouteParams.DISCLOSURE)) {
        Disclosure
    } else if (paramText.contains(AboutRouteParams.TOOLS)) {
        Tech
    } else if (paramText.contains(AboutRouteParams.CONTACT_US)) {
        ContactUs
    } else {
        ""
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp)
    ) {

        val txt1 = "https://github.com"
        val txtTag = "CLICKABLE"
        val siteLink = "https://github.com"
        val annotatedText = buildAnnotatedString {

            if (mText.contains(txt1)) {
                val t1 = mText.split(txt1)
                val t2 = t1.first()
                val t3 = t1.last()
                append(t2)

                pushStringAnnotation(tag = txtTag, annotation = siteLink)
                withStyle(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                    append(txt1)
                }
                pop()

                append(t3)
            } else {
                append(mText)
            }

        }


        Box(
            modifier = Modifier
                .padding(10.dp) // External padding
                .fillMaxWidth()
                .weight(1f)
                .clip(shape = RoundedCornerShape(10.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.LightGray, Color.Transparent)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                .border(
                    border = BorderStroke(1.dp, Color.Gray),
                    shape = RoundedCornerShape(10.dp)
                )
                .verticalScroll(state = rememberScrollState())
        ) {
            ClickableText(
                text = annotatedText,
                modifier = Modifier.padding(10.dp),
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = txtTag, start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            // Handle the click, e.g., open a URL or perform an action
                            println("Clicked on: ${annotation.item}")

                            showDialog = true

                        }
                }
            )
        }


        if (showDialog) {

            MyAlertDialog(
                title = env.strings.alert,
                desc = env.strings.thisWillOpenBrowserLink,
                okText = env.strings.okContinue,
                onDismiss = {
                    showDialog = false
                },
                onCancel = {
                    showDialog = false
                },
                onOk = {
                    showDialog = false
                    uriHandler.openUri(siteLink)
                }
            )

        } // if showDialog


    }





}